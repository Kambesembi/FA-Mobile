package com.kambesembi.famobile.data

import com.kambesembi.famobile.Fixtures
import com.kambesembi.famobile.data.model.SubmissionResult
import com.kambesembi.famobile.data.net.FaPage
import com.kambesembi.famobile.data.net.PageSource
import com.kambesembi.famobile.data.parse.HeaderParser
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pages that are asked for twice, or whose screen goes away while they load. */
class LoadingTest {

    /** Serves the saved submission and search pages; a /view/ page waits for [gate] when there is one. */
    private class Source(private val gate: CompletableDeferred<Unit>? = null) : PageSource {
        val views = AtomicInteger()
        override suspend fun fetch(path: String): FaPage {
            if (path.startsWith("/view/")) {
                views.incrementAndGet()
                gate?.await()
            }
            val doc = Fixtures.document(if (path.startsWith("/search/")) "search.html" else "view-image-loggedin.html")
            return FaPage(path, doc, HeaderParser.parse(doc))
        }
        override suspend fun post(path: String, form: List<Pair<String, String>>): FaPage = fetch(path)
    }

    @Test
    fun twoWhoAskForOneSubmissionShareTheRequest() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val source = Source(gate)
        val repo = SubmissionRepository(FaClient(source, ChallengeCoordinator()))
        val ahead = async(Dispatchers.Default) { repo.load(7) }
        val swipe = async(Dispatchers.Default) { repo.load(7) }
        withTimeout(5_000) { while (source.views.get() == 0) delay(5) }
        gate.complete(Unit)
        assertTrue(ahead.await() is SubmissionResult.Loaded)
        assertTrue(swipe.await() is SubmissionResult.Loaded)
        assertEquals(1, source.views.get())
        // Asked for again on purpose (pull to refresh): a new request.
        repo.load(7, force = true)
        assertEquals(2, source.views.get())
    }

    @Test
    fun aComicKeepsLoadingWhoeverAsksAgain() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val source = Source(gate)
        val fa = FaClient(source, ChallengeCoordinator())
        val app = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val comics = ComicRepository(fa, SubmissionRepository(fa), app)
        try {
            comics.open(7)
            // The screen comes back (the phone was turned) while the first page is still on its way.
            comics.open(7)
            assertEquals(true, comics.state.value?.loading)
            gate.complete(Unit)
            val done = withTimeout(10_000) { comics.state.first { it != null && !it.loading } }
            assertEquals(7L, done?.startId)
            assertNull(done?.error)
            assertTrue(done?.pages.orEmpty().isNotEmpty())
            val requests = source.views.get()
            // All there: opening it again asks FA for nothing.
            comics.open(7)
            assertEquals(false, comics.state.value?.loading)
            assertEquals(requests, source.views.get())
            // Left and opened anew: it loads again, and finishes.
            comics.close()
            assertNull(comics.state.value)
            comics.open(7)
            withTimeout(10_000) { comics.state.first { it != null && !it.loading } }
        } finally {
            app.cancel()
        }
    }
}
