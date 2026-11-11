package com.kambesembi.famobile.data.net

import com.kambesembi.famobile.data.parse.PageHeader
import java.io.IOException
import org.jsoup.nodes.Document

/** A parsed FA page. */
class FaPage(
    val url: String,
    val document: Document,
    val header: PageHeader,
    /** The answer as FA sent it, for the few that aren't pages (JSON); null where only the page is kept. */
    val raw: String? = null,
)

/** Where FA pages come from: the network, or saved pages in demo mode. */
interface PageSource {
    suspend fun fetch(path: String): FaPage

    /** Submits a form (FA answers with the resulting page). Repeated names, like submissions[], are allowed. */
    suspend fun post(path: String, form: List<Pair<String, String>>): FaPage

    /**
     * Sends a form with files (`multipart/form-data`), reporting the bytes as they go out. Only the
     * network and the demo can do this.
     */
    suspend fun postMultipart(
        path: String,
        form: List<Pair<String, String>>,
        files: List<FormFile>,
        progress: SendProgress,
    ): FaPage = throw IOException("Uploads aren't possible here")

    /** Called after the user got through a Cloudflare check, before the one retry. */
    fun onChallengeResolved() {}

    /** Forget the session on this phone. */
    fun clearSession() {}

    /** Sets FA's own SFW mode for this app's session. */
    fun setSfw(on: Boolean) {}
}

/** Cloudflare answered with its check instead of the page. */
class ChallengeRequired(val url: String) : IOException("Cloudflare check required for $url")

/** The user closed the Cloudflare check. */
class ChallengeCancelled : IOException("Cloudflare check cancelled")

/** The login ended (logout) while this request was on its way: its answer belongs to nobody. */
class SessionEnded : IOException("Logged out")

class FaHttpException(val code: Int, url: String) : IOException("HTTP $code for $url")
