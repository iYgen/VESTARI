package app.vestari.data

import android.content.Context
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.mail.Flags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Фоновая область приложения: загрузка почты живёт здесь и не обрывается при смене экрана. */
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

    /** Ход полной загрузки по каждому ящику. */
    private val _progress = MutableStateFlow<Map<String, SyncProgress>>(emptyMap())
    val progress: StateFlow<Map<String, SyncProgress>> = _progress.asStateFlow()

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
        val c = list[i]
        _categories.value = list.toMutableList().apply { removeAt(i) }
        persist("categories.json", _categories.value)
        if (c.remote) updateSettings { it.copy(ignoredFolders = it.ignoredFolders + c.name.lowercase()) }
        return i to c
    }

    fun restoreCategory(index: Int, c: Category) {
        _categories.update { it.toMutableList().apply { add(index.coerceIn(0, size), c) } }
        persist("categories.json", _categories.value)
        if (c.remote) updateSettings { it.copy(ignoredFolders = it.ignoredFolders - c.name.lowercase()) }
    }

    fun moveCategory(from: Int, to: Int) {
        if (from == to) return
        _categories.update { it.toMutableList().apply { add(to.coerceIn(0, size - 1), removeAt(from)) } }
        persist("categories.json", _categories.value)
    }

    /**
     * Папка, созданная в самой почте (например, в Mail.ru), становится папкой Vestari с тем же именем.
     * Если такая уже есть (в том числе встроенная — «Рассылки», «Чеки»), письма идут в неё.
     */
    private fun categoryForServerFolder(f: RemoteFolder): String? {
        if (f.isInbox) return null
        val name = f.displayName.trim()
        if (name.isEmpty() || name.lowercase() in _settings.value.ignoredFolders) return null
        _categories.value.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it.id }
        val c = Category("f_" + Integer.toHexString(name.lowercase().hashCode()), name, "folder", remote = true)
        _categories.update { list -> if (list.any { it.id == c.id }) list else list + c }
        persist("categories.json", _categories.value)
        return c.id
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
        _progress.update { it + (acc.id to SyncProgress(active = true)) }
        _accounts.update { it + acc }
        _messages.update { it + (acc.id to emptyList()) }
        persist("accounts.json", _accounts.value)
        updateSettings { it.copy(selectedAccountId = acc.id) }
        refresh(acc.id)
        return acc
    }

    fun removeAccount(id: String) {
        backfills.remove(id)?.cancel()
        pending.remove(id)
        _progress.update { it - id }
        _accounts.update { list -> list.filterNot { it.id == id } }
        _messages.update { it - id }
        secrets.remove(id)
        persist("accounts.json", _accounts.value)
        io.launch {
            File(dir, "messages_$id.json").delete()
            File(dir, "sync_$id.json").delete()
        }
        if (_settings.value.selectedAccountId == id) updateSettings { it.copy(selectedAccountId = null) }
    }

    private fun password(acc: Account) = secrets.get(acc.id)
        ?: throw IllegalStateException("Пароль для ${acc.email} не найден — удалите ящик и добавьте заново.")

    fun account(id: String) = _accounts.value.firstOrNull { it.id == id }

    // ---------- синхронизация ----------
    //
    // 1. «Быстрая» часть (sync): список папок, флаги всех писем (одна команда на папку), удалённые письма
    //    убираются, свежие письма скачиваются сразу. Отсюда же берутся уведомления.
    // 2. «Полная» часть (backfill): все остальные письма докачиваются пачками в фоне, с прогрессом.
    //    Обрыв соединения не страшен — ImapConnection переподключается, а при следующей проверке
    //    загрузка продолжится с того же места.

    private class Pending(val folder: RemoteFolder, val uids: ArrayDeque<Long>)

    private val pending = ConcurrentHashMap<String, List<Pending>>()
    private val backfills = ConcurrentHashMap<String, Job>()

    /** Запустить проверку почты, не привязываясь к экрану: уход с экрана её не прервёт. */
    fun refresh(accountId: String? = null) {
        io.launch { sync(accountId) }
    }

    /** Возвращает новые непрочитанные письма (для уведомлений). Работает в фоновой области приложения. */
    suspend fun sync(accountId: String? = null): List<MailMessage> = io.async { doSync(accountId) }.await()

    private suspend fun doSync(accountId: String?): List<MailMessage> = syncLock.withLock {
        _syncing.value = true
        _lastError.value = null
        try {
            val targets = _accounts.value.filter { accountId == null || it.id == accountId }
            val own = _accounts.value.map { it.email }.toSet()
            coroutineScope {
                targets.map { acc ->
                    async {
                        try {
                            syncHead(acc, own)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _lastError.value = "${acc.email}: ${describe(e)}"
                            setProgress(acc.id) { it.copy(active = backfills[acc.id]?.isActive == true) }
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }
        } finally {
            _syncing.value = false
        }
    }

    private fun syncHead(acc: Account, own: Set<String>): List<MailMessage> {
        setProgress(acc.id) { it.copy(active = true) }
        val before = _messages.value[acc.id].orEmpty()
        val newestBefore = before.maxOfOrNull { it.date } ?: 0L

        ImapConnection(acc, password(acc)).use { c ->
            val folders = MailClient.listFolders(c)
            val catByFolder = folders.associate { it.fullName to categoryForServerFolder(it) }
            val states = folders.associate { it.fullName to MailClient.folderState(c, it.fullName) }

            // Удалённые на сервере письма убираем, флаги (прочитано, важное) обновляем у всех.
            val oldMeta = read<Map<String, Long>>("sync_${acc.id}.json").orEmpty()
            _messages.update { map ->
                val list = map[acc.id].orEmpty().mapNotNull { m ->
                    val st = states[m.folder] ?: return@mapNotNull null
                    if (oldMeta[m.folder]?.let { it != st.uidValidity } == true) return@mapNotNull null
                    val (seen, flagged) = st.flags[m.uid] ?: return@mapNotNull null
                    val cat = recategorize(m, catByFolder[m.folder], own)
                    if (seen != m.seen || flagged != m.flagged || cat != m.autoCategory)
                        m.copy(seen = seen, flagged = flagged, autoCategory = cat) else m
                }
                map + (acc.id to list)
            }
            persist("sync_${acc.id}.json", states.mapValues { it.value.uidValidity })

            val cached = _messages.value[acc.id].orEmpty().mapTo(HashSet()) { it.folder to it.uid }
            val missing = folders.map { f ->
                val uids = states.getValue(f.fullName).flags.keys
                    .filter { (f.fullName to it) !in cached }
                    .sortedDescending() // новые письма (с большим UID) — первыми
                Pending(f, ArrayDeque(uids))
            }
            val total = states.values.sumOf { it.flags.size }
            setProgress(acc.id) { it.copy(total = total, loaded = cached.size.coerceAtMost(total), active = true) }

            // Свежие письма — сразу, остальное докачает backfill.
            val fresh = mutableListOf<MailMessage>()
            for (p in missing) {
                val head = take(p.uids, if (p.folder.isInbox) 40 else 15)
                val got = MailClient.fetchHeaders(c, acc, p.folder.fullName, head, previewCount = if (p.folder.isInbox) 30 else 0)
                fresh += merge(acc.id, got, catByFolder[p.folder.fullName], own)
            }
            pending[acc.id] = missing.filter { it.uids.isNotEmpty() }
            updateLoaded(acc.id)
            persistMessages(acc.id)
            startBackfill(acc, own, catByFolder)

            return if (before.isEmpty()) emptyList()
            else fresh.filter { !it.seen && it.date > newestBefore }
        }
    }

    private fun startBackfill(acc: Account, own: Set<String>, catByFolder: Map<String, String?>) {
        if (pending[acc.id].isNullOrEmpty()) {
            setProgress(acc.id) { it.copy(active = false) }
            return
        }
        if (backfills[acc.id]?.isActive == true) return
        backfills[acc.id] = io.launch {
            try {
                ImapConnection(acc, password(acc)).use { c ->
                    var batches = 0
                    while (true) {
                        ensureActive()
                        val p = pending[acc.id]?.firstOrNull { it.uids.isNotEmpty() } ?: break
                        val chunk = take(p.uids, 150)
                        val got = MailClient.fetchHeaders(c, acc, p.folder.fullName, chunk)
                        merge(acc.id, got, catByFolder[p.folder.fullName], own)
                        updateLoaded(acc.id)
                        if (++batches % 8 == 0) persistMessages(acc.id)
                    }
                }
                setProgress(acc.id) { it.copy(loaded = it.total) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lastError.value = "${acc.email}: загрузка прервалась (${describe(e)}). Продолжу при следующей проверке."
            } finally {
                persistMessages(acc.id)
                setProgress(acc.id) { it.copy(active = false) }
            }
        }
    }

    private fun take(q: ArrayDeque<Long>, n: Int): List<Long> = synchronized(q) {
        List(minOf(n, q.size)) { q.removeFirst() }
    }

    private fun recategorize(m: MailMessage, folderCategory: String?, own: Set<String>): String? =
        folderCategory ?: Categorizer.categorize(m, own)

    /** Добавляет письма в кэш ящика (без дублей, по дате). Возвращает добавленные. */
    private fun merge(accountId: String, add: List<MailMessage>, folderCategory: String?, own: Set<String>): List<MailMessage> {
        if (add.isEmpty()) return add
        val ready = add.map { it.copy(autoCategory = recategorize(it, folderCategory, own)) }
        _messages.update { map ->
            if (accountId !in map) return@update map // ящик успели удалить
            val byKey = LinkedHashMap<String, MailMessage>()
            map[accountId].orEmpty().forEach { byKey[it.key] = it }
            ready.forEach { m ->
                val prev = byKey[m.key]
                byKey[m.key] = if (prev != null && m.preview.isEmpty()) m.copy(preview = prev.preview) else m
            }
            map + (accountId to byKey.values.sortedByDescending { it.date })
        }
        return ready
    }

    private fun setProgress(id: String, block: (SyncProgress) -> SyncProgress) {
        _progress.update { it + (id to block(it[id] ?: SyncProgress())) }
    }

    private fun updateLoaded(id: String) {
        val n = _messages.value[id]?.size ?: 0
        setProgress(id) { it.copy(loaded = n.coerceAtMost(maxOf(it.total, n))) }
    }

    private fun persistMessages(id: String) {
        _messages.value[id]?.let { persist("messages_$id.json", it) }
    }

    private fun describe(e: Throwable): String = when (e) {
        is javax.mail.AuthenticationFailedException -> "почта не приняла пароль"
        is javax.mail.FolderClosedException, is javax.mail.StoreClosedException -> "сервер закрыл соединение"
        is java.net.UnknownHostException -> "нет интернета"
        is java.net.SocketTimeoutException -> "сервер долго не отвечает"
        else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
    }

    private fun patch(m: MailMessage, block: (MailMessage) -> MailMessage) {
        _messages.update { map ->
            val list = map[m.accountId] ?: return@update map
            map + (m.accountId to list.map { if (it.key == m.key) block(it) else it })
        }
        persistMessages(m.accountId)
    }

    fun find(key: String): MailMessage? =
        _messages.value.values.asSequence().flatten().firstOrNull { it.key == key }

    suspend fun loadBody(m: MailMessage): MailBody {
        val acc = account(m.accountId) ?: throw IllegalStateException("Ящик удалён")
        val body = MailClient.loadBody(acc, password(acc), m.folder, m.uid)
        if (!m.seen) patch(m) { it.copy(seen = true) }
        return body
    }

    /** Локально меняем сразу (интерфейс не ждёт сети), на сервер отправляем в фоне. */
    fun setSeen(m: MailMessage, seen: Boolean) {
        patch(m) { it.copy(seen = seen) }
        remote(m) { acc, pass -> MailClient.setFlag(acc, pass, m.folder, m.uid, Flags.Flag.SEEN, seen) }
    }

    fun setFlagged(m: MailMessage, flagged: Boolean) {
        patch(m) { it.copy(flagged = flagged) }
        remote(m) { acc, pass -> MailClient.setFlag(acc, pass, m.folder, m.uid, Flags.Flag.FLAGGED, flagged) }
    }

    /** Удаление с возможностью отмены: письмо прячется сразу, а на сервер уходит через [commitDelete]. */
    fun hideLocally(m: MailMessage) {
        _messages.update { map -> map + (m.accountId to map[m.accountId].orEmpty().filterNot { it.key == m.key }) }
    }

    fun unhide(m: MailMessage) {
        _messages.update { map ->
            map + (m.accountId to (map[m.accountId].orEmpty() + m).distinctBy { it.key }.sortedByDescending { it.date })
        }
    }

    fun commitDelete(m: MailMessage) {
        persistMessages(m.accountId)
        remote(m) { acc, pass -> MailClient.delete(acc, pass, m.folder, m.uid) }
    }

    suspend fun send(accountId: String, to: String, subject: String, text: String, inReplyTo: String?) {
        val acc = account(accountId) ?: throw IllegalStateException("Выберите ящик")
        MailClient.send(acc, password(acc), to, subject, text, inReplyTo)
    }

    private fun remote(m: MailMessage, block: suspend (Account, String) -> Unit) {
        val acc = account(m.accountId) ?: return
        io.launch {
            runCatching { block(acc, password(acc)) }
                .onFailure { _lastError.value = "Сервер ${acc.email}: ${describe(it)}" }
        }
    }

    fun clearError() { _lastError.value = null }

    // ---------- файлы ----------

    private inline fun <reified T> read(name: String): T? = runCatching {
        val f = File(dir, name)
        if (f.exists()) json.decodeFromString<T>(f.readText()) else null
    }.getOrNull()

    private inline fun <reified T> persist(name: String, value: T) {
        io.launch {
            val text = json.encodeToString(value)
            writeLock.withLock {
                val tmp = File(dir, "$name.tmp")
                tmp.writeText(text)
                tmp.renameTo(File(dir, name))
            }
        }
    }
}
