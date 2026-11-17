package app.vestari.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MergeSortedTest {
    private fun m(uid: Long, date: Long, preview: String = "", seen: Boolean = false) =
        MailMessage(accountId = "a", uid = uid, fromEmail = "x@y", fromName = "", subject = "s$uid", preview = preview, date = date, seen = seen)

    @Test
    fun keepsNewestFirstWithoutDuplicates() {
        val old = listOf(m(5, 50), m(3, 30), m(1, 10))
        val add = listOf(m(4, 40), m(3, 30, seen = true), m(0, 5))
        val out = mergeSorted(old, add)
        assertEquals(listOf(5L, 4L, 3L, 1L, 0L), out.map { it.uid })
        assertEquals(true, out.first { it.uid == 3L }.seen)
    }

    @Test
    fun keepsOldPreviewWhenNewIsEmpty() {
        val out = mergeSorted(listOf(m(1, 10, preview = "текст")), listOf(m(1, 10)))
        assertEquals("текст", out.single().preview)
    }

    @Test
    fun emptySidesAreFine() {
        assertEquals(listOf(2L, 1L), mergeSorted(emptyList(), listOf(m(1, 10), m(2, 20)).sortedByDescending { it.date }).map { it.uid })
        assertEquals(1, mergeSorted(listOf(m(1, 10)), emptyList()).size)
    }
}
