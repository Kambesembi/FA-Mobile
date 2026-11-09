package com.kambesembi.famobile.ui.web

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kambesembi.famobile.data.FaUrls

/** Settings every FA WebView shares: same User-Agent and cookies as OkHttp, so Cloudflare's clearance fits both. */
@SuppressLint("SetJavaScriptEnabled")
fun WebView.configureForFa(userAgent: String, background: Int) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.userAgentString = userAgent
    CookieManager.getInstance().setAcceptCookie(true)
    // Cloudflare's check runs in a frame on challenges.cloudflare.com.
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    // Lets password managers fill the login form through Android's autofill.
    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
    setBackgroundColor(background)
}

/** Keeps FA (and Cloudflare's check) in the WebView; any other link opens in the phone's browser. */
open class FaWebViewClient(
    private val onFinished: (WebView, String) -> Unit,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val host = request.url.host
        if (!request.isForMainFrame || FaUrls.isFa(host) || host == "challenges.cloudflare.com") return false
        runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url.toString()))) }
        return true
    }

    override fun onPageFinished(view: WebView, url: String) {
        onFinished(view, url)
    }
}
