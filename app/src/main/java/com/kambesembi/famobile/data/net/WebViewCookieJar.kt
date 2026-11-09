package com.kambesembi.famobile.data.net

import android.webkit.CookieManager
import com.kambesembi.famobile.data.FaUrls
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * One cookie store for the WebViews and OkHttp: the login sets FA's session cookies and Cloudflare's
 * clearance in the WebView, and every native request sends them back unchanged.
 */
class WebViewCookieJar : CookieJar {

    private val manager: CookieManager get() = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) manager.setCookie(url.toString(), cookie.toString())
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = manager.getCookie(url.toString()) ?: return emptyList()
        // CookieManager can list a name twice (host and domain cookie); send each name once.
        return header.split(';')
            .mapNotNull { Cookie.parse(url, it.trim()) }
            .distinctBy { it.name }
    }

    fun flush() = manager.flush()

    /** FA's SFW mode lives in its "sfw" cookie; FA's own switch sets and deletes it just like this. */
    fun setSfw(on: Boolean) {
        val cookie = if (on) "sfw=1; Domain=.furaffinity.net; Path=/; Max-Age=31536000" else "sfw=; Domain=.furaffinity.net; Path=/; Max-Age=0"
        manager.setCookie(FaUrls.BASE, cookie)
        manager.flush()
    }

    fun clear() {
        manager.removeAllCookies(null)
        manager.flush()
    }
}
