package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.Fixtures
import com.kambesembi.famobile.data.model.BrowseFilters
import com.kambesembi.famobile.data.model.Rating
import com.kambesembi.famobile.data.model.SearchQuery
import com.kambesembi.famobile.data.model.SearchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreParserTest {

    private val browse = ExploreParser.browse(Fixtures.document("browse.html", "https://www.furaffinity.net/browse/"))
    private val search = ExploreParser.search(Fixtures.document("search.html", "https://www.furaffinity.net/search/?q=fox"))

    @Test
    fun browseGrid() {
        assertEquals(24, browse.items.size)
        assertEquals(10001090L, browse.items.first().id)
        assertEquals("artist3", browse.items.first().artist?.name)
        assertTrue(browse.hasNext)
    }

    @Test
    fun browseOptionsComeFromThePage() {
        val options = browse.options
        assertEquals(FilterCheck("1", "All", "Visual Art"), options.categories.first().let { FilterCheck(it.value, it.label, it.group) })
        assertTrue(options.categories.any { it.label == "Story" && it.group == "Readable Art" })
        assertTrue(options.types.any { it.value == "7" && it.label == "Fanart" })
        assertTrue(options.species.size > 300)
        assertEquals("", options.genders.first().value)
        assertEquals(setOf(Rating.General, Rating.Mature), options.ratings)
        assertEquals(setOf(Rating.General, Rating.Mature), options.checkedRatings)
    }

    @Test
    fun lastBrowsePageHasNoNext() {
        val doc = Fixtures.document("browse.html")
        doc.select("button").filter { it.text() == "Next" }.forEach { it.attr("disabled", "") }
        assertFalse(ExploreParser.browse(doc).hasNext)
    }

    @Test
    fun searchResults() {
        assertEquals(24, search.items.size)
        assertEquals(1234, search.total)
        assertTrue(search.hasNext)
        assertTrue(search.options.categories.isNotEmpty())
        assertTrue(search.options.types.isNotEmpty())
        assertEquals(setOf(Rating.General, Rating.Mature), search.options.ratings)
    }

    @Test
    fun browsePath() {
        val path = BrowseFilters(category = "2", ratings = setOf(Rating.General, Rating.Mature)).path(page = 3, perPage = 72)
        assertEquals("/browse/?cat=2&atype=1&species=1&rating_general=1&rating_mature=1&rating_adult=0&perpage=72&page=3&go=Apply", path)
    }

    @Test
    fun searchPath() {
        val path = SearchQuery("red fox", types = setOf(SearchType.Art)).path(page = 2, perPage = 48)
        assertTrue(path, path.startsWith("/search/?q=red+fox&order-by=relevancy&order-direction=desc&range=all&mode=extended"))
        assertTrue(path.contains("&rating-general=1"))
        assertFalse(path.contains("rating-mature"))
        assertTrue(path.contains("&type-art=1"))
        assertFalse(path.contains("type-music"))
        assertTrue(path.endsWith("&perpage=48&page=2"))
    }

    private data class FilterCheck(val value: String, val label: String, val group: String?)
}
