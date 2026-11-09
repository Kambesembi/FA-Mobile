package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesParserTest {

    private val list = NotesParser.list(Fixtures.document("msg-pms.html", "https://www.furaffinity.net/msg/pms/"))
    private val note = NotesParser.note(Fixtures.document("msg-pms-note.html", "https://www.furaffinity.net/msg/pms/1/130000005/"), 130000005)!!

    @Test
    fun listRows() {
        assertEquals(listOf(130000005L, 130000004, 130000003, 130000002, 130000001), list.notes.map { it.id })
        val first = list.notes.first()
        assertEquals("Commission question", first.subject)
        assertEquals("artist2", first.correspondent?.name)
        assertEquals("Artist2", first.correspondent?.displayName)
        assertEquals("https://a.furaffinity.net/artist2.gif", first.correspondent?.avatarUrl)
        assertEquals(1793804000L, first.sentAt)
        assertEquals(listOf(true, true, false, false, false), list.notes.map { it.unread })
    }

    @Test
    fun listPagingAndForms() {
        assertEquals("/msg/pms/2/", list.nextPath)
        assertEquals("0000000000", list.sendKey)
        assertEquals("1", list.manageValue)
    }

    @Test
    fun lastPageHasNoNext() {
        val doc = Fixtures.document("msg-pms.html")
        doc.select(".note-pagination a").last()!!.remove()
        assertNull(NotesParser.list(doc).nextPath)
    }

    @Test
    fun openNote() {
        assertEquals("Commission question", note.subject)
        assertEquals("artist2", note.from?.name)
        assertEquals("testuser", note.to?.name)
        assertEquals(1793804000L, note.sentAt)
        assertTrue(note.bodyHtml.contains("<b>commissions</b>"))
        // FA's scam warning and the trailing line breaks are not part of the message.
        assertFalse(note.bodyHtml.contains("Scam warning"))
        assertFalse(note.bodyHtml.endsWith("<br>"))
    }

    @Test
    fun replyDraft() {
        val draft = note.reply!!
        assertEquals("artist2", draft.to)
        assertEquals("RE: Commission question", draft.subject)
        assertTrue(draft.message.contains("Are your commissions open"))
        assertTrue(draft.message.contains("\n"))
        assertEquals("0000000000", note.sendKey)
    }

    @Test
    fun listPageIsNoNote() {
        assertNull(NotesParser.note(Fixtures.document("header-loggedin.html"), 1))
    }
}
