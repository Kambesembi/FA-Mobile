package com.kambesembi.famobile.data.update

import com.kambesembi.famobile.data.Words
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** A published version of the app on GitHub. */
data class Release(val version: String, val pageUrl: String, val apkUrl: String?)

sealed interface UpdateState {
    data object Unknown : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    /** GitHub doesn't show the releases (the repository is still private). */
    data object NotPublic : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Looks at the latest release of the app's GitHub repository; nothing else is sent or asked. */
object UpdateCheck {
    const val REPO = "Kambesembi/FA-Mobile"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** GitHub's answer for the latest release: its tag (v0.1.31 → 0.1.31), page and APK. */
    fun parse(json: String): Release? = runCatching {
        val o = JSONObject(json)
        val tag = o.optString("tag_name").trim().removePrefix("v").ifEmpty { return null }
        val assets = o.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0))
            .mapNotNull { assets?.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?.optString("browser_download_url")
            ?.ifEmpty { null }
        Release(tag, o.optString("html_url").ifEmpty { RELEASES_PAGE }, apk)
    }.getOrNull()

    /** Whether [candidate] ("0.1.31") is a later version than [current] ("0.1.30"), number by number. */
    fun newer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Asks GitHub; [current] is this app's version. */
    suspend fun check(current: String): UpdateState = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "FA-Mobile-update-check")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    // A private repository looks like no repository at all.
                    response.code == 404 -> UpdateState.NotPublic
                    !response.isSuccessful -> UpdateState.Failed("HTTP ${response.code}")
                    else -> {
                        val release = parse(response.body?.string().orEmpty()) ?: return@use UpdateState.Failed(Words.get(Words.Text.UpdateUnreadable))
                        if (newer(release.version, current)) UpdateState.Available(release) else UpdateState.UpToDate
                    }
                }
            }
        } catch (e: IOException) {
            UpdateState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
