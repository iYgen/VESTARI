package app.vestari.data

/**
 * Автоматическая раскладка писем по встроенным папкам.
 * Ручной перенос письма пользователем всегда важнее (см. Repository.assign).
 */
object Categorizer {
    const val RECEIPTS = "receipts"
    const val SALES = "sales"
    const val TICKETS = "tickets"
    const val NEWSLETTERS = "newsletters"
    const val GOV = "gov"
    const val SELF = "self"

    val builtin = listOf(
        Category(RECEIPTS, "Чеки", "receipt", builtin = true),
        Category(SALES, "Скидки", "tag", builtin = true),
        Category(TICKETS, "Билеты", "plane", builtin = true),
        Category(NEWSLETTERS, "Рассылки", "megaphone", builtin = true),
        Category(GOV, "Госписьма", "bank", builtin = true),
        Category(SELF, "Письма себе", "mail", builtin = true),
    )

    private val govDomains = listOf(
        "gosuslugi.ru", "nalog.ru", "nalog.gov.ru", "mos.ru", "sfr.gov.ru", "pfr.gov.ru", "gov.ru",
        "fns.ru", "rosreestr.ru", "gibdd.ru", "pochta.ru", "cbr.ru", "mvd.ru", "fssp.gov.ru",
    )
    private val ticketDomains = listOf(
        "aeroflot.ru", "s7.ru", "pobeda.aero", "utair.ru", "rzd.ru", "tutu.ru", "aviasales.ru", "ostrovok.ru",
        "uralairlines.ru", "nordwindairlines.ru", "smartavia.com", "azimuth.aero", "yandex-team-travel", "kassir.ru",
        "afisha.ru", "ticketland.ru", "kinopoisk.ru", "redkassa.ru", "travel.yandex.ru", "onetwotrip.com",
    )
    private val receiptDomains = listOf(
        "ofd.ru", "1-ofd.ru", "ofd-ya.ru", "platformaofd.ru", "taxcom.ru", "kontur.ru", "sbis.ru", "ofd.astral.ru",
        "check.ofd", "lk.platformaofd.ru", "nalog-check",
    )

    private val receiptWords = Regex("\\bчек\\b|кассов(ый|ого) чек|электронный чек|receipt|оплачен|заказ оплачен|фискальн|ОФД", RegexOption.IGNORE_CASE)
    private val ticketWords = Regex("билет|посадочн|маршрутн(ая|ой) квитанц|бронирован|e-ticket|boarding pass|рейс\\b|электронн(ый|ого) билет", RegexOption.IGNORE_CASE)
    private val salesWords = Regex("скидк|распродаж|промокод|акци[яи]|выгод|−\\d+%|-\\d+%|\\d+ ?%|sale\\b|кэшбэк|бонус", RegexOption.IGNORE_CASE)
    private val govWords = Regex("госуслуг|налогов|ФНС|Социальн(ый|ого) фонд|Росреестр|ГИБДД|штраф", RegexOption.IGNORE_CASE)

    fun categorize(m: MailMessage, ownEmails: Set<String>): String? {
        val from = m.fromEmail.lowercase()
        val domain = from.substringAfter('@')
        val text = m.subject + " " + m.preview.take(200)

        if (from in ownEmails) return SELF
        if (govDomains.any { domain == it || domain.endsWith(".$it") } || govWords.containsMatchIn(m.subject)) return GOV
        if (ticketDomains.any { domain == it || domain.endsWith(".$it") } || ticketWords.containsMatchIn(m.subject)) return TICKETS
        if (receiptDomains.any { domain.contains(it) } || receiptWords.containsMatchIn(m.subject)) return RECEIPTS
        if (salesWords.containsMatchIn(text)) return SALES
        if (m.listUnsubscribe || from.startsWith("news") || from.contains("newsletter") ||
            from.startsWith("noreply") || from.startsWith("no-reply") || from.startsWith("info@")
        ) return NEWSLETTERS
        return null
    }
}
