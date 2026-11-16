package app.vestari.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CategorizerTest {
    private val own = setOf("me@mail.ru")
    private fun msg(from: String, subject: String, preview: String = "", unsub: Boolean = false) = MailMessage(
        accountId = "a", uid = 1, fromEmail = from, fromName = "", subject = subject, preview = preview,
        date = 0, seen = false, listUnsubscribe = unsub,
    )
    private fun cat(m: MailMessage) = Categorizer.categorize(m, own)

    @Test fun self() = assertEquals(Categorizer.SELF, cat(msg("me@mail.ru", "Заметка")))
    @Test fun gov() = assertEquals(Categorizer.GOV, cat(msg("no-reply@gosuslugi.ru", "Запись подтверждена")))
    @Test fun govSubdomain() = assertEquals(Categorizer.GOV, cat(msg("info@lkfl2.nalog.ru", "Уведомление")))
    @Test fun tickets() = assertEquals(Categorizer.TICKETS, cat(msg("info@aeroflot.ru", "Ваш билет: Москва — Казань")))
    @Test fun ticketsBySubject() = assertEquals(Categorizer.TICKETS, cat(msg("x@shop.ru", "Электронный билет на концерт")))
    @Test fun receiptOfd() = assertEquals(Categorizer.RECEIPTS, cat(msg("noreply@ofd.ru", "Кассовый чек")))
    @Test fun receiptPlatformaOfd() = assertEquals(Categorizer.RECEIPTS, cat(msg("echeck@platformaofd.ru", "Ваш электронный документ")))
    @Test fun receiptAnyOfdDomain() = assertEquals(Categorizer.RECEIPTS, cat(msg("noreply@ofd.evotor.ru", "Документ по покупке")))
    @Test fun receiptOfdByName() = assertEquals(
        Categorizer.RECEIPTS,
        cat(msg("robot@lk-mail.example", "Документ").copy(fromName = "Платформа ОФД")),
    )
    // В чеке ОФД упоминается ФНС — всё равно это чек, а не госписьмо.
    @Test fun receiptBeatsGov() = assertEquals(Categorizer.RECEIPTS, cat(msg("noreply@1-ofd.ru", "Чек передан в ФНС")))
    @Test fun receiptPaid() = assertEquals(Categorizer.RECEIPTS, cat(msg("orders@ozon.ru", "Заказ оплачен")))
    @Test fun sales() = assertEquals(Categorizer.SALES, cat(msg("promo@shop.ru", "Скидки до 50% только сегодня")))
    @Test fun newsletter() = assertEquals(Categorizer.NEWSLETTERS, cat(msg("digest@habr.com", "Лучшее за неделю", unsub = true)))
    @Test fun personal() = assertEquals(null, cat(msg("friend@yandex.ru", "Привет, как дела?")))
}
