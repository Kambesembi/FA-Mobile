package com.kambesembi.famobile

import android.app.Application
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import com.kambesembi.famobile.data.ActivityRepository
import com.kambesembi.famobile.data.AppSettings
import com.kambesembi.famobile.data.ChallengeCoordinator
import com.kambesembi.famobile.data.ComicRepository
import com.kambesembi.famobile.data.ExploreRepository
import com.kambesembi.famobile.data.FaClient
import com.kambesembi.famobile.data.FaSettingsRepository
import com.kambesembi.famobile.data.InboxRepository
import com.kambesembi.famobile.data.JournalRepository
import com.kambesembi.famobile.data.NotesRepository
import com.kambesembi.famobile.data.ProfileRepository
import com.kambesembi.famobile.data.SessionRepository
import com.kambesembi.famobile.data.SubmissionRepository
import com.kambesembi.famobile.data.UploadRepository
import com.kambesembi.famobile.data.net.DemoSource
import com.kambesembi.famobile.data.net.FaUserAgent
import com.kambesembi.famobile.data.net.LiveSource
import com.kambesembi.famobile.data.net.PageSource
import com.kambesembi.famobile.data.notify.CheckWorker
import com.kambesembi.famobile.data.notify.Notifier
import com.kambesembi.famobile.data.update.Updates
import com.kambesembi.famobile.data.upload.UploadNotifier
import com.kambesembi.famobile.ui.util.DemoImageInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The app's long-lived objects, created on first use. */
class AppContainer(private val app: Application) {

    /** Set by MainActivity before anything else is touched; only debug builds honour it. */
    var demoPage: String? = null
        set(value) {
            check(!sourceCreated) { "demo mode must be chosen before the first request" }
            field = value
        }

    val isDemo: Boolean get() = demoPage != null

    private var sourceCreated = false

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = AppSettings(app)
    val updates = Updates(app, settings)

    val userAgent: String by lazy { FaUserAgent.build(app) }

    val challenge = ChallengeCoordinator()

    /** CI only: slow submission pages in demo mode. */
    fun setDemoSlowViews(on: Boolean) {
        (source as? DemoSource)?.slowViews = on
    }

    private val source: PageSource by lazy {
        sourceCreated = true
        demoPage?.let { DemoSource(app.assets, it) } ?: LiveSource(userAgent)
    }

    val fa: FaClient by lazy { FaClient(source, challenge) }

    val session: SessionRepository by lazy { SessionRepository(fa, scope) }

    val inbox: InboxRepository by lazy { InboxRepository(fa) }

    val submissions: SubmissionRepository by lazy { SubmissionRepository(fa) }
    val faSettings: FaSettingsRepository by lazy { FaSettingsRepository(fa) }
    val comics: ComicRepository by lazy { ComicRepository(fa, submissions) }

    val activity: ActivityRepository by lazy { ActivityRepository(fa) }

    val notes: NotesRepository by lazy { NotesRepository(fa) }

    val profiles: ProfileRepository by lazy { ProfileRepository(fa) }
    val journals: JournalRepository by lazy { JournalRepository(fa) }

    val explore: ExploreRepository by lazy { ExploreRepository(fa) }

    val uploads: UploadRepository by lazy { UploadRepository(app, fa, scope, isDemo) }

    /**
     * Where the background upload talks to FA: its own connection (the login cookies are shared), so
     * a big file never holds up the pages the app loads. The demo has only its saved pages.
     */
    fun uploadSource(): PageSource = if (isDemo) source else LiveSource(userAgent)

    private val _screenRequest = MutableStateFlow<String?>(null)

    /** A screen to open ("upload", "uploads"): shared files, or a tapped upload notification. */
    val screenRequest: StateFlow<String?> = _screenRequest.asStateFlow()

    fun requestScreen(route: String) {
        _screenRequest.value = route
    }

    fun consumeScreenRequest() {
        _screenRequest.value = null
    }

    private val _openSubmission = MutableStateFlow<Long?>(null)

    /** A submission to open, from a furaffinity.net link tapped in another app. */
    val openSubmission: StateFlow<Long?> = _openSubmission.asStateFlow()

    fun requestOpen(id: Long) {
        _openSubmission.value = id
    }

    fun consumeOpenRequest() {
        _openSubmission.value = null
    }

    private val _tabRequest = MutableStateFlow<Int?>(null)

    /** A tab to show, from a tapped notification while the app was already open. */
    val tabRequest: StateFlow<Int?> = _tabRequest.asStateFlow()

    fun requestTab(tab: Int) {
        _tabRequest.value = tab
    }

    fun consumeTabRequest() {
        _tabRequest.value = null
    }

    private var backgroundStarted = false

    /**
     * Called once the activity knows whether this is demo mode: schedules the background check and
     * remembers every counter the app shows, so a notification only ever reports what's new since.
     */
    fun startBackground() {
        if (backgroundStarted || isDemo) return
        backgroundStarted = true
        Notifier.createChannels(app)
        UploadNotifier.createChannels(app)
        Updates.createChannel(app)
        uploads.resume()
        scope.launch { updates.schedule(settings.updateChecks.first()) }
        scope.launch { settings.sfw.collect { fa.setSfw(it) } }
        scope.launch { CheckWorker.schedule(app, settings.checkInterval.first(), replace = false) }
        scope.launch {
            fa.header.filterNotNull().collect { header ->
                if (header.user != null) {
                    settings.setSeenCounts(header.counts)
                    Notifier.clear(app)
                    // The app's SFW choice wins: after a new login FA's cookie is gone, so set it again.
                    val sfw = settings.sfw.first()
                    if (header.sfw != null && header.sfw != sfw) fa.setSfw(sfw)
                }
            }
        }
    }

    fun logout() {
        session.logout()
        inbox.clear()
        activity.clear()
        notes.clear()
        submissions.clear()
        profiles.clear()
        journals.clear()
        explore.clear()
        uploads.clear()
    }

    val imageLoader: ImageLoader by lazy {
        val builder = ImageLoader.Builder(app)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                // Demo mode never touches the network: every image is drawn locally.
                if (isDemo) add(DemoImageInterceptor())
            }
            .crossfade(true)
            // FA's files never change under the same address (a new upload gets a new one), so a cached
            // copy is always good: no request to ask the server whether it still is.
            .respectCacheHeaders(false)
            // A fixed cap instead of Coil's 2 % of the disk: enough for a few days of browsing.
            .diskCache { DiskCache.Builder().directory(app.cacheDir.resolve("image_cache")).maxSizeBytes(IMAGE_CACHE_BYTES).build() }
        (source as? LiveSource)?.let { builder.okHttpClient(it.client) }
        builder.build()
    }
}

private const val IMAGE_CACHE_BYTES = 150L * 1024 * 1024
