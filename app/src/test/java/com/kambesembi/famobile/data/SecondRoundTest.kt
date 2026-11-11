package com.kambesembi.famobile.data

import com.kambesembi.famobile.Fixtures
import com.kambesembi.famobile.data.net.FaPage
import com.kambesembi.famobile.data.net.PageSource
import com.kambesembi.famobile.data.net.SessionEnded
import com.kambesembi.famobile.data.parse.HeaderParser
import com.kambesembi.famobile.data.upload.UploadFiles
import com.kambesembi.famobile.ui.viewer.ViewerLists
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logging out for real, files by what they are, and the small keepers of lists and pages. */
class SecondRoundTest {

    /** The logged-in page with FA's logout form put into it (hand-built: a form with a key). */
    private fun loggedInWithLogout() = Fixtures.document("header-loggedin.html").also { doc ->
        doc.body().append("""<form method="post" action="/logout/"><input type="hidden" name="key" value="k123"><button>Log Out</button></form>""")
    }

    @Test
    fun theHeaderCarriesFasLogout() {
        val logout = HeaderParser.parse(loggedInWithLogout()).logout
        assertEquals("/logout/", logout?.path)
        assertEquals(listOf("key" to "k123"), logout?.fields)
        assertEquals(true, logout?.post)
        val link = Fixtures.document("header-loggedin.html").also { it.body().append("""<a href="/logout/?key=abc">Log Out</a>""") }
        assertEquals(false, HeaderParser.parse(link).logout?.post)
        assertNull(HeaderParser.parse(Fixtures.document("header-loggedout.html")).logout)
    }

    @Test
    fun loggingOutTellsFaAndDropsWhatWasOnItsWay() = runBlocking<Unit> {
        val slow = CompletableDeferred<Unit>()
        val posted = mutableListOf<Pair<String, List<Pair<String, String>>>>()
        var cleared = false
        val source = object : PageSource {
            override suspend fun fetch(path: String): FaPage {
                if (path == "/slow/") slow.await()
                val doc = loggedInWithLogout()
                return FaPage(path, doc, HeaderParser.parse(doc))
            }
            override suspend fun post(path: String, form: List<Pair<String, String>>): FaPage {
                posted += path to form
                return fetch(path)
            }
            override fun clearSession() { cleared = true }
        }
        val fa = FaClient(source, ChallengeCoordinator())
        fa.page("/")
        val late = async { runCatching { fa.page("/slow/") } }
        kotlinx.coroutines.yield()
        fa.endSession()
        assertEquals(listOf("/logout/" to listOf("key" to "k123")), posted)
        assertTrue(cleared)
        assertNull(fa.header.value)
        slow.complete(Unit)
        assertTrue(late.await().exceptionOrNull() is SessionEnded)
        // The late answer didn't bring the old login back.
        assertNull(fa.header.value)
    }

    @Test
    fun aFileIsWhatItsFirstBytesSay() {
        fun file(vararg bytes: Int) = File.createTempFile("type", ".bin").apply {
            deleteOnExit()
            writeBytes(ByteArray(bytes.size) { bytes[it].toByte() } + ByteArray(32))
        }
        assertEquals("jpg", UploadFiles.realType(file(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals("png", UploadFiles.realType(file(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals("gif", UploadFiles.realType(file(0x47, 0x49, 0x46, 0x38, 0x39, 0x61)))
        // WebP ("RIFF....WEBP"), whatever it is called.
        assertNull(UploadFiles.realType(file(0x52, 0x49, 0x46, 0x46)))
    }

    @Test
    fun aPlaceInXmpIsFound() {
        fun file(text: String) = File.createTempFile("xmp", ".jpg").apply {
            deleteOnExit()
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + text.toByteArray(Charsets.ISO_8859_1))
        }
        assertTrue(UploadFiles.hasXmpLocation(file("""<rdf:Description exif:GPSLatitude="52,31.2N"/>""")))
        // A near miss right before the real thing must not hide it.
        assertTrue(UploadFiles.hasXmpLocation(file("exif:GPexif:GPSLongitude")))
        assertFalse(UploadFiles.hasXmpLocation(file("""<rdf:Description exif:ExposureTime="1/60"/>""")))
        assertTrue(UploadFiles.hasXmpLocation(File("/nowhere/at/all.jpg")))
    }

    @Test
    fun onlyTheMostRecentPagesAreKept() {
        var map = emptyMap<Int, String>()
        for (i in 1..5) map = map.keepRecent(i, "p$i", limit = 3)
        assertEquals(listOf(3, 4, 5), map.keys.toList())
        // Shown again: it becomes the newest, and the oldest goes next.
        map = map.keepRecent(3, "again", limit = 3).keepRecent(6, "p6", limit = 3)
        assertEquals(listOf(5, 3, 6), map.keys.toList())
        assertEquals("again", map[3])
    }

    @Test
    fun viewerListsComeBackByTicket() {
        val lists = ViewerLists()
        val ticket = lists.put(listOf(1L, 2L, 3L))
        assertEquals(listOf(1L, 2L, 3L), lists.get(ticket))
        assertNull(lists.get(ticket + 1))
        repeat(60) { lists.put(listOf(it.toLong())) }
        assertNull(lists.get(ticket))
    }
}
