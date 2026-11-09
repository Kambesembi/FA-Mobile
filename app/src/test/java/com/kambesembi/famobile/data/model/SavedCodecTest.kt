package com.kambesembi.famobile.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedCodecTest {

    @Test
    fun artistsRoundTrip() {
        val artists = listOf(
            SavedArtist("artist2", "Artist\tTwo", "https://a.furaffinity.net/1700000002/artist2.gif"),
            SavedArtist("artist3", "Artist3", null),
        )
        val decoded = SavedCodec.decodeArtists(SavedCodec.encodeArtists(artists))
        assertEquals(listOf("artist2", "artist3"), decoded.map { it.name })
        assertEquals("Artist Two", decoded[0].displayName)
        assertEquals(null, decoded[1].avatarUrl)
    }

    @Test
    fun submissionsRoundTrip() {
        val saved = listOf(SavedSubmission(10000610, "A\ntitle", "https://t.furaffinity.net/10000610@{size}-1.jpg", Rating.Mature))
        val decoded = SavedCodec.decodeSubmissions(SavedCodec.encodeSubmissions(saved))
        assertEquals(1, decoded.size)
        assertEquals("A title", decoded[0].title)
        assertEquals(Rating.Mature, decoded[0].rating)
        assertEquals("https://t.furaffinity.net/10000610@300-1.jpg", decoded[0].preview().thumbnail(300))
    }

    @Test
    fun emptyAndBrokenInput() {
        assertTrue(SavedCodec.decodeArtists(null).isEmpty())
        assertTrue(SavedCodec.decodeSubmissions("not-a-number\tx").isEmpty())
    }
}
