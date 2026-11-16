package app.vestari.data

import java.util.Calendar
import java.util.Locale

/** Небольшие «умные» помощники, которые работают прямо на телефоне, без отправки писем куда-либо. */
object Smart {

    private val codeContext = Regex(
        "(код|code|парол|password|otp|pin|подтвержд|verif|одноразов|проверочн|sms|смс|вход|login|sign.?in)",
        RegexOption.IGNORE_CASE,
    )
    private val candidate = Regex("(?<![\\d.,:/\\-+])(\\d{4,8}|\\d{3}[- ]\\d{3})(?![\\d,:/%₽$€]|[.]\\d)")

    /**
     * Код подтверждения из темы или текста: 4–8 цифр (или «123-456») рядом со словами «код», «пароль»,
     * «code»… Годы, суммы, даты и номера телефонов не считаются кодом.
     */
    fun otp(subject: String, text: String): String? {
        val s = (subject + "\n" + text.take(600))
        if (!codeContext.containsMatchIn(s)) return null
        for (m in candidate.findAll(s)) {
            val raw = m.value
            val digits = raw.filter { it.isDigit() }
            if (digits.length !in 4..8) continue
            if (digits.length == 4 && digits.toInt() in 1950..2100) continue // год
            // Слово-подсказка должно быть рядом: до 60 символов до кода или 30 после.
            val around = s.substring(maxOf(0, m.range.first - 60), minOf(s.length, m.range.last + 30))
            if (!codeContext.containsMatchIn(around)) continue
            return digits
        }
        return null
    }

    fun otp(m: MailMessage): String? = otp(m.subject, m.preview)

    /** Код ещё актуален: письмо пришло не раньше суток назад. */
    fun isFresh(m: MailMessage, now: Long = System.currentTimeMillis()) = now - m.date < 24L * 3600 * 1000

    /** Подпись группы для разделителя в списке. */
    fun dateBucket(ts: Long, now: Long = System.currentTimeMillis()): String {
        val n = Calendar.getInstance().apply { timeInMillis = now }
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        val sameYear = n.get(Calendar.YEAR) == c.get(Calendar.YEAR)
        val dayDiff = daysBetween(c, n)
        return when {
            dayDiff <= 0 -> "Сегодня"
            dayDiff == 1 -> "Вчера"
            dayDiff < 7 && sameWeek(c, n) -> "На этой неделе"
            sameYear && n.get(Calendar.MONTH) == c.get(Calendar.MONTH) -> "Ранее в этом месяце"
            else -> {
                val month = MONTHS[c.get(Calendar.MONTH)]
                if (sameYear) month else "$month ${c.get(Calendar.YEAR)}"
            }
        }
    }

    private val MONTHS = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )

    private fun startOfDay(c: Calendar): Long = (c.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun daysBetween(a: Calendar, b: Calendar): Int =
        ((startOfDay(b) - startOfDay(a)) / (24L * 3600 * 1000)).toInt()

    private fun sameWeek(a: Calendar, b: Calendar): Boolean {
        val ru = Locale("ru")
        val x = Calendar.getInstance(ru).apply { timeInMillis = a.timeInMillis }
        val y = Calendar.getInstance(ru).apply { timeInMillis = b.timeInMillis }
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.WEEK_OF_YEAR) == y.get(Calendar.WEEK_OF_YEAR)
    }
}
