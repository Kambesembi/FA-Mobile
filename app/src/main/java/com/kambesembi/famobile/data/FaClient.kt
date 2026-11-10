package com.kambesembi.famobile.data

import com.kambesembi.famobile.data.net.ChallengeCancelled
import com.kambesembi.famobile.data.net.ChallengeRequired
import com.kambesembi.famobile.data.net.FaPage
import com.kambesembi.famobile.data.net.FormFile
import com.kambesembi.famobile.data.net.PageSource
import com.kambesembi.famobile.data.parse.PageHeader
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document

/**
 * The one door to FA pages for every screen. Each page's header updates the unread counts, and a
 * Cloudflare check is shown to the user and then retried exactly once.
 */
class FaClient(
    private val source: PageSource,
    private val challenge: ChallengeCoordinator,
) {
    private val _header = MutableStateFlow<PageHeader?>(null)

    /** The header of the latest page, so the badges follow whatever was loaded last. */
    val header: StateFlow<PageHeader?> = _header.asStateFlow()

    suspend fun page(path: String): FaPage = load { source.fetch(path) }

    /**
     * A page, read by [parse] off the main thread. If FA changed the page so it can't be read, that
     * becomes an [IOException] like a network error: a message on screen, not a crash.
     */
    suspend fun <T> read(path: String, parse: (Document) -> T): T {
        val page = page(path)
        if (!page.header.present && page.document.title().contains("Error", ignoreCase = true)) throw IOException(bareError(page.document))
        return withContext(Dispatchers.Default) {
            try {
                parse(page.document)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                throw IOException("Fur Affinity's page couldn't be read (${e.javaClass.simpleName})", e)
            }
        }
    }

    suspend fun post(path: String, form: List<Pair<String, String>>): FaPage = load { source.post(path, form) }

    /** A form without files sent as `multipart/form-data`, for forms that FA marks that way. */
    suspend fun postMultipart(path: String, form: List<Pair<String, String>>, files: List<FormFile>): FaPage =
        load { source.postMultipart(path, form, files) { _, _ -> } }

    private suspend fun load(request: suspend () -> FaPage): FaPage {
        val page = try {
            request()
        } catch (e: ChallengeRequired) {
            // FA's front page: the clearance counts for the whole site, and loading the address that failed
            // would carry out a fav or watch link a second time.
            if (!challenge.await(FaUrls.BASE + "/")) throw ChallengeCancelled()
            source.onChallengeResolved()
            request()
        }
        // A page without FA's header (a bare error page) leaves the login and counts as they were.
        if (page.header.present) _header.value = page.header
        return page
    }

    /** The page that checks the login: FA's bare error pages ("Database Error") become an [IOException]. */
    suspend fun headerPage(path: String): FaPage {
        val page = page(path)
        if (!page.header.present) throw IOException(bareError(page.document))
        return page
    }

    fun setSfw(on: Boolean) = source.setSfw(on)

    fun clearSession() {
        source.clearSession()
        _header.value = null
    }
}

/** What FA says on a page without its usual layout, e.g. "Database Error: … Please retry your request." */
internal fun bareError(doc: Document): String {
    val title = doc.title().substringBefore(" -- ").trim()
    val text = doc.select("p, td, div").asSequence().map { it.ownText().trim() }.firstOrNull { it.length > 20 }.orEmpty()
    return listOf(title.ifEmpty { "Fur Affinity sent an unexpected page" }, text.take(200)).filter { it.isNotEmpty() }.joinToString(": ")
}
