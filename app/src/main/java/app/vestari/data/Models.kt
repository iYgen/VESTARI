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
    /** Подпись, которая добавляется в конец новых писем с этого ящика. */
    val signature: String = "",
)

/** Файл, приложенный к черновику. Копия лежит во внутренней папке приложения ([path]). */
@Serializable
data class DraftAttachment(val name: String, val mime: String, val size: Long, val path: String)

/**
 * Черновик письма. Сохраняется автоматически во время набора.
 * Если задан [sendAt] — это запланированное письмо, его отправит фоновая задача в указанное время.
 */
@Serializable
data class Draft(
    val id: String,
    val accountId: String?,
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String = "",
    val body: String = "",
    val attachments: List<DraftAttachment> = emptyList(),
    val replyToKey: String? = null,
    val inReplyTo: String? = null,
    val updated: Long = 0L,
    val sendAt: Long? = null,
) {
    val isEmpty: Boolean
        get() = to.isEmpty() && cc.isEmpty() && bcc.isEmpty() && subject.isBlank() && body.isBlank() && attachments.isEmpty()
}

/** Адресат для подсказок в поле «Кому». */
data class Contact(val email: String, val name: String, val score: Int)

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
    /** Папка пришла с сервера (создана в самой почте, например в Mail.ru). */
    val remote: Boolean = false,
)

/** Вид списка писем. */
enum class ListStyle(val title: String, val hint: String) {
    Cards("Карточки", "Крупно, с превью текста"),
    Compact("Компактный", "Карточки ниже, превью в одну строку"),
    List("Список", "Плотный список без карточек, как в почте на компьютере"),
}

@Serializable
data class Settings(
    val palette: Int = 0,
    /** 0 — как в системе, 1 — светлая, 2 — тёмная. */
    val darkMode: Int = 0,
    /** null — все ящики сразу. */
    val selectedAccountId: String? = null,
    val notifications: Boolean = true,
    /** Загружать картинки из интернета в письмах сразу (иначе — по кнопке). */
    val autoImages: Boolean = true,
    /** Серверные папки, которые пользователь удалил из списка, — заново не создаём. */
    val ignoredFolders: Set<String> = emptySet(),
    val listStyle: ListStyle = ListStyle.Compact,
    /** Размер заголовка экрана: 0 — маленький, 1 — средний, 2 — крупный. */
    val titleSize: Int = 0,
    /** Показывать превью текста письма. */
    val showPreview: Boolean = true,

    // ---- умные функции (экран «Умные функции») ----
    /** Автоматически раскладывать письма по встроенным папкам (Чеки, Билеты…). */
    val autoSort: Boolean = true,
    /** Запоминать ручные переносы и раскладывать так же остальные письма отправителя. */
    val learnFromMoves: Boolean = true,
    /** Папки, созданные в самой почте, становятся папками приложения. */
    val serverFolders: Boolean = true,
    /** Находить коды подтверждения и показывать их с кнопкой «Скопировать». */
    val otpCodes: Boolean = true,
    /** Разделители «Сегодня», «Вчера», «На этой неделе» в списке. */
    val groupByDate: Boolean = true,
    /** Рассылки и скидки не дают уведомлений и красных счётчиков. */
    val quietPromos: Boolean = true,
    /** Во «Все» не показывать рассылки и скидки — они остаются в своих папках. */
    val cleanInbox: Boolean = false,
    /** Во «Все» письма выбранных папок сворачиваются в одну строку («Рассылки · New · Ozon, Сбер…»). */
    val bundleFolders: Boolean = true,
    /** Какие папки сворачивать. По умолчанию — как в Mail.ru: рассылки, чеки, скидки, госписьма. */
    val bundled: Set<String> = setOf("newsletters", "receipts", "sales", "gov"),

    // ---- оформление экрана письма ----
    /** Логотип Vestari вверху экрана письма. */
    val showLogo: Boolean = true,
    /** Фон экрана письма: id из [app.vestari.ui.components.Backdrops] или «custom». */
    val composeBackdrop: String = "wave",
    /** Путь к своей картинке фона (копия во внутренней папке приложения). */
    val customBackdrop: String? = null,
)

/**
 * Правило раскладки: письма от адреса (`ivan@shop.ru`) или домена (`@shop.ru`) идут в папку.
 * [categoryId] пустой — «без папки».
 */
@Serializable
data class SenderRule(val pattern: String, val categoryId: String, val created: Long = 0L) {
    fun matches(email: String): Boolean {
        val e = email.lowercase()
        if (!pattern.startsWith("@")) return e == pattern
        val domain = e.substringAfter('@', "")
        val p = pattern.drop(1)
        return domain == p || domain.endsWith(".$p")
    }
}

/** Ход загрузки писем ящика: сколько уже скачано из общего числа на сервере. */
data class SyncProgress(val loaded: Int = 0, val total: Int = 0, val active: Boolean = false) {
    val fraction: Float get() = if (total <= 0) 0f else (loaded.toFloat() / total).coerceIn(0f, 1f)
    val loading: Boolean get() = active && (total == 0 || loaded < total)
}

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
