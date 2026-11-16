package app.vestari.data

/**
 * Помощник по тексту письма. Работает прямо на телефоне, по правилам — без отправки текста в интернет.
 * Каждое действие возвращает новый текст и число правок; применять или нет — решает пользователь.
 */
object TextAssist {

    data class Result(val text: String, val changes: Int)

    enum class Tone(val title: String) { Formal("Официальнее"), Friendly("Дружелюбнее") }

    /** Без учёта регистра и для кириллицы (inline-флаг `(?i)` в Java работает только с латиницей). */
    private fun ci(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    // ---- Исправить ошибки ----

    /** Частые опечатки и ошибки: слово целиком, без учёта регистра первой буквы. */
    private val typos = mapOf(
        "вообщем" to "в общем", "извените" to "извините", "зделать" to "сделать", "зделал" to "сделал",
        "щас" to "сейчас", "тоесть" to "то есть", "всмысле" to "в смысле", "координально" to "кардинально",
        "в кратце" to "вкратце", "впринципе" to "в принципе", "ихний" to "их", "ложить" to "класть",
        "здраствуйте" to "здравствуйте", "пожалуста" to "пожалуйста", "пожалйста" to "пожалуйста",
        "спосибо" to "спасибо", "зделаю" to "сделаю", "зделаем" to "сделаем", "прийдёт" to "придёт", "прийдет" to "придет", "агенство" to "агентство",
        "калектив" to "коллектив", "дефицит" to "дефицит", "будующий" to "будущий", "следущий" to "следующий",
        "поскольку" to "поскольку", "в течении" to "в течение", "вобще" to "вообще", "тогоже" to "того же",
        "инциндент" to "инцидент", "эксперемент" to "эксперимент", "конечно же" to "конечно",
    ).filter { it.key != it.value }

    fun fixErrors(input: String): Result {
        var changes = 0
        fun apply(regex: Regex, transform: (MatchResult) -> String, s: String): String =
            regex.replace(s) { m -> transform(m).also { if (it != m.value) changes++ } }

        var t = input
        // Опечатки из словаря, сохраняя заглавную первую букву.
        typos.forEach { (wrong, right) ->
            t = apply(ci("(?<![\\p{L}])${Regex.escape(wrong)}(?![\\p{L}])"), { m ->
                if (m.value.first().isUpperCase()) right.replaceFirstChar { it.uppercase() } else right
            }, t)
        }
        // Пробел перед знаком препинания убираем, после — добавляем.
        t = apply(Regex("[ \\t]+([,.!?;:)])"), { it.groupValues[1] }, t)
        t = apply(Regex("([,;:!?])(?=[\\p{L}])"), { it.groupValues[1] + " " }, t)
        t = apply(Regex("(?<=[\\p{Ll}])\\.(?=[\\p{Lu}])"), { ". " }, t)
        // Двойные пробелы и пробелы в конце строк.
        t = apply(Regex("(?<=\\S)[ \\t]{2,}(?=\\S)"), { " " }, t)
        t = apply(Regex("[ \\t]+(?=\\n|$)"), { "" }, t)
        // Больше двух пустых строк подряд — лишнее.
        t = apply(Regex("\\n{4,}"), { "\n\n\n" }, t)
        // Заглавная буква в начале предложения и абзаца.
        t = apply(Regex("(^|[.!?]\\s+|\\n\\s*)(\\p{Ll})"), { it.groupValues[1] + it.groupValues[2].uppercase() }, t)
        // «Тире» из дефиса с пробелами.
        t = apply(Regex("(?<=\\S) - (?=\\S)"), { " — " }, t)
        return Result(t, changes)
    }

    // ---- Сделать короче ----

    private val fillers = listOf(
        "хотелось бы отметить, что ", "хочу отметить, что ", "дело в том, что ", "стоит отметить, что ",
        "как бы ", "в принципе, ", "в принципе ", "на самом деле, ", "на самом деле ", "собственно говоря, ",
        "честно говоря, ", "в общем-то, ", "в общем-то ", "так сказать, ", "так сказать ", "по большому счёту, ",
        "по сути, ", "я думаю, что ", "мне кажется, что ", "как известно, ", "в целом, ", "буквально ",
        "просто ", "вообще-то, ", "вообще-то ", "конечно, ", "безусловно, ",
    )
    private val wordy = mapOf(
        "в связи с тем, что" to "так как", "в случае, если" to "если", "для того чтобы" to "чтобы",
        "в настоящий момент" to "сейчас", "в данный момент" to "сейчас", "на сегодняшний день" to "сейчас",
        "в течение некоторого времени" to "какое-то время", "буду очень благодарен" to "буду благодарен",
        "буду очень благодарна" to "буду благодарна", "имеет место быть" to "есть", "является" to "—",
        "осуществить" to "сделать", "произвести оплату" to "оплатить", "предоставить информацию" to "сообщить",
        "в кратчайшие сроки" to "быстро", "в рамках" to "в",
    ).filterKeys { it != "является" }

    fun shorten(input: String): Result {
        var changes = 0
        var t = input
        wordy.forEach { (long, short) ->
            t = ci("${Regex.escape(long)}").replace(t) { m ->
                changes++
                if (m.value.first().isUpperCase()) short.replaceFirstChar { it.uppercase() } else short
            }
        }
        fillers.forEach { f ->
            t = ci("(?<![\\p{L}])${Regex.escape(f)}").replace(t) { m ->
                changes++
                // Если вводное слово стояло в начале предложения — следующую букву делаем заглавной.
                if (m.value.first().isUpperCase()) "\u0000" else ""
            }
        }
        t = Regex("\u0000(\\p{L})").replace(t) { it.groupValues[1].uppercase() }.replace("\u0000", "")
        // Повторы одного слова подряд («очень очень»).
        t = ci("(?<![\\p{L}])(\\p{L}+) \\1(?![\\p{L}])").replace(t) { changes++; it.groupValues[1] }
        val fixed = fixErrors(t)
        return Result(fixed.text, changes + if (changes > 0) 0 else fixed.changes)
    }

    // ---- Изменить тон ----

    private val toFormal = listOf(
        "^\\s*(привет|приветик|хай|здорово)[!,.]*" to "Здравствуйте!",
        "(?<![\\p{L}])спасибки(?![\\p{L}])" to "спасибо",
        "(?<![\\p{L}])спс(?![\\p{L}])" to "спасибо",
        "(?<![\\p{L}])ок(ей)?(?![\\p{L}])" to "хорошо",
        "(?<![\\p{L}])окей(?![\\p{L}])" to "хорошо",
        "(?<![\\p{L}])щас(?![\\p{L}])" to "сейчас",
        "(?<![\\p{L}])норм(ально)?(?![\\p{L}])" to "хорошо",
        "(?<![\\p{L}])кста(ти)?(?![\\p{L}])" to "кстати",
        "(?<![\\p{L}])плиз(?![\\p{L}])" to "пожалуйста",
        "(?<![\\p{L}])пж(?![\\p{L}])" to "пожалуйста",
        "(?<![\\p{L}])давай(те)? созвонимся" to "предлагаю созвониться",
        "\\n\\s*(пока|до связи|на связи|обнимаю)[!.]*\\s*$" to "\n\nС уважением,",
    )
    private val toFriendly = listOf(
        "^\\s*(здравствуйте|добрый день|доброго времени суток)[!,.]*" to "Привет!",
        "(?<![\\p{L}])благодарю(?![\\p{L}])" to "спасибо",
        "(?<![\\p{L}])прошу Вас(?![\\p{L}])" to "пожалуйста,",
        "(?<![\\p{L}])предлагаю созвониться" to "давай созвонимся",
        "(?<![\\p{L}])сообщаю, что(?![\\p{L}])" to "",
        "\\n\\s*(с уважением|с наилучшими пожеланиями)[,.]*\\s*" to "\n\nХорошего дня!\n",
    )

    fun changeTone(input: String, tone: Tone): Result {
        var changes = 0
        var t = input
        val rules = if (tone == Tone.Formal) toFormal else toFriendly
        rules.forEach { (pattern, replacement) ->
            t = ci("$pattern").replace(t) { m ->
                changes++
                val keepCap = replacement.isNotEmpty() && m.value.trimStart().firstOrNull()?.isUpperCase() == true &&
                    !replacement.first().isUpperCase() && replacement.first() != '\n'
                if (keepCap) replacement.replaceFirstChar { it.uppercase() } else replacement
            }
        }
        if (tone == Tone.Formal) {
            // Восклицательные знаки подряд и смайлики в деловом письме ни к чему.
            t = Regex("!{2,}").replace(t) { changes++; "!" }
            t = Regex("\\s*(:\\)|:-\\)|\\)\\)+|:D|😊|😀|😉|🙂|👍)").replace(t) { changes++; "" }
        }
        val fixed = fixErrors(t)
        return Result(fixed.text, changes)
    }

    /** Простая проверка адреса для подсветки чипа. */
    fun isEmail(s: String) = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$").matches(s.trim())
}
