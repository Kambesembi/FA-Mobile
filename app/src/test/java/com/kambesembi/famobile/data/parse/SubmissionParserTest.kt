package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.Fixtures
import com.kambesembi.famobile.data.model.Rating
import com.kambesembi.famobile.data.model.Submission
import com.kambesembi.famobile.data.model.SubmissionKind
import com.kambesembi.famobile.data.model.SubmissionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmissionParserTest {

    private fun load(name: String, id: Long): SubmissionResult =
        SubmissionParser.parse(Fixtures.document(name, "https://www.furaffinity.net/view/$id/"), id)

    private fun loaded(name: String, id: Long): Submission = (load(name, id) as SubmissionResult.Loaded).submission

    private val image = loaded("view-image-loggedin.html", 10000610)

    @Test
    fun imageAndFiles() {
        assertEquals(SubmissionKind.Image, image.kind)
        assertEquals("https://d.furaffinity.net/art/artist1/1793798280/1793798265.submission.jpg", image.imageUrl)
        assertEquals("https://t.furaffinity.net/10000610@600-1793798280.jpg", image.previewUrl)
        assertEquals(image.imageUrl, image.downloadUrl)
    }

    @Test
    fun artistTitleAndDate() {
        assertEquals("artist1", image.artist.name)
        assertEquals("https://a.furaffinity.net/1792492658/artist1.gif", image.artist.avatarUrl)
        assertEquals("artist1", image.artist.displayName)
        assertEquals("Submission 10000610", image.title)
        assertEquals(1793798265L, image.postedAt)
    }

    @Test
    fun statsRatingAndDetails() {
        assertEquals(14, image.views)
        assertEquals(1, image.commentCount)
        assertEquals(0, image.favoriteCount)
        assertEquals(Rating.General, image.rating)
        assertEquals("Category" to "Artwork (Digital)", image.info.first())
        assertTrue(image.info.any { it.first == "Resolution" && it.second == "1999 x 1843" })
        assertEquals(27, image.keywords.size)
        assertEquals("adopt", image.keywords.first())
        assertTrue(image.descriptionHtml.contains("bbcode"))
    }

    @Test
    fun favWhenLoggedIn() {
        assertEquals(false, image.fav?.faved)
        assertEquals("/fav/10000610/?key=0000000000", image.fav?.path)
    }

    @Test
    fun noFavWhenLoggedOut() = assertNull(loaded("view-image.html", 10000610).fav)

    @Test
    fun comments() {
        val comment = image.comments.single()
        assertEquals(194319437L, comment.id)
        assertEquals(0, comment.depth)
        assertEquals("artist1", comment.author?.name)
        assertEquals(1793798293L, comment.postedAt)
        assertTrue(comment.byOriginalPoster)
        assertFalse(comment.hidden)
        assertTrue(comment.html.isNotBlank())
    }

    @Test
    fun moreFromTheArtist() {
        assertEquals(listOf(10000300L, 10000050, 10000040), image.moreFromArtist.map { it.id })
        assertEquals("artist1", image.moreFromArtist.first().artist?.name)
        assertEquals("https://t.furaffinity.net/10000300@{size}-1793222909.jpg", image.moreFromArtist.first().thumbnailTemplate)
    }

    @Test
    fun story() {
        val story = loaded("view-story.html", 10000370)
        assertEquals(SubmissionKind.Text, story.kind)
        assertEquals(
            "https://d.furaffinity.net/download/art/artist1/stories/1793797267/1793797267.submission.docx",
            story.downloadUrl,
        )
        assertTrue(story.comments.isEmpty())
    }

    @Test
    fun registeredUsersOnly() {
        val result = load("view-registered-only.html", 10009990)
        assertTrue(result is SubmissionResult.Unavailable)
        assertTrue((result as SubmissionResult.Unavailable).message.contains("registered users only"))
    }
}
