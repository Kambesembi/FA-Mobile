package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.Fixtures
import com.kambesembi.famobile.data.model.ActivityKind
import com.kambesembi.famobile.data.model.FormField
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityParserTest {

    private val sections = ActivityParser.parse(Fixtures.document("msg-others.html", "https://www.furaffinity.net/msg/others/"))

    @Test
    fun sectionsInPageOrder() {
        assertEquals(
            listOf(
                ActivityKind.Watches,
                ActivityKind.SubmissionComments,
                ActivityKind.JournalComments,
                ActivityKind.Favorites,
                ActivityKind.Journals,
            ),
            sections.map { it.kind },
        )
        assertEquals(listOf(2, 1, 1, 2, 1), sections.map { it.items.size })
    }

    /** The one kind checked against a live page so far. */
    @Test
    fun journal() {
        val journals = sections.single { it.kind == ActivityKind.Journals }
        val item = journals.items.single()
        assertEquals(FormField("journals[]", "11451636"), item.checkbox)
        assertEquals("artist8", item.actor?.name)
        // FA shows no avatar for journals; the one under the name stands in.
        assertEquals("https://a.furaffinity.net/artist8.gif", item.actor?.avatarUrl)
        assertEquals("Journal title", item.title)
        assertEquals("https://www.furaffinity.net/journal/11451636/", item.link)
        assertEquals(1793780000L, item.postedAt)
        assertEquals(FormField("remove-journals", "Remove Selected Journals"), journals.remove)
        assertEquals(FormField("nuke-journals", "Nuke Journals"), journals.nuke)
        assertEquals("Journals", journals.title)
    }

    @Test
    fun watchUsesTheNameLinkAndAvatar() {
        val watch = sections.first().items.first()
        assertEquals("artist2", watch.actor?.name)
        assertEquals("artist2", watch.actor?.displayName)
        assertEquals("https://a.furaffinity.net/1700000000/artist2.gif", watch.actor?.avatarUrl)
    }

    @Test
    fun commentAndFavoriteLinkTheirSubmission() {
        val comment = sections.single { it.kind == ActivityKind.SubmissionComments }.items.single()
        assertEquals("https://www.furaffinity.net/view/10000610/#cid:194319437", comment.link)
        assertEquals(FormField("comments-submissions[]", "194319437"), comment.checkbox)
        val fav = sections.single { it.kind == ActivityKind.Favorites }.items.first()
        assertEquals("Submission 10000350", fav.title)
    }

    @Test
    fun unknownSectionStillListsWithItsOwnFields() {
        val doc = Jsoup.parse(
            """
            <form action="/msg/others/"><section id="messages-something-new"><div class="section-header">
            <div class="highlight">Something New</div><button type="submit" name="remove-new" value="Remove">x</button></div>
            <ul><li><input type="checkbox" name="new[]" value="7"> artist1 did a thing</li></ul></section></form>
            """.trimIndent(),
            "https://www.furaffinity.net/msg/others/",
        )
        val section = ActivityParser.parse(doc).single()
        assertEquals(ActivityKind.Other, section.kind)
        assertEquals("Something New", section.title)
        assertEquals(FormField("new[]", "7"), section.items.single().checkbox)
        assertTrue(section.items.single().text.contains("did a thing"))
    }

    @Test
    fun noFormNoSections() {
        assertTrue(ActivityParser.parse(Fixtures.document("header-loggedin.html")).isEmpty())
    }
}
