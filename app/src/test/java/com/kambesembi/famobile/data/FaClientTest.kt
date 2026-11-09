package com.kambesembi.famobile.data

import com.kambesembi.famobile.Fixtures
import com.kambesembi.famobile.data.net.ChallengeCancelled
import com.kambesembi.famobile.data.net.ChallengeRequired
import com.kambesembi.famobile.data.net.FaPage
import com.kambesembi.famobile.data.net.PageSource
import com.kambesembi.famobile.data.parse.HeaderParser
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaClientTest {

    /** Throws a challenge for the first [challenges] fetches, then serves the logged-in page. */
    private class FakeSource(var challenges: Int) : PageSource {
        var fetches = 0
        var resolvedCalls = 0
        override suspend fun fetch(path: String): FaPage {
            fetches++
            if (challenges-- > 0) throw ChallengeRequired(path)
            val doc = Fixtures.document("header-loggedin.html")
            return FaPage(path, doc, HeaderParser.parse(doc))
        }
        override suspend fun post(path: String, form: List<Pair<String, String>>): FaPage = fetch(path)
        override fun onChallengeResolved() { resolvedCalls++ }
    }

    @Test
    fun pageUpdatesTheHeader() = runTest {
        val fa = FaClient(FakeSource(0), ChallengeCoordinator())
        fa.page("/")
        assertEquals("testuser", fa.header.value?.user?.username)
    }

    @Test
    fun aBareErrorPageKeepsTheLogin() = runTest {
        var page = "header-loggedin.html"
        val source = object : PageSource {
            override suspend fun fetch(path: String): FaPage {
                val doc = Fixtures.document(page)
                return FaPage(path, doc, HeaderParser.parse(doc))
            }
            override suspend fun post(path: String, form: List<Pair<String, String>>): FaPage = fetch(path)
        }
        val fa = FaClient(source, ChallengeCoordinator())
        fa.page("/")
        page = "system-error.html"
        fa.page("/view/5/")
        assertEquals("testuser", fa.header.value?.user?.username)
        page = "header-loggedout.html"
        fa.page("/")
        assertEquals(null, fa.header.value?.user)
    }

    @Test
    fun challengeIsShownThenRetriedOnce() = runTest {
        val source = FakeSource(1)
        val coordinator = ChallengeCoordinator()
        val fa = FaClient(source, coordinator)
        val load = async { fa.page("/") }
        while (coordinator.pendingUrl.value == null) yield()
        coordinator.resolve(true)
        load.await()
        assertEquals(2, source.fetches)
        assertEquals(1, source.resolvedCalls)
        assertEquals(null, coordinator.pendingUrl.value)
    }

    @Test
    fun cancelledChallengeFails() = runTest {
        val coordinator = ChallengeCoordinator()
        val fa = FaClient(FakeSource(1), coordinator)
        val load = async { runCatching { fa.page("/") } }
        while (coordinator.pendingUrl.value == null) yield()
        coordinator.resolve(false)
        assertTrue(load.await().exceptionOrNull() is ChallengeCancelled)
    }

    @Test
    fun bareErrorPageIsAnErrorForTheLoginCheck() = runTest {
        val doc = org.jsoup.Jsoup.parse(
            """<html><head><title>Database Error -- Fur Affinity [dot] net</title></head><body>
            <h2>Database Error</h2><p>The page you are requesting has encountered a database error. Please retry your request.</p>
            </body></html>""",
        )
        val source = object : PageSource {
            override suspend fun fetch(path: String) = FaPage(path, doc, HeaderParser.parse(doc))
            override suspend fun post(path: String, form: List<Pair<String, String>>) = fetch(path)
        }
        val client = FaClient(source, ChallengeCoordinator())
        val error = runCatching { client.headerPage("/controls/settings/") }.exceptionOrNull()
        assertTrue(error is java.io.IOException)
        assertTrue(error!!.message!!, error.message!!.startsWith("Database Error: The page you are requesting"))
        assertEquals(null, client.header.value)
    }
}
