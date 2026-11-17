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
    private lateinit var appContext: Context
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

    /** Правила раскладки по отправителю (созданные вручную или выученные на переносах). */
    private val _rules = MutableStateFlow<List<SenderRule>>(emptyList())
    val rules: StateFlow<List<SenderRule>> = _rules.asStateFlow()

    /** Ящик → все папки на сервере (с ролями и вложенностью) — для панели папок. */
    private val _serverFolders = MutableStateFlow<Map<String, List<RemoteFolder>>>(emptyMap())
    val serverFolders: StateFlow<Map<String, List<RemoteFolder>>> = _serverFolders.asStateFlow()

    /** Ящик → непрочитанные в служебных папках, которые не скачиваются целиком. */
    private val _remoteUnread = MutableStateFlow<Map<String, Map<String, Int>>>(emptyMap())
    val remoteUnread: StateFlow<Map<String, Map<String, Int>>> = _remoteUnread.asStateFlow()

    /** Письма служебных папок, открытых для просмотра: «ящик|папка» → письма. В файлы не пишутся. */
    private val _remote = MutableStateFlow<Map<String, List<MailMessage>>>(emptyMap())
    val remote: StateFlow<Map<String, List<MailMessage>>> = _remote.asStateFlow()
    private val remoteTotals = ConcurrentHashMap<String, Int>()

    /**
     * Догружает очередную страницу писем папки с сервера (сначала новые).
     * Возвращает true, если на сервере есть ещё письма.
     */
    suspend fun loadRemoteFolder(accountId: String, folder: String, reset: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            val acc = account(accountId) ?: return@withContext false
            val key = "$accountId|$folder"
            val have = if (reset) 0 else _remote.value[key]?.size ?: 0
            val (page, total) = ImapConnection(acc, password(acc)).use { c ->
                MailClient.fetchLatest(c, acc, folder, have, if (have == 0) 40 else 60)
            }
            remoteTotals[key] = total
            _remote.update { map ->
                val list = (if (reset) emptyList() else map[key].orEmpty()) + page
                map + (key to list.distinctBy { it.key }.sortedByDescending { it.date })
            }
            (_remote.value[key]?.size ?: 0) < total
        }

    fun remoteTotal(accountId: String, folder: String) = remoteTotals["$accountId|$folder"]

    /** Ящик → (папка на сервере → папка приложения). Нужно, чтобы перераспределять без сети. */
    private val folderCats = ConcurrentHashMap<String, Map<String, String?>>()

    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            appContext = context.applicationContext
            dir = File(context.filesDir, "store").apply { mkdirs() }
            secrets = Secrets(context.applicationContext)
            _accounts.value = read("accounts.json") ?: emptyList()
            _categories.value = read<List<Category>>("categories.json") ?: Categorizer.builtin
            _assignments.value = read("assignments.json") ?: emptyMap()
            _settings.value = read("settings.json") ?: Settings()
            _messages.value = _accounts.value.associate { it.id to (read<List<MailMessage>>("messages_${it.id}.json") ?: emptyList()) }
            _rules.value = read("rules.json") ?: emptyList()
            _drafts.value = read("drafts.json") ?: emptyList()
            _serverFolders.value = read("server_folders.json") ?: emptyMap()
            read<Map<String, Map<String, String?>>>("folder_map.json")?.let { folderCats.putAll(it) }
            ready = true
        }
        // Правила раскладки обновились (например, чеки ОФД) — один раз раскладываем уже скачанную почту заново.
        if (_settings.value.sortVersion < Categorizer.VERSION) {
            redistributeAsync()
            updateSettings { it.copy(sortVersion = Categorizer.VERSION) }
        }
    }

    // ---------- папки и раскладка ----------

    /** Итоговая папка письма: ручной перенос важнее всего, затем автоматическая раскладка. */
    fun categoryOf(m: MailMessage): String? {
        val manual = _assignments.value[m.key]
        return when {
            manual == null -> m.autoCategory
            manual.isEmpty() -> null
            else -> manual
        }
    }

    /** «Тихая» папка: рассылки и скидки без уведомлений и красных счётчиков (если включено). */
    fun isQuiet(categoryId: String?): Boolean =
        _settings.value.quietPromos && (categoryId == Categorizer.NEWSLETTERS || categoryId == Categorizer.SALES)

    private fun ownEmails() = _accounts.value.map { it.email }.toSet()

    /**
     * Автоматическая папка письма. Порядок: правило по отправителю → папка из самой почты →
     * встроенная раскладка (Чеки, Билеты…). Каждую ступень можно выключить в «Умных функциях».
     */
    private fun autoCategoryFor(m: MailMessage, own: Set<String>): String? {
        val s = _settings.value
        val email = m.fromEmail.lowercase()
        val rules = _rules.value
        (rules.firstOrNull { !it.pattern.startsWith("@") && it.matches(email) } ?: rules.firstOrNull { it.matches(email) })
            ?.let { r -> return r.categoryId.takeIf { id -> id.isNotEmpty() && _categories.value.any { it.id == id } } }
        if (s.serverFolders) folderCats[m.accountId]?.get(m.folder)?.let { return it }
        return if (s.autoSort) Categorizer.categorize(m, own) else null
    }

    /**
     * Перераспределить все скачанные письма по текущим правилам и настройкам — без обращения к серверу.
     * Возвращает число писем, которые сменили папку.
     */
    suspend fun redistribute(): Int = withContext(Dispatchers.Default) {
        val own = ownEmails()
        var changed = 0
        _messages.update { map ->
            changed = 0
            map.mapValues { (_, list) ->
                list.map { m ->
                    val c = autoCategoryFor(m, own)
                    if (c != m.autoCategory) { changed++; m.copy(autoCategory = c) } else m
                }
            }
        }
        _messages.value.keys.forEach(::persistMessages)
        changed
    }

    /** То же, но из фона: результат придёт в [onDone] на главном потоке. Удобно звать из шторок и диалогов. */
    fun redistributeAsync(onDone: (Int) -> Unit = {}) {
        io.launch {
            val n = redistribute()
            withContext(Dispatchers.Main) { onDone(n) }
        }
    }

    fun addRule(pattern: String, categoryId: String?) {
        val p = pattern.trim().lowercase().let { if (!it.contains('@')) "@$it" else it }
        _rules.update { list -> list.filterNot { it.pattern == p } + SenderRule(p, categoryId ?: "", System.currentTimeMillis()) }
        persist("rules.json", _rules.value)
    }

    fun removeRule(pattern: String) {
        _rules.update { list -> list.filterNot { it.pattern == pattern } }
        persist("rules.json", _rules.value)
    }

    /**
     * Обучение на ручном переносе: письма от этого отправителя дальше идут в ту же папку,
     * уже скачанные — перераспределяются. В [onDone] — сколько ещё писем переехало.
     */
    fun learnFromMove(m: MailMessage, categoryId: String?, onDone: (Int) -> Unit) {
        val email = m.fromEmail.lowercase()
        if (!_settings.value.learnFromMoves || email.isBlank() || !email.contains('@')) return onDone(0)
        addRule(email, categoryId)
        redistributeAsync(onDone)
    }

    fun assign(m: MailMessage, categoryId: String?) {
        _assignments.update { it + (m.key to (categoryId ?: "")) }
        persist("assignments.json", _assignments.value)
    }

    /** Новая папка; с [parent] — подпапка внутри неё (сразу после последней подпапки родителя). */
    fun addCategory(name: String, icon: String, parent: String? = null): Category {
        val c = Category("c_" + UUID.randomUUID().toString().take(8), name.trim(), icon, parent = parent)
        _categories.update { list ->
            val after = if (parent == null) -1 else list.indexOfLast { it.id in list.subtreeIds(parent) }
            if (after < 0) list + c else list.toMutableList().apply { add(after + 1, c) }
        }
        persist("categories.json", _categories.value)
        return c
    }

    /** Обновить папку. Вложить папку в её же подпапку нельзя — такой родитель игнорируется. */
    fun updateCategory(c: Category) {
        _categories.update { list ->
            val safe = if (c.parent != null && c.parent in list.subtreeIds(c.id)) c.copy(parent = list.firstOrNull { it.id == c.id }?.parent) else c
            list.map { if (it.id == safe.id) safe else it }
        }
        persist("categories.json", _categories.value)
    }

    /** Сдвинуть папку выше (-1) или ниже (+1) среди папок того же уровня — подпапки едут вместе с ней. */
    fun moveAmongSiblings(id: String, delta: Int) {
        _categories.update { list ->
            val me = list.firstOrNull { it.id == id } ?: return@update list
            val ids = list.map { it.id }.toSet()
            val parentOf = { c: Category -> c.parent?.takeIf { it in ids } }
            val siblings = list.filter { parentOf(it) == parentOf(me) }
            val i = siblings.indexOfFirst { it.id == id }
            val other = siblings.getOrNull(i + delta) ?: return@update list
            val a = list.indexOfFirst { it.id == id }
            val b = list.indexOfFirst { it.id == other.id }
            list.toMutableList().apply { this[a] = other; this[b] = me }
        }
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
    private fun categoryForServerFolder(f: RemoteFolder, all: List<RemoteFolder>, depth: Int = 0): String? {
        if (f.isInbox || depth > 16) return null
        val name = f.displayName.trim()
        if (name.isEmpty() || name.lowercase() in _settings.value.ignoredFolders) return null
        // Вложенная папка ящика («Работа/Проекты») становится вложенной и в Vestari.
        val parentId = f.parent
            ?.let { p -> all.firstOrNull { it.fullName == p } }
            ?.takeIf { !it.isInbox && it.role.isEmpty() }
            ?.let { categoryForServerFolder(it, all, depth + 1) }
        if (parentId == null) {
            _categories.value.firstOrNull { it.name.equals(name, ignoreCase = true) && it.parent == null }?.let { return it.id }
        }
        // Одинаковые имена в разных ветках («Работа/2024» и «Дом/2024») — разные папки.
        val key = if (parentId == null) name.lowercase() else "$parentId/${name.lowercase()}"
        val c = Category("f_" + Integer.toHexString(key.hashCode()), name, "folder", remote = true, parent = parentId)
        _categories.update { list ->
            val i = list.indexOfFirst { it.id == c.id }
            when {
                i < 0 -> list + c
                list[i].parent != parentId -> list.toMutableList().also { it[i] = it[i].copy(parent = parentId) }
                else -> list
            }
        }
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
                           imapHost: String, imapPort: Int, smtpHost: String, smtpPort: Int,
                           imapSecurity: String = "ssl", smtpSecurity: String = ""): Account {
        val acc = Account(
            id = UUID.randomUUID().toString().take(12),
            email = email.trim().lowercase(),
            name = name.trim(),
            provider = provider.id,
            imapHost = imapHost.trim(), imapPort = imapPort,
            smtpHost = smtpHost.trim(), smtpPort = smtpPort,
            imapSecurity = imapSecurity, smtpSecurity = smtpSecurity,
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
        _serverFolders.update { it - id }
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
            }.also { fresh -> if (fresh.isNotEmpty()) runCatching { newMailListener?.invoke(fresh) } }
        } finally {
            _syncing.value = false
        }
    }

    /** Кто узнаёт о новых письмах после проверки почты (уведомления в шторке). */
    @Volatile var newMailListener: ((List<MailMessage>) -> Unit)? = null

    private fun syncHead(acc: Account, own: Set<String>): List<MailMessage> {
        setProgress(acc.id) { it.copy(active = true) }
        val before = _messages.value[acc.id].orEmpty()
        val newestBefore = before.maxOfOrNull { it.date } ?: 0L

        ImapConnection(acc, password(acc)).use { c ->
            val allFolders = MailClient.listAllFolders(c)
            val folders = allFolders.filter { it.syncable }
            // Для панели папок: всё дерево ящика и непрочитанные в служебных папках (спам, архив).
            val unread = allFolders.filter { it.role == "spam" || it.role == "archive" }
                .associate { f -> f.fullName to runCatching { MailClient.unreadCount(c, f.fullName) }.getOrDefault(0) }
            _serverFolders.update { it + (acc.id to allFolders) }
            _remoteUnread.update { it + (acc.id to unread) }
            persist("server_folders.json", _serverFolders.value)
            val catByFolder = folders.associate {
                it.fullName to (if (_settings.value.serverFolders) categoryForServerFolder(it, allFolders) else null)
            }
            folderCats[acc.id] = catByFolder
            persist("folder_map.json", HashMap(folderCats))
            // Папки с сервера, которых больше нет ни в одном ящике (переименованы, перенесены в другую ветку), убираем,
            // если вручную в них ничего не переносили.
            if (_settings.value.serverFolders) {
                val used = folderCats.values.flatMap { it.values }.filterNotNull().toSet() + _assignments.value.values.toSet()
                _categories.update { list ->
                    list.filter { !it.remote || it.id in used }.map { c -> if (c.parent != null && list.none { it.id == c.parent }) c.copy(parent = null) else c }
                }
                persist("categories.json", _categories.value)
            }
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
            // Ограничение из настроек: только N последних писем каждой папки (0 — все).
            val limit = _settings.value.syncLimit
            val missing = folders.map { f ->
                val all = states.getValue(f.fullName).flags.keys.sortedDescending() // новые (больший UID) — первыми
                val wanted = if (limit > 0) all.take(limit) else all
                Pending(f, ArrayDeque(wanted.filter { (f.fullName to it) !in cached }))
            }
            val total = states.values.sumOf { if (limit > 0) minOf(limit, it.flags.size) else it.flags.size }
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

    /**
     * Докачка старых писем. Чтобы телефон не тормозил на десятках тысяч писем:
     * пачки крупные (из настроек), список на экране обновляется не чаще раза в полторы секунды,
     * а на диск сохраняется раз в полминуты и в конце. Пауза и «только по Wi-Fi» проверяются перед каждой пачкой.
     */
    private fun startBackfill(acc: Account, own: Set<String>, catByFolder: Map<String, String?>) {
        if (pending[acc.id].isNullOrEmpty()) {
            setProgress(acc.id) { it.copy(active = false, note = null) }
            return
        }
        stopReason()?.let { why -> setProgress(acc.id) { it.copy(active = false, note = why) }; return }
        if (backfills[acc.id]?.isActive == true) return
        backfills[acc.id] = io.launch {
            var stoppedBy: String? = null
            val buffer = ArrayList<MailMessage>()
            fun flush() {
                if (buffer.isEmpty()) return
                merge(acc.id, buffer.toList(), null, own)
                buffer.clear()
                updateLoaded(acc.id)
            }
            try {
                setProgress(acc.id) { it.copy(active = true, note = null) }
                ImapConnection(acc, password(acc)).use { c ->
                    var lastShown = System.currentTimeMillis()
                    var lastSaved = lastShown
                    while (true) {
                        ensureActive()
                        stopReason()?.let { stoppedBy = it }
                        if (stoppedBy != null) break
                        val p = pending[acc.id]?.firstOrNull { it.uids.isNotEmpty() } ?: break
                        val chunk = take(p.uids, _settings.value.syncBatch.coerceIn(50, 2000))
                        buffer += MailClient.fetchHeaders(c, acc, p.folder.fullName, chunk)
                        val now = System.currentTimeMillis()
                        if (now - lastShown > 1500) { flush(); lastShown = now }
                        else setProgress(acc.id) { it.copy(loaded = ((_messages.value[acc.id]?.size ?: 0) + buffer.size).coerceAtMost(maxOf(it.total, 1))) }
                        if (now - lastSaved > 30_000) { persistMessages(acc.id); lastSaved = now }
                    }
                }
                flush()
                if (stoppedBy == null) setProgress(acc.id) { it.copy(loaded = it.total) }
            } catch (e: CancellationException) {
                flush()
                throw e
            } catch (e: Exception) {
                flush()
                _lastError.value = "${acc.email}: загрузка прервалась (${describe(e)}). Продолжу при следующей проверке."
            } finally {
                persistMessages(acc.id)
                setProgress(acc.id) { it.copy(active = false, note = stoppedBy) }
            }
        }
    }

    /** Почему докачку сейчас нельзя вести: пауза или нет Wi-Fi (если включено «только по Wi-Fi»). */
    private fun stopReason(): String? {
        val s = _settings.value
        if (s.syncPaused) return "Пауза"
        if (s.syncWifiOnly) {
            val cm = appContext.getSystemService(android.net.ConnectivityManager::class.java)
            if (cm?.isActiveNetworkMetered != false) return "Жду Wi-Fi"
        }
        return null
    }

    /** Пауза или продолжение докачки старых писем (кнопка в настройках «Загрузка писем»). */
    fun setLoadingPaused(paused: Boolean) {
        updateSettings { it.copy(syncPaused = paused) }
        if (paused) {
            backfills.values.forEach { it.cancel() }
            _progress.update { map -> map.mapValues { (_, p) -> p.copy(active = false, note = if (p.loaded < p.total) "Пауза" else null) } }
        } else refresh()
    }

    /** Осталось ли что-то докачать (для кнопки «Продолжить»). */
    fun hasPending(): Boolean = pending.values.any { l -> l.any { it.uids.isNotEmpty() } } ||
        _progress.value.values.any { it.total > 0 && it.loaded < it.total }

    private fun take(q: ArrayDeque<Long>, n: Int): List<Long> = synchronized(q) {
        List(minOf(n, q.size)) { q.removeFirst() }
    }

    @Suppress("UNUSED_PARAMETER") // папка с сервера уже учтена через folderCats
    private fun recategorize(m: MailMessage, folderCategory: String?, own: Set<String>): String? = autoCategoryFor(m, own)

    /** Добавляет письма в кэш ящика (без дублей, по дате). Возвращает добавленные. */
    private fun merge(accountId: String, add: List<MailMessage>, folderCategory: String?, own: Set<String>): List<MailMessage> {
        if (add.isEmpty()) return add
        val ready = add.map { it.copy(autoCategory = recategorize(it, folderCategory, own)) }
        _messages.update { map ->
            if (accountId !in map) return@update map // ящик успели удалить
            map + (accountId to mergeSorted(map[accountId].orEmpty(), ready))
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
            ?: _remote.value.values.asSequence().flatten().firstOrNull { it.key == key }

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

    /** «В спам» с отменой: как удаление — письмо прячется сразу, на сервер уходит здесь. */
    fun commitSpam(m: MailMessage) {
        persistMessages(m.accountId)
        val hint = _serverFolders.value[m.accountId]?.firstOrNull { it.role == "spam" }?.fullName
        remote(m) { acc, pass -> MailClient.moveToSpam(acc, pass, m.folder, m.uid, hint) }
    }

    /** Отметить сразу много писем прочитанными или непрочитанными. */
    fun setSeenAll(list: List<MailMessage>, seen: Boolean) {
        val changed = list.filter { it.seen != seen }
        if (changed.isEmpty()) return
        val keys = changed.map { it.key }.toSet()
        _messages.update { map -> map.mapValues { (_, l) -> l.map { if (it.key in keys) it.copy(seen = seen) else it } } }
        changed.map { it.accountId }.distinct().forEach { persistMessages(it) }
        // Одно подключение и одна команда на папку, а не вход на сервер ради каждого письма.
        changed.groupBy { it.accountId to it.folder }.forEach { (k, msgs) ->
            remote(msgs.first()) { acc, pass -> MailClient.setFlagMany(acc, pass, k.second, msgs.map { it.uid }, Flags.Flag.SEEN, seen) }
        }
    }

    /** Отметить письмо прочитанным и дождаться ответа сервера (кнопка в уведомлении). */
    suspend fun markSeenNow(key: String): Boolean {
        val m = find(key) ?: return false
        patch(m) { it.copy(seen = true) }
        val acc = account(m.accountId) ?: return false
        return runCatching { MailClient.setFlag(acc, password(acc), m.folder, m.uid, Flags.Flag.SEEN, true) }.isSuccess
    }

    /** Спрятать сразу несколько писем (удаление или спам с возможностью отмены). */
    fun hideAll(list: List<MailMessage>) {
        val keys = list.map { it.key }.toSet()
        _messages.update { map -> map.mapValues { (_, l) -> l.filterNot { it.key in keys } } }
    }

    fun unhideAll(list: List<MailMessage>) = list.forEach { unhide(it) }

    suspend fun send(accountId: String, to: String, subject: String, text: String, inReplyTo: String?) {
        val acc = account(accountId) ?: throw IllegalStateException("Выберите ящик")
        MailClient.send(acc, password(acc), to, subject, text, inReplyTo)
    }

    /**
     * Сменить серверы, порты, шифрование или пароль у подключённого ящика.
     * Сначала проверяем подключение; не вышло — ничего не меняем. Пустой [newPassword] — пароль прежний.
     */
    suspend fun editAccount(updated: Account, newPassword: String?) {
        val pass = newPassword?.takeIf { it.isNotBlank() } ?: password(updated)
        MailClient.test(updated, pass)
        if (!newPassword.isNullOrBlank()) secrets.put(updated.id, newPassword)
        updateAccount(updated)
        refresh(updated.id)
    }

    fun updateAccount(acc: Account) {
        _accounts.update { list -> list.map { if (it.id == acc.id) acc else it } }
        persist("accounts.json", _accounts.value)
    }

    // ---------- черновики, вложения, отправка ----------

    private val _drafts = MutableStateFlow<List<Draft>>(emptyList())
    val drafts: StateFlow<List<Draft>> = _drafts.asStateFlow()

    private fun draftDir(id: String) = File(dir.parentFile, "drafts/$id")

    /** Сохраняет черновик (или удаляет, если он пустой). Вызывается часто — во время набора. */
    fun saveDraft(d: Draft) {
        if (d.isEmpty) return deleteDraft(d.id)
        val stamped = d.copy(updated = System.currentTimeMillis())
        _drafts.update { list -> listOf(stamped) + list.filterNot { it.id == d.id } }
        persist("drafts.json", _drafts.value)
    }

    fun draft(id: String) = _drafts.value.firstOrNull { it.id == id }

    fun deleteDraft(id: String) {
        if (_drafts.value.none { it.id == id }) {
            io.launch { draftDir(id).deleteRecursively() }
            return
        }
        _drafts.update { list -> list.filterNot { it.id == id } }
        persist("drafts.json", _drafts.value)
        io.launch { draftDir(id).deleteRecursively() }
    }

    /** Своя картинка фона экрана письма: копия во внутренней папке, старая удаляется. */
    suspend fun setCustomBackdrop(context: Context, uri: android.net.Uri) = withContext(Dispatchers.IO) {
        val folder = File(dir.parentFile, "backdrops").apply { mkdirs() }
        // Новое имя каждый раз — экран сразу видит новую картинку, а не закэшированную старую.
        val target = File(folder, "custom_${System.currentTimeMillis()}.img")
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: throw IllegalStateException("Не удалось открыть картинку")
        folder.listFiles()?.filter { it != target }?.forEach { it.delete() }
        updateSettings { it.copy(customBackdrop = target.absolutePath, picturePlace = if (it.picturePlace == PicturePlace.Off) PicturePlace.Header else it.picturePlace) }
    }

    /** Копирует выбранный файл во внутреннюю папку черновика: так он не пропадёт, даже если исходник удалят. */
    suspend fun importAttachment(context: Context, draftId: String, uri: android.net.Uri): DraftAttachment =
        withContext(Dispatchers.IO) {
            val cr = context.contentResolver
            var name = "file"
            var size = -1L
            cr.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(android.provider.OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                }
            }
            val mime = cr.getType(uri) ?: "application/octet-stream"
            val target = File(draftDir(draftId).apply { mkdirs() }, "${System.nanoTime()}_${name.replace('/', '_')}")
            cr.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                ?: throw IllegalStateException("Не удалось открыть файл")
            DraftAttachment(name, mime, if (size >= 0) size else target.length(), target.absolutePath)
        }

    /** Отправляет черновик; при успехе удаляет его. Подпись ящика добавляется к тексту. */
    suspend fun sendDraft(d: Draft) {
        val acc = d.accountId?.let(::account) ?: throw IllegalStateException("Выберите ящик")
        require(d.to.isNotEmpty() || d.cc.isNotEmpty() || d.bcc.isNotEmpty()) { "Укажите адресата" }
        val text = if (acc.signature.isBlank()) d.body else d.body.trimEnd() + "\n\n--\n" + acc.signature.trim()
        MailClient.send(
            acc, password(acc),
            to = d.to.joinToString(", "), cc = d.cc.joinToString(", "), bcc = d.bcc.joinToString(", "),
            subject = d.subject, text = text, inReplyTo = d.inReplyTo,
            files = d.attachments.map { MailClient.OutgoingFile(File(it.path), it.name, it.mime) },
        )
        deleteDraft(d.id)
    }

    /** Отправка в фоне приложения: не прервётся, даже если экран письма закрыть. Ответ — на главном потоке. */
    fun sendInBackground(d: Draft, onDone: (Throwable?) -> Unit) {
        io.launch {
            val error = runCatching { sendDraft(d) }.exceptionOrNull()?.let { e ->
                if (e is javax.mail.AuthenticationFailedException) IllegalStateException("почта не приняла пароль") else e
            }
            withContext(Dispatchers.Main) { onDone(error) }
        }
    }

    /** Отложенная отправка: черновик помечается временем, фоновая задача отправит его сама. */
    fun scheduleDraft(context: Context, d: Draft, at: Long) {
        saveDraft(d.copy(sendAt = at))
        app.vestari.sync.ScheduledSendWorker.schedule(context, d.id, at)
    }

    fun cancelSchedule(context: Context, id: String) {
        draft(id)?.let { saveDraft(it.copy(sendAt = null)) }
        app.vestari.sync.ScheduledSendWorker.cancel(context, id)
    }

    /**
     * Подсказки адресов: отправители скачанных писем и адресаты своих писем.
     * Чем чаще человек встречается в почте, тем выше он в списке.
     */
    fun contacts(): List<Contact> {
        val own = ownEmails()
        val byEmail = HashMap<String, Contact>()
        fun add(email: String, name: String, w: Int) {
            val e = email.trim().lowercase()
            if (!e.contains('@') || e in own || e.startsWith("noreply") || e.startsWith("no-reply")) return
            val prev = byEmail[e]
            byEmail[e] = Contact(e, prev?.name?.ifBlank { null } ?: name, (prev?.score ?: 0) + w)
        }
        _messages.value.values.forEach { list ->
            list.forEach { m ->
                add(m.fromEmail, m.fromName, if (m.listUnsubscribe) 1 else 3)
                if (m.fromEmail.lowercase() in own) m.to.split(',').forEach { add(it, "", 5) }
            }
        }
        _drafts.value.forEach { d -> (d.to + d.cc).forEach { add(it, "", 2) } }
        return byEmail.values.sortedByDescending { it.score }
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

    /** Последняя запланированная запись каждого файла: новая отменяет ещё не начатую старую. */
    private val writes = ConcurrentHashMap<String, Job>()

    private inline fun <reified T> persist(name: String, value: T) {
        writes[name]?.cancel()
        writes[name] = io.launch {
            // Большие списки писем пишем с небольшой задержкой: частые сохранения подряд сливаются в одно.
            if (name.startsWith("messages_")) kotlinx.coroutines.delay(400)
            val text = json.encodeToString(value)
            ensureActive()
            writeLock.withLock {
                val tmp = File(dir, "$name.tmp")
                tmp.writeText(text)
                tmp.renameTo(File(dir, name))
            }
        }
    }
}
