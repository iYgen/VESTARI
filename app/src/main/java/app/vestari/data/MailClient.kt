package app.vestari.data

import com.sun.mail.imap.IMAPFolder
import java.util.Date
import java.util.Properties
import javax.mail.Address
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.Transport
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Работа с почтой по стандартным протоколам IMAP (чтение) и SMTP (отправка). */
object MailClient {

    private fun session(acc: Account): Session {
        val p = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", acc.imapHost)
            put("mail.imaps.port", acc.imapPort.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "30000")
            put("mail.imaps.partialfetch", "false")

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

    private inline fun <T> withInbox(acc: Account, password: String, mode: Int, block: (IMAPFolder) -> T): T {
        val store: Store = session(acc).getStore("imaps")
        store.connect(acc.imapHost, acc.imapPort, acc.email, password)
        try {
            val folder = store.getFolder("INBOX") as IMAPFolder
            folder.open(mode)
            try {
                return block(folder)
            } finally {
                runCatching { folder.close(false) }
            }
        } finally {
            runCatching { store.close() }
        }
    }

    /** Проверка логина и пароля при добавлении ящика. Бросает исключение с понятным текстом. */
    suspend fun test(acc: Account, password: String) = withContext(Dispatchers.IO) {
        try {
            withInbox(acc, password, Folder.READ_ONLY) { it.messageCount }
        } catch (e: javax.mail.AuthenticationFailedException) {
            throw IllegalStateException("Почта не приняла пароль. Нужен именно пароль для внешнего приложения, а не обычный.")
        } catch (e: javax.mail.MessagingException) {
            throw IllegalStateException("Не удалось подключиться к ${acc.imapHost}: ${e.message ?: e.javaClass.simpleName}")
        }
        // Отправку тоже проверяем, чтобы не узнать о проблеме при первом письме.
        try {
            val t = session(acc).getTransport("smtp")
            t.connect(acc.smtpHost, acc.smtpPort, acc.email, password)
            t.close()
        } catch (e: javax.mail.MessagingException) {
            throw IllegalStateException("Чтение работает, но отправка через ${acc.smtpHost} — нет: ${e.message ?: ""}")
        }
    }

    /** Последние [limit] писем из «Входящих». Для свежих писем дополнительно грузится короткое превью. */
    suspend fun fetchInbox(acc: Account, password: String, limit: Int = 200, previewFor: Int = 60): List<MailMessage> =
        withContext(Dispatchers.IO) {
            withInbox(acc, password, Folder.READ_ONLY) { folder ->
                val total = folder.messageCount
                if (total == 0) return@withInbox emptyList()
                val start = maxOf(1, total - limit + 1)
                val msgs = folder.getMessages(start, total)
                val fp = FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE)
                    add(FetchProfile.Item.FLAGS)
                    add(UIDFolder.FetchProfileItem.UID)
                    add("List-Unsubscribe")
                }
                folder.fetch(msgs, fp)
                msgs.reversed().mapIndexed { i, m ->
                    val from = m.from?.firstOrNull() as? InternetAddress
                    MailMessage(
                        accountId = acc.id,
                        uid = folder.getUID(m),
                        fromEmail = from?.address.orEmpty(),
                        fromName = decode(from?.personal),
                        to = addresses(m.getRecipients(Message.RecipientType.TO)),
                        subject = decode(m.subject).ifBlank { "(без темы)" },
                        preview = if (i < previewFor) runCatching { previewOf(m) }.getOrDefault("") else "",
                        date = (m.receivedDate ?: m.sentDate ?: Date()).time,
                        seen = m.isSet(Flags.Flag.SEEN),
                        flagged = m.isSet(Flags.Flag.FLAGGED),
                        listUnsubscribe = m.getHeader("List-Unsubscribe")?.isNotEmpty() == true,
                        messageId = (m as? MimeMessage)?.messageID,
                    )
                }
            }
        }

    suspend fun loadBody(acc: Account, password: String, uid: Long): MailBody = withContext(Dispatchers.IO) {
        withInbox(acc, password, Folder.READ_WRITE) { folder ->
            val m = folder.getMessageByUID(uid) ?: return@withInbox MailBody(null, "Письмо не найдено на сервере.")
            val body = extract(m)
            // Открыли письмо — помечаем прочитанным и на сервере, как сделал бы любой клиент.
            runCatching { m.setFlag(Flags.Flag.SEEN, true) }
            body
        }
    }

    suspend fun setFlag(acc: Account, password: String, uid: Long, flag: Flags.Flag, value: Boolean) =
        withContext(Dispatchers.IO) {
            withInbox(acc, password, Folder.READ_WRITE) { folder ->
                folder.getMessageByUID(uid)?.setFlag(flag, value)
            }
        }

    /** Удаление = перенос в корзину (если сервер её отдаёт), иначе пометка DELETED. */
    suspend fun delete(acc: Account, password: String, uid: Long) = withContext(Dispatchers.IO) {
        withInbox(acc, password, Folder.READ_WRITE) { folder ->
            val m = folder.getMessageByUID(uid) ?: return@withInbox
            val trash = folder.store.defaultFolder.list("*").firstOrNull { f ->
                (f as? IMAPFolder)?.attributes?.any { it.equals("\\Trash", true) } == true ||
                    f.name.equals("Trash", true) || f.name.equals("Корзина", true)
            }
            if (trash != null && trash.fullName != folder.fullName) folder.moveMessages(arrayOf(m), trash)
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
        val b = extract(m)
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

    private fun extract(p: Part): MailBody {
        var html: String? = null
        var text: String? = null
        fun walk(part: Part) {
            when {
                part.isMimeType("text/html") && html == null && !isAttachment(part) -> html = part.content as? String
                part.isMimeType("text/plain") && text == null && !isAttachment(part) -> text = part.content as? String
                part.isMimeType("multipart/*") -> {
                    val mp = part.content as Multipart
                    for (i in 0 until mp.count) walk(mp.getBodyPart(i))
                }
                part.isMimeType("message/rfc822") -> (part.content as? Part)?.let(::walk)
            }
        }
        walk(p)
        return MailBody(html, text)
    }

    private fun isAttachment(p: Part) = Part.ATTACHMENT.equals(p.disposition, ignoreCase = true)
}
