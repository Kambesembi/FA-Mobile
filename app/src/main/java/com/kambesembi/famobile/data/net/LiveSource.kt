package com.kambesembi.famobile.data.net

import android.os.SystemClock
import com.kambesembi.famobile.data.FaUrls
import com.kambesembi.famobile.data.parse.Challenge
import com.kambesembi.famobile.data.parse.HeaderParser
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
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
        load(Request.Builder().url(FaUrls.absolute(path)))
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
    ): FaPage = withContext(Dispatchers.IO) {
        // It waits its turn like any page, but the pages after it don't wait while the file travels.
        gate.withLock {
            val wait = MIN_GAP_MS - (SystemClock.elapsedRealtime() - lastRequestAt)
            if (wait > 0) delay(wait)
            lastRequestAt = SystemClock.elapsedRealtime()
        }
        val url = FaUrls.absolute(path)
        val body = CountingBody(multipartBody(form, files), progress)
        load(Request.Builder().url(url).post(body).header("Origin", FaUrls.BASE).header("Referer", url), uploadClient)
    }

    private suspend fun gated(block: () -> FaPage): FaPage = withContext(Dispatchers.IO) {
        gate.withLock {
            val wait = MIN_GAP_MS - (SystemClock.elapsedRealtime() - lastRequestAt)
            if (wait > 0) delay(wait)
            try {
                block()
            } finally {
                lastRequestAt = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun load(builder: Request.Builder, http: OkHttpClient = client): FaPage {
        val request = builder.header("Accept", "text/html,application/xhtml+xml").build()
        val url = request.url.toString()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (Challenge.isChallenge(response.code, response.header("cf-mitigated"), body)) {
                throw ChallengeRequired(url)
            }
            if (!response.isSuccessful) throw FaHttpException(response.code, url)
            val finalUrl = response.request.url.toString()
            val doc = Jsoup.parse(body, finalUrl)
            return FaPage(finalUrl, doc, HeaderParser.parse(doc))
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
    }
}
