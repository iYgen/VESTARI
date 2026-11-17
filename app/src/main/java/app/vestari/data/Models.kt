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
    /** Шифрование IMAP: "ssl" — SSL/TLS сразу при подключении, "starttls", "none". */
    val imapSecurity: String = "ssl",
    /** Шифрование SMTP. Пусто — по порту, как было раньше: 465 → SSL/TLS, иначе STARTTLS. */
    val smtpSecurity: String = "",
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
    /** Родительская папка (id) — папки могут быть вложены на любую глубину. */
    val parent: String? = null,
)

/** Строка дерева папок: папка, глубина вложенности и есть ли у неё подпапки. */
data class CategoryNode(val category: Category, val depth: Int, val hasChildren: Boolean)

/**
 * Дерево папок в порядке пользователя: каждая папка, затем её подпапки (с отступом), на любую глубину.
 * Папка с несуществующим родителем считается верхней. Свёрнутые ветки ([collapsed]) пропускаются.
 */
fun categoryTree(cats: List<Category>, collapsed: Set<String> = emptySet()): List<CategoryNode> {
    val ids = cats.map { it.id }.toSet()
    val byParent = cats.groupBy { c -> c.parent?.takeIf { it in ids && it != c.id } }
    val out = mutableListOf<CategoryNode>()
    val seen = mutableSetOf<String>()
    fun walk(c: Category, depth: Int) {
        if (!seen.add(c.id)) return
        val kids = byParent[c.id].orEmpty()
        out += CategoryNode(c, depth, kids.isNotEmpty())
        if (c.id !in collapsed) kids.forEach { walk(it, depth + 1) }
    }
    byParent[null].orEmpty().forEach { walk(it, 0) }
    return out
}

/** Подпапки первого уровня. */
fun List<Category>.childrenOf(id: String?): List<Category> = filter { it.parent == id && it.id != id }

/** Папка и все её подпапки на любую глубину. */
fun List<Category>.subtreeIds(id: String): Set<String> {
    val out = mutableSetOf(id)
    var frontier = setOf(id)
    while (frontier.isNotEmpty()) {
        frontier = filter { it.parent in frontier && out.add(it.id) }.map { it.id }.toSet()
    }
    return out
}

/** Верхняя папка (без родителя) или папка, чей родитель удалён. */
fun List<Category>.isTopLevel(c: Category): Boolean = c.parent == null || none { it.id == c.parent }

/** Цепочка от верхней папки до [id] включительно: «Работа › Проекты › 2024». */
fun List<Category>.pathTo(id: String?): List<Category> {
    val byId = associateBy { it.id }
    val out = ArrayList<Category>()
    var cur = id?.let { byId[it] }
    while (cur != null && out.none { it.id == cur!!.id }) {
        out.add(0, cur); cur = cur.parent?.let { byId[it] }
    }
    return out
}

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

    // ---- внешний вид (экран «Внешний вид») ----
    /** Логотип Vestari в шапке всех разделов. */
    val showLogo: Boolean = true,
    /** Рядом со значком — надпись «Vestari». */
    val logoText: Boolean = false,
    /** Узор фона всех экранов: id из [app.vestari.ui.components.Backdrops]. */
    val composeBackdrop: String = "wave",
    /** Путь к своей картинке (копия во внутренней папке приложения). */
    val customBackdrop: String? = null,
    /** Где показывать картинку. */
    val picturePlace: PicturePlace = PicturePlace.Off,
    /** Как вписывать картинку. */
    val pictureFit: PictureFit = PictureFit.Crop,
    /** Какую часть картинки показывать при обрезке: 0 — верх, 1 — центр, 2 — низ. */
    val pictureAlign: Int = 1,
    /** Затемнение/осветление картинки, чтобы текст читался: 0…0.9. */
    val pictureVeil: Float = 0.45f,
    /** Высота картинки в шапке и в подвале, dp. */
    val pictureHeight: Int = 180,
    /** Скругление карточек писем: 0 — острые, 1 — обычные, 2 — сильные. */
    val cornerStyle: Int = 1,

    /** Версия правил раскладки, по которой разложены письма (см. [Categorizer.VERSION]). */
    val sortVersion: Int = 0,

    // ---- загрузка писем (экран «Загрузка писем») ----
    /** Сколько писем запрашивать у сервера за раз. Больше — быстрее, но дольше каждый запрос. */
    val syncBatch: Int = 500,
    /** Сколько последних писем загружать в каждой папке; 0 — все. */
    val syncLimit: Int = 0,
    /** Докачивать старые письма только по Wi-Fi (новые письма приходят всегда). */
    val syncWifiOnly: Boolean = false,
    /** Докачка старых писем поставлена на паузу вручную. */
    val syncPaused: Boolean = false,
    /** Показывать ход загрузки в строке состояния (на Android 16 — значок рядом с камерой). */
    val syncProgressNotice: Boolean = true,

    // ---- уведомления о письмах ----
    /** Смахнуть письмо в шторке: true — отметить прочитанным, false — только скрыть. */
    val notifySwipeMarksRead: Boolean = false,
    /** Сколько писем ящика показывать в шторке одновременно (1…5). */
    val notifyMax: Int = 5,
)

