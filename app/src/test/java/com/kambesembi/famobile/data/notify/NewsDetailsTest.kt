package com.kambesembi.famobile.data.notify

import com.kambesembi.famobile.Fixtures
import com.kambesembi.famobile.data.parse.ActivityParser
import com.kambesembi.famobile.data.parse.InboxParser
import com.kambesembi.famobile.data.parse.NotesParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsDetailsTest {

    private val sections = ActivityParser.parse(Fixtures.document("msg-others.html"))

    @Test
    fun newWatcherIsNamed() {
        val lines = NewsDetails.fromActivity(Category.Watches, sections, 1)
        assertEquals(1, lines.size)
        assertEquals(LineKind.Watch, lines.single().kind)
        assertEquals("artist2", lines.single().actor)
        // The URL name too, for the notification's avatar.
        assertEquals("artist2", lines.single().user)
        assertEquals("artist2", NotificationAvatars.userOf(News(Category.Watches, 1, 1, lines)))
    }

    @Test
    fun noSingleAvatarForSeveralPeople() {
        val lines = listOf(NewsLine(LineKind.Watch, "A", null, "a"), NewsLine(LineKind.Watch, "B", null, "b"))
        assertEquals(null, NotificationAvatars.userOf(News(Category.Watches, 2, 2, lines)))
    }

    @Test
    fun onlyTheNewestAndOnlyThatCategory() {
        val all = NewsDetails.fromActivity(Category.Favorites, sections, 50)
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.kind == LineKind.Favorite })
        assertEquals(all.take(1), NewsDetails.fromActivity(Category.Favorites, sections, 1))
    }

    @Test
    fun commentsIncludeShouts() {
        val kinds = NewsDetails.fromActivity(Category.Comments, sections, 50).map { it.kind }.toSet()
        assertTrue(kinds.all { it in setOf(LineKind.SubmissionComment, LineKind.JournalComment, LineKind.Shout) })
    }

    @Test
    fun unreadNotes() {
        val notes = NotesParser.list(Fixtures.document("msg-pms.html")).notes
        val lines = NewsDetails.fromNotes(notes, 5)
        assertEquals(listOf("Commission question", "RE: Art trade?"), lines.map { it.subject })
        assertEquals("Artist2", lines.first().actor)
    }

    @Test
    fun newSubmissions() {
        val items = InboxParser.parse(Fixtures.document("msg-submissions.html")).items
        val lines = NewsDetails.fromInbox(items, 2)
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.kind == LineKind.Submission && it.subject != null })
    }
}
