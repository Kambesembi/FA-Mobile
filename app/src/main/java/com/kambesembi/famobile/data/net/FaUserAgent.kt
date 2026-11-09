package com.kambesembi.famobile.data.net

import android.content.Context
import android.webkit.WebSettings
import com.kambesembi.famobile.BuildConfig

object FaUserAgent {
    /**
     * The User-Agent for every request and WebView. Cloudflare ties its clearance cookie to the exact
     * User-Agent, so the WebViews and OkHttp must all send this same string. The tag lets FA staff
     * tell this app's traffic apart.
     *
     * It has the reduced form Chrome on Android sends ("Android 10; K", only the major version), with
     * the WebView's Chrome version: FA answers the WebView's own string, which names the real Android
     * version and the device, with a "Database Error" page.
     */
    fun build(context: Context): String = reduced(WebSettings.getDefaultUserAgent(context), BuildConfig.VERSION_NAME)

    /** [webView] is the WebView's default User-Agent; only its Chrome major version is kept. */
    fun reduced(webView: String, version: String): String {
        val major = Regex("""Chrome/(\d+)""").find(webView)?.groupValues?.get(1) ?: FALLBACK_CHROME
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Mobile Safari/537.36 FAMobile/$version"
    }

    private const val FALLBACK_CHROME = "130"
}
