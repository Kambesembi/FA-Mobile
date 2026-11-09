package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.Fixtures
import com.kambesembi.famobile.data.model.Rating
import com.kambesembi.famobile.data.model.SubmissionKind
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InboxParserTest {

    private val page = InboxParser.parse(Fixtures.document("msg-submissions.html", "https://www.furaffinity.net/msg/submissions/"))

    @Test
    fun allSubmissionsInPageOrder() {
        assertEquals(
            listOf(10000610L, 10000360, 10000350, 10000340, 10000330, 10000320, 10000310),
            page.items.map { it.preview.id },
        )
    }

    @Test
    fun firstSubmission() {
        val first = page.items.first()
        assertEquals("Today, 4 November 2026", first.day)
        with(first.preview) {
            assertEquals("Submission 10000610", title)
            assertEquals("artist5", artist?.name)
            assertEquals("https://t.furaffinity.net/10000610@{size}-1793798280.jpg", thumbnailTemplate)
            assertEquals("https://t.furaffinity.net/10000610@600-1793798280.jpg", thumbnail(600))
            assertEquals(271.161f / 250f, aspectRatio!!, 0.001f)
            assertEquals(Rating.General, rating)
            assertEquals(SubmissionKind.Image, kind)
            assertEquals(listOf("u_artist", "c_artwork_digital", "t_all", "s_unspecified_any", "adopt"), tags?.take(5))
        }
    }

    @Test
    fun ratings() {
        assertEquals(
            listOf(Rating.General, Rating.General, Rating.General, Rating.Adult, Rating.General, Rating.Adult, Rating.Mature),
            page.items.map { it.preview.rating },
        )
    }

    @Test
    fun singlePageHasNoNext() = assertNull(page.nextPath)

    @Test
    fun nextButNotPrev() {
        val doc = Jsoup.parse(
            """
            <form id="messages-form"><section>
              <h4 class="date-divider">Yesterday</h4>
              <figure id="sid-5" class="r-mature t-text"><b><u><a href="/view/5/"><img src="//t.furaffinity.net/5@300-1.jpg"></a></u></b>
                <figcaption><label><p><a href="/view/5/" title="Five">Five</a></p><p><i>by</i> <a href="/user/x/" title="X">X</a></p></label></figcaption>
              </figure>
            </section>
            <div class="messagecenter-navigation">
              <a class="button standard more-half prev" href="/msg/submissions/new~9@72/">Prev. 72</a>
              <a class="button standard more-half" href="/msg/submissions/new~4@72/">Next 72</a>
            </div></form>
            """,
            "https://www.furaffinity.net/msg/submissions/",
        )
        val parsed = InboxParser.parse(doc)
        assertEquals("/msg/submissions/new~4@72/", parsed.nextPath)
        assertEquals("Yesterday", parsed.items.single().day)
        assertEquals(SubmissionKind.Text, parsed.items.single().preview.kind)
        assertNull(parsed.items.single().preview.aspectRatio)
    }

    @Test
    fun headerCountsStillReadFromTheSamePage() {
        val header = HeaderParser.parse(Fixtures.document("msg-submissions.html"))
        assertEquals("testuser", header.user?.username)
        assertEquals(7, header.counts.submissions)
    }
}
