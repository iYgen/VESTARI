package app.vestari.data

import com.sun.mail.imap.IMAPFolder
import java.io.Closeable
import java.util.Base64
import java.util.Date
import java.util.Properties
import javax.mail.Address
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Папка на сервере: полное имя (для IMAP), имя для показа, роль и разделитель вложенности.
 * [role]: inbox, sent, drafts, spam, trash, archive или "" — обычная папка пользователя.
 */
@kotlinx.serialization.Serializable
data class RemoteFolder(
    val fullName: String,
    val displayName: String,
    val isInbox: Boolean,
    val role: String = if (isInbox) "inbox" else "",
    val separator: String = "/",
) {
    /** Служебные папки не скачиваем целиком — их можно открыть и посмотреть с сервера. */
    val syncable: Boolean get() = role == "inbox" || role == ""
    val parts: List<String> get() = if (separator.isEmpty()) listOf(fullName) else fullName.split(separator)
    val parent: String? get() = parts.takeIf { it.size > 1 }?.dropLast(1)?.joinToString(separator)
}

/** Снимок папки: UIDVALIDITY и флаги (прочитано, важное) всех писем по UID — одна дешёвая команда. */
data class FolderState(val uidValidity: Long, val flags: Map<Long, Pair<Boolean, Boolean>>)

/** Работа с почтой по стандартным протоколам IMAP (чтение) и SMTP (отправка). */
object MailClient {

