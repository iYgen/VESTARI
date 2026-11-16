package app.vestari.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAssistTest {
    @Test fun fixesPunctuationAndCapitals() {
        val r = TextAssist.fixErrors("привет ,как дела?всё хорошо.  жду ответа")
        assertEquals("Привет, как дела? Всё хорошо. Жду ответа", r.text)
        assertTrue(r.changes > 0)
    }

    @Test fun fixesTypos() {
        assertEquals("Извините, в общем сейчас сделаю", TextAssist.fixErrors("Извените, вообщем щас зделаю").text)
    }

    @Test fun keepsCorrectText() {
        val r = TextAssist.fixErrors("Добрый день! Отправляю счёт.")
        assertEquals("Добрый день! Отправляю счёт.", r.text)
        assertEquals(0, r.changes)
    }

    @Test fun shortens() {
        val r = TextAssist.shorten("Хотелось бы отметить, что в связи с тем, что сроки сдвинулись, мы перенесём встречу.")
        assertEquals("Так как сроки сдвинулись, мы перенесём встречу.", r.text)
        assertTrue(r.changes >= 2)
    }

    @Test fun formalTone() {
        val r = TextAssist.changeTone("привет! спс за файл, ок, щас посмотрю :)\nпока", TextAssist.Tone.Formal)
        assertTrue(r.text, r.text.startsWith("Здравствуйте!"))
        assertTrue(r.text, r.text.contains("Спасибо за файл"))
        assertTrue(r.text, r.text.contains("сейчас посмотрю"))
        assertFalse(r.text, r.text.contains(":)"))
        assertTrue(r.text, r.text.trimEnd().endsWith("С уважением,"))
    }

    @Test fun friendlyTone() {
        val r = TextAssist.changeTone("Здравствуйте! Благодарю за ответ.\nС уважением,\nИван", TextAssist.Tone.Friendly)
        assertTrue(r.text, r.text.startsWith("Привет!"))
        assertTrue(r.text, r.text.contains("Спасибо за ответ"))
        assertTrue(r.text, r.text.contains("Хорошего дня!"))
    }

    @Test fun emails() {
        assertTrue(TextAssist.isEmail("anna.petrova@example.com"))
        assertFalse(TextAssist.isEmail("anna@"))
        assertFalse(TextAssist.isEmail("просто текст"))
    }
}
