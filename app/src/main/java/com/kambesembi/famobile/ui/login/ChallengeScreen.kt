package com.kambesembi.famobile.ui.login

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.parse.Challenge
import com.kambesembi.famobile.ui.components.Bubble
import com.kambesembi.famobile.ui.web.FaWebView
import kotlinx.coroutines.delay

/**
 * Shows Cloudflare's check full-size (a tiny WebView can't complete it) and reports back once the
 * real page has loaded behind it.
 */
@Composable
fun ChallengeScreen(url: String, userAgent: String, onResult: (Boolean) -> Unit) {
    val report by rememberUpdatedState(onResult)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var done by remember { mutableStateOf(false) }

    BackHandler { report(false) }

    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        while (!done) {
            delay(1000)
            view.evaluateJavascript(Challenge.PROBE_JS) { result ->
                if (result == "\"ok\"" && !done) {
                    done = true
                    CookieManager.getInstance().flush()
                    report(true)
                }
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding()) {
            Bubble(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.challenge_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { report(false) }) { Text(stringResource(R.string.cancel)) }
                }
                Text(
                    stringResource(R.string.challenge_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FaWebView(
                url = url,
                userAgent = userAgent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .clip(MaterialTheme.shapes.large),
                onCreated = { webView = it },
            )
        }
    }
}
