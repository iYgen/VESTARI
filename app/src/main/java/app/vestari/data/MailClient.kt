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

/** Папка на сервере: полное имя (для IMAP) и имя для показа. */
data class RemoteFolder(val fullName: String, val displayName: String, val isInbox: Boolean)

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

    private val skipAttrs = setOf(
        "\\noselect", "\\nonexistent", "\\sent", "\\drafts", "\\trash", "\\junk", "\\all", "\\important", "\\flagged",
    )
    private val skipNames = setOf(
        "sent", "sent items", "sent messages", "отправленные", "drafts", "черновики", "spam", "junk", "спам",
        "trash", "deleted", "deleted items", "deleted messages", "корзина", "удаленные", "удалённые",
        "outbox", "исходящие", "[gmail]",
    )

    /** Папки с письмами, кроме служебных (отправленные, черновики, спам, корзина). «Входящие» — первыми. */
    fun listFolders(c: ImapConnection): List<RemoteFolder> = c.withStore { store ->
        store.defaultFolder.list("*").filterIsInstance<IMAPFolder>().filter { f ->
            val attrs = f.attributes.orEmpty().map { it.lowercase() }
            attrs.none { it in skipAttrs } && f.name.lowercase() !in skipNames && f.fullName.lowercase() !in skipNames
        }.map { f ->
            val inbox = f.fullName.equals("INBOX", true)
            RemoteFolder(f.fullName, if (inbox) "Входящие" else f.name, inbox)
        }.distinctBy { it.fullName }.sortedByDescending { it.isInbox }
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

    suspend fun send(
        acc: Account, password: String,
        to: String, subject: String, text: String,
        inReplyTo: String? = null,
    ) = withContext(Dispatchers.IO) {
        val s = session(acc)
        val msg = MimeMessage(s).apply {
            setFrom(InternetAddress(acc.email, acc.name.ifBlank { null }, "UTF-8"))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to.replace(';', ','), false))
            setSubject(subject, "UTF-8")
            setText(text, "UTF-8")
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