/**
 * Слить уже отсортированный (новые сверху) список писем с новой пачкой за один проход, без пересортировки всего ящика.
 * Письмо, которое уже есть, заменяется новой версией (превью сохраняется, если в новой его нет).
 */
fun mergeSorted(old: List<MailMessage>, add: List<MailMessage>): List<MailMessage> {
    if (add.isEmpty()) return old
    val incoming = HashMap<String, MailMessage>(add.size * 2)
    add.forEach { incoming[it.key] = it }
    val replaced = HashSet<String>()
    val base = old.map { o ->
        val n = incoming[o.key] ?: return@map o
        replaced += o.key
        if (n.preview.isEmpty() && o.preview.isNotEmpty()) n.copy(preview = o.preview) else n
    }
    val fresh = incoming.values.filter { it.key !in replaced }.sortedByDescending { it.date }
    if (fresh.isEmpty()) return base
    val out = ArrayList<MailMessage>(base.size + fresh.size)
    var i = 0; var j = 0
    while (i < base.size && j < fresh.size) {
        if (base[i].date >= fresh[j].date) out += base[i++] else out += fresh[j++]
    }
    while (i < base.size) out += base[i++]
    while (j < fresh.size) out += fresh[j++]
    return out
}

/** Варианты размера пачки: подпись и число писем. */
val SyncBatchOptions = listOf("Экономно" to 150, "Обычно" to 500, "Быстро" to 1000)

/** Варианты ограничения: подпись и число последних писем в папке (0 — все). */
val SyncLimitOptions = listOf("Все" to 0, "30 000" to 30_000, "10 000" to 10_000, "2 000" to 2_000)

/** Где показывать свою картинку. */
enum class PicturePlace(val title: String) {
    Off("Нет"), Header("В шапке"), Footer("Внизу"), Both("Шапка и низ"), Full("Весь экран"),
}

/** Как вписать картинку. */
enum class PictureFit(val title: String) {
    Crop("Обрезать"), Width("По ширине"), Fit("Целиком"), Stretch("Растянуть"),
}

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
data class SyncProgress(
    val loaded: Int = 0,
    val total: Int = 0,
    val active: Boolean = false,
    /** Почему загрузка стоит: «Пауза», «Жду Wi-Fi»; null — идёт или закончена. */
    val note: String? = null,
) {
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

/** Варианты шифрования соединения с сервером почты: подпись → значение в [Account]. */
val SecurityOptions = listOf("SSL/TLS" to "ssl", "STARTTLS" to "starttls", "Нет" to "none")

/** Обычное шифрование для порта: 465/993/995 — SSL/TLS сразу, 25/587/143 — STARTTLS, иначе null (не угадать). */
fun securityForPort(port: Int): String? = when (port) {
    465, 993, 995 -> "ssl"
    25, 587, 143, 110 -> "starttls"
    else -> null
}

val Account.smtpMode: String get() = smtpSecurity.ifBlank { if (smtpPort == 465) "ssl" else "starttls" }
val Account.imapMode: String get() = imapSecurity.ifBlank { "ssl" }
