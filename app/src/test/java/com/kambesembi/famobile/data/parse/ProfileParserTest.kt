package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.Fixtures
import com.kambesembi.famobile.data.model.ProfileResult
import com.kambesembi.famobile.data.model.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileParserTest {

    private val profile = (ProfileParser.profile(Fixtures.document("user-page.html", "https://www.furaffinity.net/user/artist2/"), "artist2")
        as ProfileResult.Loaded).profile

    @Test
    fun header() {
        assertEquals("artist2", profile.user.name)
        assertEquals("Artist2", profile.user.displayName)
        assertEquals("https://a.furaffinity.net/1700000002/artist2.gif", profile.user.avatarUrl)
        assertEquals("Digital artist", profile.title)
        assertEquals(1420070400L, profile.registeredAt)
        assertEquals("/newpm/artist2/", profile.notePath)
        assertFalse(profile.watch!!.watching)
        assertEquals("/watch/artist2/?key=0000000000", profile.watch!!.path)
    }

    @Test
    fun statsAndInfo() {
        assertEquals(
            listOf("Views" to 48210, "Submissions" to 214, "Favs" to 3120, "Comments Earned" to 1876, "Comments Made" to 932, "Journals" to 41),
            profile.stats,
        )
        assertEquals("Accepting Commissions" to "Yes", profile.info[1])
        assertEquals("Character Species" to "Red fox", profile.info[2])
        assertEquals(1234, profile.watchedBy)
        assertEquals(87, profile.watching)
        assertTrue(profile.profileHtml.contains("I draw foxes"))
    }

    @Test
    fun recentAndShouts() {
        assertEquals(8, profile.recentSubmissions.size)
        assertEquals(10000610L, profile.recentSubmissions.first().id)
        assertEquals("artist2", profile.recentSubmissions.first().artist?.name)
        assertEquals(6, profile.recentFavorites.size)
        assertEquals("artist3", profile.recentFavorites.first().artist?.name)
        assertEquals(listOf(57000003L, 57000002, 57000001), profile.shouts.map { it.id })
        assertEquals("artist3", profile.shouts.first().author?.name)
        assertEquals("Love the new sketch!", profile.shouts.first().html)
    }

    @Test
    fun loggedOutHasNoWatch() {
        val doc = Fixtures.document("user-page.html")
        doc.selectFirst("a#watch-button")!!.attr("href", "/watch/artist2/?key=")
        assertNull(ProfileParser.watch(doc))
    }

    @Test
    fun watchingShowsUnwatch() {
        val doc = Fixtures.document("user-page.html")
        doc.selectFirst("a#watch-button")!!.attr("href", "/unwatch/artist2/?key=0000000000")
        assertTrue(ProfileParser.watch(doc)!!.watching)
    }

    @Test
    fun pageWithoutHeaderIsUnavailable() {
        val result = ProfileParser.profile(Fixtures.document("header-loggedin.html"), "nobody")
        assertTrue(result is ProfileResult.Unavailable)
    }

    @Test
    fun gallery() {
        val page = ProfileParser.gallery(Fixtures.document("user-gallery.html"), "artist2")
        assertEquals(24, page.items.size)
        assertEquals("/gallery/artist2/2/", page.nextPath)
        assertEquals(Rating.Mature, page.items[2].rating)
        assertEquals(160f / 200f, page.items[1].aspectRatio!!, 0.001f)
    }

    @Test
    fun favoritesNameTheirArtists() {
        val page = ProfileParser.gallery(Fixtures.document("user-favorites.html"), "artist2")
        assertEquals(24, page.items.size)
        assertEquals("/favorites/artist2/1790000000/next", page.nextPath)
        assertEquals("artist3", page.items.first().artist?.name)
    }

    @Test
    fun lastGalleryPageHasNoNext() {
        val doc = Fixtures.document("user-gallery.html")
        doc.select("form[action*=/gallery/]").remove()
        assertNull(ProfileParser.gallery(doc, "artist2").nextPath)
    }

    @Test
    fun journals() {
        val page = ProfileParser.journals(Fixtures.document("user-journals.html"))
        assertEquals(listOf(11000003L, 11000002, 11000001), page.items.map { it.id })
        val first = page.items.first()
        assertEquals("Commissions open!", first.title)
        assertEquals(1793750000L, first.postedAt)
        assertEquals(12, first.commentCount)
        assertTrue(first.bodyHtml.contains("Five slots"))
        assertEquals(Rating.Mature, page.items[2].rating)
        assertEquals("/journals/artist2/2/", page.nextPath)
    }

    @Test
    fun bannerIsTheUsersOwnOnly() {
        val own = org.jsoup.Jsoup.parse(
            """<site-banner><a href="/user/artist2/"><picture>
              <source media="(max-width: 799px)" srcset="//d.furaffinity.net/art/artist2/1/profile_banner_mobile.jpg">
              <source media="(min-width: 800px)" srcset="//d.furaffinity.net/art/artist2/1/profile_banner.jpg">
              <img src="//d.furaffinity.net/art/artist2/1/profile_banner.jpg" alt="Profile Banner image"></picture></a></site-banner>""",
        )
        assertEquals("https://d.furaffinity.net/art/artist2/1/profile_banner.jpg", ProfileParser.banner(own))
        // FA's own seasonal banner isn't the user's.
        val site = org.jsoup.Jsoup.parse("""<site-banner><picture><img src="//d.furaffinity.net/media/banners/modern/x.jpg"></picture></site-banner>""")
        assertNull(ProfileParser.banner(site))
        assertNull(profile.banner)
    }
}