    internal fun session(acc: Account): Session {
        val p = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", acc.imapHost)
            put("mail.imaps.port", acc.imapPort.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "20000")
            put("mail.imaps.timeout", "60000")
            put("mail.imaps.partialfetch", "false")
            put("mail.imaps.peek", "true")

            val ssl = acc.smtpPort == 465
            put("mail.smtp.host", acc.smtpHost)
            put("mail.smtp.port", acc.smtpPort.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.ssl.enable", ssl.toString())
            put("mail.smtp.starttls.enable", (!ssl).toString())
            put("mail.smtp.starttls.required", (!ssl).toString())
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "30000")
            put("mail.mime.decodetext.strict", "false")
        }
        return Session.getInstance(p)
    }

    /** Проверка логина и пароля при добавлении ящика. Бросает исключение с понятным текстом. */
    suspend fun test(acc: Account, password: String) = withContext(Dispatchers.IO) {
        try {
            ImapConnection(acc, password).use { c -> c.run("INBOX", Folder.READ_ONLY) { it.messageCount } }
        } catch (e: AuthenticationFailedException) {
            throw IllegalStateException("Почта не приняла пароль. Нужен именно пароль для внешнего приложения, а не обычный.")
        } catch (e: MessagingException) {
            throw IllegalStateException("Не удалось подключиться к ${acc.imapHost}: ${e.message ?: e.javaClass.simpleName}")
        }
        // Отправку тоже проверяем, чтобы не узнать о проблеме при первом письме.
        try {
            val t = session(acc).getTransport("smtp")
            t.connect(acc.smtpHost, acc.smtpPort, acc.email, password)
            t.close()
        } catch (e: MessagingException) {
            throw IllegalStateException("Чтение работает, но отправка через ${acc.smtpHost} — нет: ${e.message ?: ""}")
        }
    }

    // ---- синхронизация (вызывать на IO-потоке) ----

    /** Дубликаты писем (Gmail «Вся почта», «Помеченные») и папки-заглушки не показываем вовсе. */
    private val hiddenAttrs = setOf("\\noselect", "\\nonexistent", "\\all", "\\important", "\\flagged")
    private val roleByAttr = mapOf(
        "\\sent" to "sent", "\\drafts" to "drafts", "\\junk" to "spam", "\\trash" to "trash", "\\archive" to "archive",
    )
    private val roleByName = mapOf(
        "sent" to "sent", "sent items" to "sent", "sent messages" to "sent", "отправленные" to "sent",
        "drafts" to "drafts", "черновики" to "drafts",
        "spam" to "spam", "junk" to "spam", "спам" to "spam", "bulk mail" to "spam",
        "trash" to "trash", "deleted" to "trash", "deleted items" to "trash", "deleted messages" to "trash",
        "корзина" to "trash", "удаленные" to "trash", "удалённые" to "trash",
        "archive" to "archive", "архив" to "archive",
        "outbox" to "outbox", "исходящие" to "outbox",
    )
    val roleTitles = mapOf(
        "inbox" to "Входящие", "sent" to "Отправленные", "drafts" to "Черновики", "spam" to "Спам",
        "trash" to "Корзина", "archive" to "Архив", "outbox" to "Исходящие",
    )

    /** Все папки ящика с ролями и вложенностью (для панели папок). «Входящие» — первыми. */
    fun listAllFolders(c: ImapConnection): List<RemoteFolder> = c.withStore { store ->
        store.defaultFolder.list("*").filterIsInstance<IMAPFolder>().mapNotNull { f ->
            val attrs = f.attributes.orEmpty().map { it.lowercase() }
            if (attrs.any { it in hiddenAttrs } || f.name.equals("[Gmail]", true)) return@mapNotNull null
            val inbox = f.fullName.equals("INBOX", true)
            val role = when {
                inbox -> "inbox"
                else -> attrs.firstNotNullOfOrNull { roleByAttr[it] } ?: roleByName[f.name.lowercase()] ?: ""
            }
            val sep = runCatching { f.separator.toString() }.getOrDefault("/")
            // Служебные папки называем по-русски единообразно; папки пользователя — как в почте.
            val title = if (role.isNotEmpty() && role != "archive") roleTitles.getValue(role) else f.name
            RemoteFolder(f.fullName, title, inbox, role, sep)
        }.distinctBy { it.fullName }.sortedByDescending { it.isInbox }
    }

    /** Папки, письма которых скачиваются целиком: «Входящие» и папки пользователя. */
    fun listFolders(c: ImapConnection): List<RemoteFolder> = listAllFolders(c).filter { it.syncable }

    /** Сколько непрочитанных в папке — команда STATUS, саму папку не открываем. */
    fun unreadCount(c: ImapConnection, folder: String): Int =
        c.withStore { (it.getFolder(folder) as IMAPFolder).unreadMessageCount }

    /** Последние письма папки с сервера, без сохранения (для служебных папок: отправленные, спам…). */
    fun fetchLatest(c: ImapConnection, acc: Account, folder: String, skip: Int, count: Int): Pair<List<MailMessage>, Int> {
        val uids = folderState(c, folder).flags.keys.sortedDescending()
        val page = uids.drop(skip).take(count)
        val list = fetchHeaders(c, acc, folder, page, previewCount = page.size).sortedByDescending { it.date }
        return list to uids.size
    }

    fun folderState(c: ImapConnection, folder: String): FolderState = c.run(folder, Folder.READ_ONLY) { f ->
        val msgs = f.messages
        f.fetch(msgs, FetchProfile().apply { add(FetchProfile.Item.FLAGS); add(UIDFolder.FetchProfileItem.UID) })
        FolderState(
            f.uidValidity,
            msgs.associate { f.getUID(it) to (it.isSet(Flags.Flag.SEEN) to it.isSet(Flags.Flag.FLAGGED)) },
        )
    }

    /** Заголовки писем по списку UID. Для первых [previewCount] UID дополнительно грузится короткое превью. */
    fun fetchHeaders(
        c: ImapConnection, acc: Account, folder: String, uids: List<Long>, previewCount: Int = 0,
    ): List<MailMessage> = if (uids.isEmpty()) emptyList() else c.run(folder, Folder.READ_ONLY) { f ->
        val msgs = f.getMessagesByUID(uids.toLongArray()).filterNotNull().toTypedArray()
        f.fetch(msgs, FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE)
            add(FetchProfile.Item.FLAGS)
            add(UIDFolder.FetchProfileItem.UID)
            add("List-Unsubscribe")
        })
        val previewUids = uids.take(previewCount).toSet()
        msgs.map { m ->
            val uid = f.getUID(m)
            val from = m.from?.firstOrNull() as? InternetAddress
            MailMessage(
                accountId = acc.id,
                uid = uid,
                folder = folder,
                fromEmail = from?.address.orEmpty(),
                fromName = decode(from?.personal),
                to = addresses(m.getRecipients(Message.RecipientType.TO)),
                subject = decode(m.subject).ifBlank { "(без темы)" },
                preview = if (uid in previewUids) runCatching { previewOf(m) }.getOrDefault("") else "",
                date = (m.receivedDate ?: m.sentDate ?: Date(0)).time,
                seen = m.isSet(Flags.Flag.SEEN),
                flagged = m.isSet(Flags.Flag.FLAGGED),
                listUnsubscribe = m.getHeader("List-Unsubscribe")?.isNotEmpty() == true,
                messageId = (m as? MimeMessage)?.messageID,
            )
        }
    }

    // ---- действия с одним письмом ----

    private fun <T> once(acc: Account, password: String, folder: String, mode: Int, block: (IMAPFolder) -> T): T =
        ImapConnection(acc, password).use { it.run(folder, mode, block) }

    suspend fun loadBody(acc: Account, password: String, folder: String, uid: Long): MailBody = withContext(Dispatchers.IO) {
        once(acc, password, folder, Folder.READ_WRITE) { f ->
            val m = f.getMessageByUID(uid) ?: return@once MailBody(null, "Письмо не найдено на сервере.")
            val body = extract(m, inlineImages = true)
            // Открыли письмо — помечаем прочитанным и на сервере, как сделал бы любой клиент.
            runCatching { m.setFlag(Flags.Flag.SEEN, true) }
            body
        }
    }

    suspend fun setFlag(acc: Account, password: String, folder: String, uid: Long, flag: Flags.Flag, value: Boolean) =
        withContext(Dispatchers.IO) {
            once(acc, password, folder, Folder.READ_WRITE) { f -> f.getMessageByUID(uid)?.setFlag(flag, value) }
        }

    /** Флаг сразу у многих писем одной папки — одной командой UID STORE. */
    suspend fun setFlagMany(acc: Account, password: String, folder: String, uids: List<Long>, flag: Flags.Flag, value: Boolean) =
        withContext(Dispatchers.IO) {
            once(acc, password, folder, Folder.READ_WRITE) { f ->
                uids.chunked(500).forEach { part ->
                    val msgs = f.getMessagesByUID(part.toLongArray()).filterNotNull().toTypedArray()
                    if (msgs.isNotEmpty()) f.setFlags(msgs, Flags(flag), value)
                }
            }
        }

    /** Удаление = перенос в корзину (если сервер её отдаёт), иначе пометка DELETED. */
    suspend fun delete(acc: Account, password: String, folder: String, uid: Long) = withContext(Dispatchers.IO) {
        once(acc, password, folder, Folder.READ_WRITE) { f ->
            val m = f.getMessageByUID(uid) ?: return@once
            val trash = f.store.defaultFolder.list("*").firstOrNull { t ->
                (t as? IMAPFolder)?.attributes?.any { it.equals("\\Trash", true) } == true ||
                    t.name.equals("Trash", true) || t.name.equals("Корзина", true)
            }
            if (trash != null && trash.fullName != f.fullName) f.moveMessages(arrayOf(m), trash)
            else m.setFlag(Flags.Flag.DELETED, true)
        }
    }

    /** Перенос в «Спам»: папка ищется по подсказке, атрибуту \Junk или по имени (Spam, Junk, Спам). */
    suspend fun moveToSpam(acc: Account, password: String, folder: String, uid: Long, spamHint: String? = null) = withContext(Dispatchers.IO) {
        once(acc, password, folder, Folder.READ_WRITE) { f ->
            val m = f.getMessageByUID(uid) ?: return@once
            val all = f.store.defaultFolder.list("*")
            val spam = all.firstOrNull { it.fullName == spamHint } ?: all.firstOrNull { t ->
                (t as? IMAPFolder)?.attributes?.any { it.equals("\\Junk", true) || it.equals("\\Spam", true) } == true ||
                    t.name.lowercase() in setOf("spam", "junk", "спам", "junk e-mail", "нежелательная почта")
            } ?: throw javax.mail.MessagingException("на сервере нет папки «Спам»")
            if (spam.fullName != f.fullName) f.moveMessages(arrayOf(m), spam)
        }
    }

    /** Файл для отправки: [file] — копия во внутренней папке приложения. */
    data class OutgoingFile(val file: java.io.File, val name: String, val mime: String)

    suspend fun send(
        acc: Account, password: String,
        to: String, subject: String, text: String,
        inReplyTo: String? = null,
        cc: String = "", bcc: String = "",
        files: List<OutgoingFile> = emptyList(),
    ) = withContext(Dispatchers.IO) {
        val s = session(acc)
        fun parse(list: String) = InternetAddress.parse(list.replace(';', ','), false)
        val msg = MimeMessage(s).apply {
            setFrom(InternetAddress(acc.email, acc.name.ifBlank { null }, "UTF-8"))
            setRecipients(Message.RecipientType.TO, parse(to))
            if (cc.isNotBlank()) setRecipients(Message.RecipientType.CC, parse(cc))
            if (bcc.isNotBlank()) setRecipients(Message.RecipientType.BCC, parse(bcc))
            setSubject(subject, "UTF-8")
            if (files.isEmpty()) {
                setText(text, "UTF-8")
            } else {
                val mp = javax.mail.internet.MimeMultipart("mixed")
                mp.addBodyPart(MimeBodyPart().apply { setText(text, "UTF-8") })
                files.forEach { f ->
                    mp.addBodyPart(MimeBodyPart().apply {
                        attachFile(f.file, f.mime, null)
                        fileName = MimeUtility.encodeText(f.name, "UTF-8", null)
                    })
                }
                setContent(mp)
            }
            sentDate = Date()
            if (inReplyTo != null) {
                setHeader("In-Reply-To", inReplyTo)
                setHeader("References", inReplyTo)
            }
        }
        val t = s.getTransport("smtp")
        t.connect(acc.smtpHost, acc.smtpPort, acc.email, password)
        try {
            t.sendMessage(msg, msg.allRecipients)
        } finally {
            t.close()
        }
    }

    // ---- разбор содержимого ----

    private fun decode(s: String?): String =
        if (s.isNullOrBlank()) "" else runCatching { MimeUtility.decodeText(s) }.getOrDefault(s)

    private fun addresses(a: Array<Address>?): String =
        a?.joinToString(", ") { (it as? InternetAddress)?.address ?: it.toString() }.orEmpty()

    private fun previewOf(m: Message): String {
        val b = extract(m, inlineImages = false)
        val text = b.text ?: b.html?.let(::htmlToText).orEmpty()
        return text.replace(Regex("\\s+"), " ").trim().take(180)
    }

    fun htmlToText(html: String): String = html
        .replace(Regex("(?is)<(style|script|head)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</tr>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&laquo;", "«").replace("&raquo;", "»")
        .replace("&mdash;", "—").replace("&ndash;", "–")

    private const val MAX_INLINE_IMAGE = 4 * 1024 * 1024
    private val cidRef = Regex("(?i)cid:([^\"'\\s)>]+)")

    /**
     * Достаёт HTML и текст письма. Картинки, вложенные в само письмо (ссылки вида `cid:`),
     * подставляются в HTML как data-URI — иначе WebView их не покажет.
     */
    private fun extract(p: Part, inlineImages: Boolean): MailBody {
        var html: String? = null
        var text: String? = null
        val cids = mutableMapOf<String, String>()
        fun walk(part: Part) {
            when {
                part.isMimeType("text/html") && html == null && !isAttachment(part) -> html = part.content as? String
                part.isMimeType("text/plain") && text == null && !isAttachment(part) -> text = part.content as? String
                part.isMimeType("multipart/*") -> {
                    val mp = part.content as Multipart
                    for (i in 0 until mp.count) walk(mp.getBodyPart(i))
                }
                part.isMimeType("message/rfc822") -> (part.content as? Part)?.let(::walk)
                inlineImages && part.isMimeType("image/*") && part is MimeBodyPart -> {
                    val cid = part.contentID?.trim()?.removePrefix("<")?.removeSuffix(">")
                    if (!cid.isNullOrEmpty() && part.size <= MAX_INLINE_IMAGE) runCatching {
                        val bytes = part.inputStream.use { it.readBytes() }
                        val type = part.contentType.substringBefore(';').trim().lowercase()
                        cids[cid] = "data:$type;base64," + Base64.getEncoder().encodeToString(bytes)
                    }
                }
            }
        }
        walk(p)
        val h = html?.let { src ->
            if (cids.isEmpty()) src else cidRef.replace(src) { mr -> cids[mr.groupValues[1]] ?: mr.value }
        }
        return MailBody(h, text)
    }

    private fun isAttachment(p: Part) = Part.ATTACHMENT.equals(p.disposition, ignoreCase = true)
}

