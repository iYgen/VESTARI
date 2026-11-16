package app.vestari.data

import android.content.Context
import java.io.File
import java.util.UUID
import javax.mail.Flags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Единое хранилище приложения: ящики, письма (кэш), папки, ручная раскладка и настройки.
 * Всё лежит JSON-файлами во внутренней папке приложения; пароли — отдельно, в [Secrets].
 */
object Repository {
    private lateinit var dir: File
    private lateinit var secrets: Secrets
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val syncLock = Mutex()
    @Volatile private var ready = false

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _messages = MutableStateFlow<Map<String, List<MailMessage>>>(emptyMap())
    val messages: StateFlow<Map<String, List<MailMessage>>> = _messages.asStateFlow()

    private val _categories = MutableStateFlow(Categorizer.builtin)
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    /** Ручная раскладка: ключ письма → id папки ("" = убрать из папок). */
    private val _assignments = MutableStateFlow<Map<String, String>>(emptyMap())
    val assignments: StateFlow<Map<String, String>> = _assignments.asStateFlow()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            dir = File(context.filesDir, "store").apply { mkdirs() }
            secrets = Secrets(context.applicationContext)
            _accounts.value = read("accounts.json") ?: emptyList()
            _categories.value = read<List<Category>>("categories.json") ?: Categorizer.builtin
            _assignments.value = read("assignments.json") ?: emptyMap()
            _settings.value = read("settings.json") ?: Settings()
            _messages.value = _accounts.value.associate { it.id to (read<List<MailMessage>>("messages_${it.id}.json") ?: emptyList()) }
            ready = true
        }
    }

    // ---------- папки и раскладка ----------

    fun categoryOf(m: MailMessage): String? =
        _assignments.value[m.key]?.takeIf { it.isNotEmpty() } ?: m.autoCategory.takeIf { _assignments.value[m.key] == null }

    fun assign(m: MailMessage, categoryId: String?) {
        _assignments.update { it + (m.key to (categoryId ?: "")) }
        persist("assignments.json", _assignments.value)
    }

    fun addCategory(name: String, icon: String): Category {
        val c = Category("c_" + UUID.randomUUID().toString().take(8), name.trim(), icon)
        _categories.update { it + c }
        persist("categories.json", _categories.value)
        return c
    }

    fun updateCategory(c: Category) {
        _categories.update { list -> list.map { if (it.id == c.id) c else it } }
        persist("categories.json", _categories.value)
    }

    /** Возвращает позицию, чтобы удаление можно было отменить через [restoreCategory]. */
    fun removeCategory(id: String): Pair<Int, Category>? {
        val list = _categories.value
        val i = list.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return null
        _categories.value = list.toMutableList().apply { removeAt(i) }
        persist("categories.json", _categories.value)
        return i to list[i]
    }

    fun restoreCategory(index: Int, c: Category) {
        _categories.update { it.toMutableList().apply { add(index.coerceIn(0, size), c) } }
        persist("categories.json", _categories.value)
    }

    fun moveCategory(from: Int, to: Int) {
        if (from == to) return
        _categories.update { it.toMutableList().apply { add(to.coerceIn(0, size - 1), removeAt(from)) } }
        persist("categories.json", _categories.value)
    }

    // ---------- настройки ----------

    fun updateSettings(block: (Settings) -> Settings) {
        _settings.update(block)
        persist("settings.json", _settings.value)
    }

    // ---------- ящики ----------

    suspend fun addAccount(email: String, name: String, password: String, provider: Provider,
                           imapHost: String, imapPort: Int, smtpHost: String, smtpPort: Int): Account {
        val acc = Account(
            id = UUID.randomUUID().toString().take(12),
            email = email.trim().lowercase(),
            name = name.trim(),
            provider = provider.id,
            imapHost = imapHost.trim(), imapPort = imapPort,
            smtpHost = smtpHost.trim(), smtpPort = smtpPort,
            color = _accounts.value.size,
        )
        require(_accounts.value.none { it.email == acc.email }) { "Этот ящик уже добавлен." }
        MailClient.test(acc, password)
        secrets.put(acc.id, password)
        _accounts.update { it + acc }
        persist("accounts.json", _accounts.value)
        updateSettings { it.copy(selectedAccountId = acc.id) }
        return acc
    }

    fun removeAccount(id: String) {
        _accounts.update { list -> list.filterNot { it.id == id } }
        _messages.update { it - id }
        secrets.remove(id)
        persist("accounts.json", _accounts.value)
        io.launch { File(dir, "messages_$id.json").delete() }
        if (_settings.value.selectedAccountId == id) updateSettings { it.copy(selectedAccountId = null) }
    }

    private fun password(acc: Account) = secrets.get(acc.id)
        ?: throw IllegalStateException("Пароль для ${acc.email} не найден — удалите ящик и добавьте заново.")

    fun account(id: String) = _accounts.value.firstOrNull { it.id == id }

    // ---------- синхронизация ----------

    /** Возвращает новые непрочитанные письма (для уведомлений). */
    suspend fun sync(accountId: String? = null): List<MailMessage> = syncLock.withLock {
        _syncing.value = true
        _lastError.value = null
        try {
            val targets = _accounts.value.filter { accountId == null || it.id == accountId }
            val own = _accounts.value.map { it.email }.toSet()
            val results = withContext(Dispatchers.IO) {
                targets.map { acc ->
                    async {
                        runCatching {
                            val old = _messages.value[acc.id].orEmpty()
                            val oldByUid = old.associateBy { it.uid }
                            val fresh = MailClient.fetchInbox(acc, password(acc)).map { m ->
                                // Превью для старых писем не перекачиваем — берём из кэша.
                                val prev = oldByUid[m.uid]
                                val withPreview = if (m.preview.isEmpty() && prev != null) m.copy(preview = prev.preview) else m
                                withPreview.copy(autoCategory = Categorizer.categorize(withPreview, own))
                            }
                            val newOnes = if (old.isEmpty()) emptyList()
                            else fresh.filter { it.uid !in oldByUid && !it.seen }
                            Triple(acc, fresh, newOnes)
                        }.onFailure { e -> _lastError.value = "${acc.email}: ${e.message ?: e.javaClass.simpleName}" }
                    }
                }.awaitAll()
            }
            val newMessages = mutableListOf<MailMessage>()
            results.forEach { r ->
                r.getOrNull()?.let { (acc, fresh, newOnes) ->
                    _messages.update { it + (acc.id to fresh) }
                    persist("messages_${acc.id}.json", fresh)
                    newMessages += newOnes
                }
            }
            newMessages
        } finally {
            _syncing.value = false
        }
    }

    private fun patch(m: MailMessage, block: (MailMessage) -> MailMessage) {
        _messages.update { map ->
            val list = map[m.accountId] ?: return@update map
            map + (m.accountId to list.map { if (it.uid == m.uid) block(it) else it })
        }
        persist("messages_${m.accountId}.json", _messages.value[m.accountId].orEmpty())
    }

    fun find(key: String): MailMessage? =
        _messages.value.values.asSequence().flatten().firstOrNull { it.key == key }

    suspend fun loadBody(m: MailMessage): MailBody {
        val acc = account(m.accountId) ?: throw IllegalStateException("Ящик удалён")
        val body = MailClient.loadBody(acc, password(acc), m.uid)
        if (!m.seen) patch(m) { it.copy(seen = true) }
        return body
    }

    /** Локально меняем сразу (интерфейс не ждёт сети), на сервер отправляем в фоне. */
    fun setSeen(m: MailMessage, seen: Boolean) {
        patch(m) { it.copy(seen = seen) }
        remote(m) { acc, pass -> MailClient.setFlag(acc, pass, m.uid, Flags.Flag.SEEN, seen) }
    }

    fun setFlagged(m: MailMessage, flagged: Boolean) {
        patch(m) { it.copy(flagged = flagged) }
        remote(m) { acc, pass -> MailClient.setFlag(acc, pass, m.uid, Flags.Flag.FLAGGED, flagged) }
    }

    /** Удаление с возможностью отмены: письмо прячется сразу, а на сервер уходит через [commitDelete]. */
    fun hideLocally(m: MailMessage) {
        _messages.update { map -> map + (m.accountId to map[m.accountId].orEmpty().filterNot { it.uid == m.uid }) }
    }

    fun unhide(m: MailMessage) {
        _messages.update { map ->
            map + (m.accountId to (map[m.accountId].orEmpty() + m).sortedByDescending { it.date })
        }
    }

    fun commitDelete(m: MailMessage) {
        persist("messages_${m.accountId}.json", _messages.value[m.accountId].orEmpty())
        remote(m) { acc, pass -> MailClient.delete(acc, pass, m.uid) }
    }

    suspend fun send(accountId: String, to: String, subject: String, text: String, inReplyTo: String?) {
        val acc = account(accountId) ?: throw IllegalStateException("Выберите ящик")
        MailClient.send(acc, password(acc), to, subject, text, inReplyTo)
    }

    private fun remote(m: MailMessage, block: suspend (Account, String) -> Unit) {
        val acc = account(m.accountId) ?: return
        io.launch {
            runCatching { block(acc, password(acc)) }
                .onFailure { _lastError.value = "Сервер ${acc.email}: ${it.message}" }
        }
    }

    fun clearError() { _lastError.value = null }

    // ---------- файлы ----------

    private inline fun <reified T> read(name: String): T? = runCatching {
        val f = File(dir, name)
        if (f.exists()) json.decodeFromString<T>(f.readText()) else null
    }.getOrNull()

    private inline fun <reified T> persist(name: String, value: T) {
        val text = json.encodeToString(value)
        io.launch {
            writeLock.withLock {
                val tmp = File(dir, "$name.tmp")
                tmp.writeText(text)
                tmp.renameTo(File(dir, name))
            }
        }
    }
}
