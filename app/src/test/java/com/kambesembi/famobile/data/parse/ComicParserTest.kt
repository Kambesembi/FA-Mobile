package com.kambesembi.famobile.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicParserTest {

    @Test
    fun seriesLinksAreToldApartByTheirText() {
        val html = """
            A new page!<br>
            <a href="/view/100/">&lt;&lt; First</a> |
            <a href="https://www.furaffinity.net/view/108/">« Previous</a> |
            <a href="/view/110/" title="next">Next page »</a> |
            <a href="/full/120/">Latest</a><br>
            <a href="/user/artist1/">artist1</a> <a href="/view/999/">my other comic</a>
        """
        val links = ComicParser.links(html)
        assertEquals(100L, links.first)
        assertEquals(108L, links.prev)
        assertEquals(110L, links.next)
        assertEquals(120L, links.last)
    }

    @Test
    fun arrowsAloneCount() {
        val links = ComicParser.links("""<a href="/view/5/">&lt;</a> <a href="/view/7/">&gt;</a> <a href="/view/8/">→</a>""")
        assertEquals(5L, links.prev)
        assertEquals(7L, links.next)
        assertNull(links.first)
    }

    @Test
    fun theFirstLinkOfAKindWins() {
        val links = ComicParser.links("""<a href="/view/2/">Next</a> <a href="/view/3/">next</a>""")
        assertEquals(2L, links.next)
    }

    @Test
    fun noSeriesLinks() {
        assertTrue(ComicParser.links("").isEmpty)
        assertTrue(ComicParser.links("""Thanks! <a href="/user/artist2/">artist2</a> <a href="/view/9/">the sketch</a>""").isEmpty)
    }

    @Test
    fun numberedTitles() {
        assertEquals(NumberedTitle("Aquarium Encounter", 9), ComicParser.numberedTitle("Aquarium Encounter - Page 9"))
        assertEquals(NumberedTitle("Night Shift", 3), ComicParser.numberedTitle("Night Shift 3/10"))
        assertEquals(NumberedTitle("Night Shift", 3), ComicParser.numberedTitle("Night Shift part 3 of 10"))
        assertEquals(NumberedTitle("Pinup", 4), ComicParser.numberedTitle("Pinup #4"))
        assertEquals(NumberedTitle("Road Trip", 5), ComicParser.numberedTitle("Road Trip: Ep.5"))
        assertEquals(NumberedTitle("Road Trip", 12), ComicParser.numberedTitle("Road Trip (pg 12)"))
    }

    @Test
    fun yearsAndBareNumbersAreNotPages() {
        assertNull(ComicParser.numberedTitle("Summer 2023"))
        assertNull(ComicParser.numberedTitle("Page 12"))
        assertNull(ComicParser.numberedTitle("Aquarium Encounter"))
        assertNull(ComicParser.numberedTitle("Page 2 of the story, continued"))
    }

    @Test
    fun seriesFromThePageAlone() {
        assertTrue(ComicParser.isSeries("Night Shift 3", ""))
        assertTrue(ComicParser.isSeries("A title", """<a href="/view/2/">Next »</a>"""))
        assertFalse(ComicParser.isSeries("A title", "Just a picture."))
    }

    @Test
    fun searchForTheSameTitleByTheArtist() {
        assertEquals("aquarium encounter", ComicParser.normalize("Aquarium  Encounter!"))
        assertEquals(
            "@title \"Aquarium Encounter Part\" @lower artist1",
            ComicParser.searchText("Aquarium (Encounter) - Part", "artist1"),
        )
    }
}