/**
 * Одно IMAP-подключение, которое переживает обрывы: если сервер закрыл папку или соединение
 * (mail.ru делает так на долгих выгрузках — FolderClosedException), подключаемся заново и повторяем.
 */
class ImapConnection(private val acc: Account, private val password: String) : Closeable {
    private var store: Store? = null
    private var folder: IMAPFolder? = null
    private var mode = Folder.READ_ONLY

    private fun connectedStore(): Store {
        store?.takeIf { it.isConnected }?.let { return it }
        close()
        val s = MailClient.session(acc).getStore("imaps")
        s.connect(acc.imapHost, acc.imapPort, acc.email, password)
        store = s
        return s
    }

    private fun open(name: String, wanted: Int): IMAPFolder {
        val cur = folder
        if (cur != null && cur.isOpen && cur.fullName == name && mode >= wanted && store?.isConnected == true) return cur
        runCatching { cur?.takeIf { it.isOpen }?.close(false) }
        val f = connectedStore().getFolder(name) as IMAPFolder
        f.open(wanted)
        folder = f
        mode = wanted
        return f
    }

    fun <T> withStore(block: (Store) -> T): T = retry { block(connectedStore()) }

    fun <T> run(folderName: String, mode: Int, block: (IMAPFolder) -> T): T = retry { block(open(folderName, mode)) }

    private fun <T> retry(block: () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: AuthenticationFailedException) {
                throw e
            } catch (e: Exception) {
                // MessagingException (FolderClosed, StoreClosed, обрыв сокета) или «Folder is not Open».
                if (e !is MessagingException && e !is IllegalStateException) throw e
                if (++attempt >= 4) throw e
                close()
                Thread.sleep(1500L * attempt)
            }
        }
    }

    override fun close() {
        runCatching { folder?.takeIf { it.isOpen }?.close(false) }
        runCatching { store?.close() }
        folder = null
        store = null
    }
}
