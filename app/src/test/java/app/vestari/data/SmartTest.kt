package app.vestari.data

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartTest {
    @Test fun codeInSubject() = assertEquals("482913", Smart.otp("Ваш код подтверждения: 482913", ""))
    @Test fun codeInText() = assertEquals("7731", Smart.otp("Вход в аккаунт", "Никому не сообщайте код 7731. Он действует 10 минут."))
    @Test fun englishCode() = assertEquals("120034", Smart.otp("Your verification code", "Use 120034 to sign in"))
    @Test fun dashedCode() = assertEquals("123456", Smart.otp("Код для входа 123-456", ""))
    @Test fun noContextNoCode() = assertNull(Smart.otp("Заказ 948321 отправлен", "Трек-номер 1234567"))
    @Test fun yearIsNotCode() = assertNull(Smart.otp("Пароль изменён в 2026 году", ""))
    @Test fun priceIsNotCode() = assertNull(Smart.otp("Код акции", "Скидка на 15000₽ только сегодня"))

    @Test fun buckets() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 19, 12, 0) }.timeInMillis
        fun at(m: Int, d: Int) = Calendar.getInstance().apply { set(2026, m, d, 9, 0) }.timeInMillis
        assertEquals("Сегодня", Smart.dateBucket(at(Calendar.NOVEMBER, 19), now))
        assertEquals("Вчера", Smart.dateBucket(at(Calendar.NOVEMBER, 18), now))
        assertEquals("Ранее в этом месяце", Smart.dateBucket(at(Calendar.NOVEMBER, 2), now))
        assertEquals("Сентябрь", Smart.dateBucket(at(Calendar.SEPTEMBER, 2), now))
        assertTrue(Smart.dateBucket(Calendar.getInstance().apply { set(2024, Calendar.MAY, 1) }.timeInMillis, now) == "Май 2024")
    }

    @Test fun rules() {
        assertTrue(SenderRule("@ozon.ru", "x").matches("news@mail.ozon.ru"))
        assertTrue(SenderRule("ivan@site.ru", "x").matches("Ivan@Site.ru"))
        assertTrue(!SenderRule("@ozon.ru", "x").matches("a@notozon.ru"))
    }
}
