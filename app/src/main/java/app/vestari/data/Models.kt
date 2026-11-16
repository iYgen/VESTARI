package app.vestari.data

import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val id: String,
    val email: String,
    val name: String,
    val provider: String,
    val imapHost: String,
    val imapPort: Int,
    val smtpHost: String,
    val smtpPort: Int,
    /** Индекс цвета аватарки в [app.vestari.ui.theme.AvatarColors]. */
    val color: Int = 0,
)

@Serializable
data class MailMessage(
    val accountId: String,
    val uid: Long,
    val folder: String = "INBOX",
    val fromEmail: String,
    val fromName: String,
    val to: String = "",
    val subject: String,
    val preview: String = "",
    val date: Long,
    val seen: Boolean,
    val flagged: Boolean = false,
    val listUnsubscribe: Boolean = false,
    val messageId: String? = null,
    /** Категория, которую подобрал [Categorizer]. Ручной выбор хранится отдельно и важнее. */
    val autoCategory: String? = null,
) {
    val key: String get() = "$accountId|$folder|$uid"
    val senderTitle: String get() = fromName.ifBlank { fromEmail }
}

@Serializable
data class Category(
    val id: String,
    val name: String,
    val icon: String,
    val builtin: Boolean = false,
)

@Serializable
data class Settings(
    val palette: Int = 0,
    /** 0 — как в системе, 1 — светлая, 2 — тёмная. */
    val darkMode: Int = 0,
    /** null — все ящики сразу. */
    val selectedAccountId: String? = null,
    val notifications: Boolean = true,
)

data class MailBody(val html: String?, val text: String?)

/** Готовые настройки серверов популярных почтовиков. */
data class Provider(
    val id: String,
    val title: String,
    val domains: List<String>,
    val imapHost: String,
    val imapPort: Int = 993,
    val smtpHost: String,
    val smtpPort: Int = 465,
    val hint: String,
)

object Providers {
    val all = listOf(
        Provider(
            "mailru", "Mail.ru", listOf("mail.ru", "inbox.ru", "list.ru", "bk.ru", "internet.ru", "xmail.ru"),
            "imap.mail.ru", smtpHost = "smtp.mail.ru",
            hint = "Обычный пароль не подойдёт. В веб-почте откройте Настройки → Безопасность → " +
                "Пароли для внешних приложений → Добавить, выберите полный доступ (IMAP/SMTP) " +
                "и вставьте сюда созданный пароль.",
        ),
        Provider(
            "yandex", "Яндекс", listOf("yandex.ru", "ya.ru", "yandex.com", "narod.ru"),
            "imap.yandex.ru", smtpHost = "smtp.yandex.ru",
            hint = "Нужен пароль приложения: id.yandex.ru → Безопасность → Пароли приложений → Почта. " +
                "В настройках Яндекс Почты включите доступ по IMAP.",
        ),
        Provider(
            "gmail", "Gmail", listOf("gmail.com", "googlemail.com"),
            "imap.gmail.com", smtpHost = "smtp.gmail.com",
            hint = "Нужен пароль приложения: myaccount.google.com → Безопасность → Пароли приложений " +
                "(работает только с включённой двухэтапной проверкой).",
        ),
        Provider(
            "outlook", "Outlook", listOf("outlook.com", "hotmail.com", "live.com"),
            "outlook.office365.com", smtpHost = "smtp.office365.com", smtpPort = 587,
            hint = "Используйте пароль приложения из настроек безопасности учётной записи Microsoft.",
        ),
        Provider(
            "rambler", "Рамблер", listOf("rambler.ru", "lenta.ru", "ro.ru"),
            "imap.rambler.ru", smtpHost = "smtp.rambler.ru",
            hint = "В настройках Рамблер Почты включите «Доступ к почтовому ящику с помощью почтовых клиентов».",
        ),
        Provider(
            "custom", "Другой", emptyList(),
            "", smtpHost = "",
            hint = "Укажите серверы IMAP и SMTP вашей почты (обычно они есть в справке почтового сервиса).",
        ),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.last()
    fun guess(email: String): Provider {
        val domain = email.substringAfter('@', "").lowercase()
        return all.firstOrNull { domain in it.domains } ?: byId("custom")
    }
}
