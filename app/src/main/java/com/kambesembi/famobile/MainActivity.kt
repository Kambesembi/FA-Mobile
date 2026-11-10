package com.kambesembi.famobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.kambesembi.famobile.data.AppLanguage
import com.kambesembi.famobile.data.ThemeMode
import com.kambesembi.famobile.data.notify.Category
import com.kambesembi.famobile.data.notify.LineKind
import com.kambesembi.famobile.data.notify.News
import com.kambesembi.famobile.data.notify.NewsLine
import com.kambesembi.famobile.data.notify.NotificationAvatars
import com.kambesembi.famobile.data.notify.Notifier
import com.kambesembi.famobile.data.parse.FaLink
import com.kambesembi.famobile.ui.AppRoot
import com.kambesembi.famobile.ui.StartOptions
import com.kambesembi.famobile.ui.theme.FaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container get() = (application as FaApp).container

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Demo mode (saved pages, no network) is for CI screenshots and exists in debug builds only.
        if (BuildConfig.DEBUG && intent.getBooleanExtra("demo", false) && !container.isDemo) {
            runCatching { container.demoPage = intent.getStringExtra("demoPage") ?: "header-loggedin.html" }
        }
        if (BuildConfig.DEBUG && container.isDemo) container.setDemoSlowViews(intent.getBooleanExtra("demoSlow", false))
        container.startBackground()
        // Debug only: sample notifications, so CI can screenshot the notification shade.
        if (BuildConfig.DEBUG && intent.getBooleanExtra("demoNotify", false)) {
            Notifier.createChannels(this)
            val samples = listOf(
                    News(Category.Watches, 1, 3, listOf(NewsLine(LineKind.Watch, "Artist2", null, "artist2"))),
                    News(
                        Category.Comments, 2, 5,
                        listOf(
                            NewsLine(LineKind.SubmissionComment, "Artist3", "Submission 10000610"),
                            NewsLine(LineKind.Shout, "Artist4", null),
                        ),
                    ),
                    News(Category.Notes, 1, 4, listOf(NewsLine(LineKind.Note, "Artist2", "Commission question", "artist2"))),
                    News(Category.Submissions, 12, 1204, listOf(NewsLine(LineKind.Submission, "Artist5", "Sketch 24", "artist5"))),
                )
            val context = applicationContext
            lifecycleScope.launch {
                Notifier.post(context, samples, NotificationAvatars.load(context, container.imageLoader, samples))
            }
        }
        val start = StartOptions(
            tab = intent.getIntExtra(EXTRA_TAB, 0),
            // Debug only (CI screenshots): another app must not steer the release app's start.
            screen = if (BuildConfig.DEBUG) intent.getStringExtra("screen") else null,
        )
        if (savedInstanceState == null) {
            handleLink(intent)
            handleOpen(intent)
        }

        setContent {
            val theme by container.settings.theme.collectAsStateWithLifecycle(ThemeMode.System)
            FaTheme(theme) {
                AppRoot(container, start)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
        handleOpen(intent)
        intent.getIntExtra(EXTRA_TAB, -1).takeIf { it >= 0 }?.let(container::requestTab)
    }

    companion object {
        /** The tab to open: from a notification, or from CI screenshots. */
        const val EXTRA_TAB = "tab"

        /** A screen to open, from an upload notification. */
        const val EXTRA_OPEN = "open"
        const val OPEN_UPLOADS = "uploads"

        /** From the update notification: ask to update now. */
        const val OPEN_UPDATE = "update"
    }

    /**
     * Files shared from another app go into the upload form, copied right away while the app may still
     * read them; an upload notification opens the list of uploads.
     */
    private fun handleOpen(intent: Intent) {
        if (intent.getStringExtra(EXTRA_OPEN) == OPEN_UPLOADS) container.requestScreen("uploads")
        if (intent.getStringExtra(EXTRA_OPEN) == OPEN_UPDATE) container.scope.launch { container.updates.checkAndOffer(force = true) }
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.streamExtra())
            Intent.ACTION_SEND_MULTIPLE -> intent.streamExtras()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        container.requestScreen("upload")
        container.scope.launch { container.uploads.addFiles(uris) }
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamExtra(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun Intent.streamExtras(): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM))
            .orEmpty().toList()

    /** furaffinity.net/view/… links (and the debug-only "view" extra used by CI) open the viewer. */
    private fun handleLink(intent: Intent) {
        val fromLink = intent.data?.let { FaLink.of(it.toString()) as? FaLink.Submission }?.id
        val fromExtra = if (BuildConfig.DEBUG) intent.getLongExtra("view", 0L).takeIf { it > 0 } else null
        (fromLink ?: fromExtra)?.let(container::requestOpen)
    }
}
