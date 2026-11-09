package com.kambesembi.famobile.ui.web

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

/**
 * An FA WebView inside Compose, for pages without typing (the Cloudflare check). Forms with text
 * fields go to [com.kambesembi.famobile.ui.login.LoginActivity] instead: inside Compose the field
 * loses focus whenever the keyboard changes height.
 */
@Composable
fun FaWebView(
    url: String,
    userAgent: String,
    modifier: Modifier = Modifier,
    onPageFinished: (WebView, String) -> Unit = { _, _ -> },
    onCreated: (WebView) -> Unit = {},
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    val pageFinished by rememberUpdatedState(onPageFinished)
    val background = MaterialTheme.colorScheme.background.toArgb()

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                configureForFa(userAgent, background)
                webViewClient = FaWebViewClient { view, finishedUrl ->
                    canGoBack = view.canGoBack()
                    pageFinished(view, finishedUrl)
                }
                webView = this
                onCreated(this)
                loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
    )
}
