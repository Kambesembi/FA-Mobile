package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.Fixtures
import com.kambesembi.famobile.data.model.JournalResult
import com.kambesembi.famobile.data.model.ProfileResult
import com.kambesembi.famobile.data.model.Rating
import com.kambesembi.famobile.data.model.SubmissionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The forms for comments and shouts, and the journal page (Iteration 6). */
class WritingParserTest {

    private fun submission(name: String) =
        (SubmissionParser.parse(Fixtures.document(name, "https://www.furaffinity.net/view/10000610/"), 10000610) as SubmissionResult.Loaded).submission

    private val journal = (JournalParser.parse(Fixtures.document("journal.html", "https://www.furaffinity.net/journal/11000003/"), 11000003)
        as JournalResult.Loaded).journal

    @Test
    fun commentFormOnSubmission() {
        val form = submission("view-image-loggedin.html").commentForm!!
        assertEquals("/view/10000610/", form.path)
        assertEquals("reply", form.textField)
        assertEquals(listOf("f" to "0", "action" to "reply", "replyto" to "", "submit" to "Post Comment"), form.fields)
        assertTrue(form.canReply)
        assertNull(form.maxLength)
    }

    @Test
    fun noCommentFormLoggedOut() {
        assertNull(submission("view-image.html").commentForm)
    }

    @Test
    fun newCommentAndReply() {
        val form = submission("view-image-loggedin.html").commentForm!!
        assertEquals(
            listOf("f" to "0", "action" to "reply", "replyto" to "", "submit" to "Post Comment", "reply" to "Nice!"),
            form.submit("Nice!"),
        )
        // A reply does what FA's script does: action=replyto and the comment's id.
        assertEquals(
            listOf("f" to "0", "action" to "replyto", "replyto" to "194319437", "submit" to "Post Comment", "reply" to "Thanks"),
            form.submit("Thanks", replyTo = 194319437),
        )
    }

    @Test
    fun aspectRatioFromResolution() {
        assertEquals(1999f / 1843f, submission("view-image-loggedin.html").aspectRatio!!, 0.0001f)
    }

    @Test
    fun shoutBox() {
        val profile = (ProfileParser.profile(Fixtures.document("user-page.html", "https://www.furaffinity.net/user/artist2/"), "artist2")
            as ProfileResult.Loaded).profile
        val form = profile.shoutForm!!
        assertEquals("/user/artist2/", form.path)
        assertEquals("shout", form.textField)
        assertEquals(255, form.maxLength)
        assertFalse(form.canReply)
        assertEquals(
            listOf("action" to "shout", "key" to "0000000000", "name" to "artist2", "submit" to "Submit", "shout" to "Hi!"),
            form.submit("Hi!"),
        )
    }

    @Test
    fun journalPage() {
        assertEquals("Commissions open!", journal.title)
        assertEquals("artist2", journal.author.name)
        assertEquals("Artist2", journal.author.displayName)
        assertEquals(Rating.General, journal.rating)
        assertEquals(1793750000L, journal.postedAt)
        assertTrue(journal.bodyHtml.contains("Five slots are open"))
        assertEquals(listOf(31000001L, 31000002L, 31000003L), journal.comments.map { it.id })
        assertEquals(listOf(0, 1, 0), journal.comments.map { it.depth })
        assertTrue(journal.comments[1].byOriginalPoster)
        assertEquals("/journal/11000003/", journal.commentForm?.path)
    }

    @Test
    fun notAJournal() {
        val result = JournalParser.parse(Fixtures.document("header-loggedin.html", "https://www.furaffinity.net/journal/1/"), 1)
        assertTrue(result is JournalResult.Unavailable)
    }

    @Test
    fun journalLinks() {
        assertEquals(FaLink.Journal(11000003), FaLink.of("https://www.furaffinity.net/journal/11000003/"))
        assertEquals(FaLink.Journal(5), FaLink.of("/journal/5/"))
    }
}
