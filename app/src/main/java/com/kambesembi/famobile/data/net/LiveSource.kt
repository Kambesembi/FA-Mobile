package com.kambesembi.famobile.data.net

import android.os.SystemClock
import com.kambesembi.famobile.data.FaUrls
import com.kambesembi.famobile.data.parse.Challenge
import com.kambesembi.famobile.data.parse.HeaderParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

/** Pages over the network, one at a time and spaced out, so the app never floods FA. */
class LiveSource(userAgent: String) : PageSource {

    val cookies = WebViewCookieJar()

    /** Shared with the image loader: one connection pool, so a Cloudflare repair covers images too. */
    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookies)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Big files on slow connections: more patience while sending, and while FA processes them. */
    private val uploadClient: OkHttpClient by lazy {
        client.newBuilder().writeTimeout(2, TimeUnit.MINUTES).readTimeout(3, TimeUnit.MINUTES).build()
    }

    private val gate = Mutex()
    private var lastRequestAt = 0L

    override suspend fun fetch(path: String): FaPage = gated {
        load(Request.Builder().url(FaUrls.absolute(path)), patient = true)
    }

    override suspend fun post(path: String, form: List<Pair<String, String>>): FaPage = gated {
        val body = FormBody.Builder().apply { form.forEach { (name, value) -> add(name, value) } }.build()
        val url = FaUrls.absolute(path)
        // FA checks where a form came from, as a browser would send it.
        load(Request.Builder().url(url).post(body).header("Origin", FaUrls.BASE).header("Referer", url))
    }

    override suspend fun postMultipart(
        path: String,
        form: List<Pair<String, String>>,
        files: List<FormFile>,
        progress: SendProgress,
    ): FaPage {
        // It waits its turn like any page, but the pages after it don't wait while the file travels.
        gate.withLock {
            val wait = MIN_GAP_MS - (SystemClock.elapsedRealtime() - lastRequestAt)
            if (wait > 0) delay(wait)
            lastRequestAt = SystemClock.elapsedRealtime()
        }
        val url = FaUrls.absolute(path)
        val body = CountingBody(multipartBody(form, files), progress)
        return load(Request.Builder().url(url).post(body).header("Origin", FaUrls.BASE).header("Referer", url), uploadClient)
    }

    private suspend fun gated(block: suspend () -> FaPage): FaPage = gate.withLock {
        val wait = MIN_GAP_MS - (SystemClock.elapsedRealtime() - lastRequestAt)
        if (wait > 0) delay(wait)
        try {
            block()
        } finally {
            lastRequestAt = SystemClock.elapsedRealtime()
        }
    }

    /** What came back, read to the end on OkHttp's thread. */
    private class Answer(val code: Int, val mitigated: String?, val retryAfter: String?, val body: String, val url: String)

    /**
     * Runs the call without holding a thread, and so that giving up on it (the screen is gone, the
     * work was stopped) really ends the request instead of letting it run to its timeout.
     */
    private suspend fun Call.answer(): Answer = suspendCancellableCoroutine { waiting ->
        waiting.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (waiting.isActive) waiting.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        Answer(it.code, it.header("cf-mitigated"), it.header("Retry-After"), it.body?.string().orEmpty(), it.request.url.toString())
                    }
                }
                if (waiting.isActive) waiting.resumeWith(result)
            }
        })
    }

    /**
     * [patient]: when FA answers "too many requests" or "busy" (429, 503), wait as long as it says (or
     * a few seconds) and ask again, twice at most. Only for reading pages: a form is never sent twice.
     */
    private suspend fun load(builder: Request.Builder, http: OkHttpClient = client, patient: Boolean = false): FaPage {
        val request = builder.header("Accept", "text/html,application/xhtml+xml").build()
        val url = request.url.toString()
        var waits = 0
        while (true) {
            val answer = http.newCall(request).answer()
            if (Challenge.isChallenge(answer.code, answer.mitigated, answer.body)) throw ChallengeRequired(url)
            if (patient && (answer.code == 429 || answer.code == 503) && waits < BUSY_WAITS.size) {
                val asked = answer.retryAfter?.trim()?.toLongOrNull()?.times(1000)
                delay((asked ?: BUSY_WAITS[waits]).coerceIn(1_000, MAX_BUSY_WAIT_MS))
                waits++
                continue
            }
            if (answer.code !in 200..299) throw FaHttpException(answer.code, url)
            // Parsing a large page takes a moment: off the caller's thread.
            return withContext(Dispatchers.Default) {
                val doc = Jsoup.parse(answer.body, answer.url)
                FaPage(answer.url, doc, HeaderParser.parse(doc), answer.body)
            }
        }
    }

    override fun onChallengeResolved() {
        // Connections opened before the check carry the old clearance; start over with fresh ones.
        client.connectionPool.evictAll()
        cookies.flush()
    }

    override fun setSfw(on: Boolean) = cookies.setSfw(on)

    override fun clearSession() {
        cookies.clear()
        client.connectionPool.evictAll()
    }

    private companion object {
        const val MIN_GAP_MS = 350L
        val BUSY_WAITS = longArrayOf(3_000, 8_000)
        const val MAX_BUSY_WAIT_MS = 20_000L
    }
}
