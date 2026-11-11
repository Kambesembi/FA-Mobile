package com.kambesembi.famobile.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kambesembi.famobile.AppContainer
import com.kambesembi.famobile.BuildConfig
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.AppSettings
import com.kambesembi.famobile.data.CheckInterval
import com.kambesembi.famobile.data.FaUrls
import com.kambesembi.famobile.data.NoteResult
import com.kambesembi.famobile.data.SessionError
import com.kambesembi.famobile.data.SessionState
import com.kambesembi.famobile.data.model.ActivityKind
import com.kambesembi.famobile.data.model.Blocklist
import com.kambesembi.famobile.data.model.NoteDraft
import com.kambesembi.famobile.data.model.SubmissionResult
import com.kambesembi.famobile.data.model.UploadStatus
import com.kambesembi.famobile.data.notify.Notifier
import com.kambesembi.famobile.data.parse.FaLink
import com.kambesembi.famobile.ui.components.Bubble
import com.kambesembi.famobile.ui.components.ContentFilter
import com.kambesembi.famobile.ui.components.FurryLoader
import com.kambesembi.famobile.ui.components.LocalContentFilter
import com.kambesembi.famobile.ui.components.LocalGridColumns
import com.kambesembi.famobile.ui.components.LocalOpenOutside
import com.kambesembi.famobile.ui.components.LocalPullSurprise
import com.kambesembi.famobile.ui.components.LocalQuickActions
import com.kambesembi.famobile.ui.components.OutsideLinkDialog
import com.kambesembi.famobile.ui.components.PullSurprise
import com.kambesembi.famobile.ui.components.QuickActions
import com.kambesembi.famobile.ui.components.UpdatePrompt
import com.kambesembi.famobile.ui.explore.ExploreTab
import com.kambesembi.famobile.ui.fasettings.FaSettingsPicker
import com.kambesembi.famobile.ui.fasettings.FaSettingsView
import com.kambesembi.famobile.ui.journal.JournalActions
import com.kambesembi.famobile.ui.journal.JournalScreen
import com.kambesembi.famobile.ui.login.ChallengeScreen
import com.kambesembi.famobile.ui.login.LoginScreen
import com.kambesembi.famobile.ui.notes.ComposeScreen
import com.kambesembi.famobile.ui.notes.NoteScreen
import com.kambesembi.famobile.ui.profile.ProfileActions
import com.kambesembi.famobile.ui.profile.ProfileScreen
import com.kambesembi.famobile.ui.saved.SavedActions
import com.kambesembi.famobile.ui.saved.SavedScreen
import com.kambesembi.famobile.ui.settings.SettingsScreen
import com.kambesembi.famobile.ui.shell.ActivityTab
import com.kambesembi.famobile.ui.shell.InboxTab
import com.kambesembi.famobile.ui.shell.MainShell
import com.kambesembi.famobile.ui.shell.NotesTab
import com.kambesembi.famobile.ui.shell.UploadIndicator
import com.kambesembi.famobile.ui.upload.UploadActions
import com.kambesembi.famobile.ui.upload.UploadScreen
import com.kambesembi.famobile.ui.upload.UploadsActions
import com.kambesembi.famobile.ui.upload.UploadsScreen
import com.kambesembi.famobile.ui.viewer.ComicScreen
import com.kambesembi.famobile.ui.viewer.ViewerActions
import com.kambesembi.famobile.ui.viewer.ViewerScreen
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the activity was started: a tab and an optional screen ("settings"), used by CI screenshots. */
data class StartOptions(val tab: Int = 0, val screen: String? = null)

@Composable
fun AppRoot(container: AppContainer, start: StartOptions) {
    val session by container.session.state.collectAsStateWithLifecycle()
    val challengeUrl by container.challenge.pendingUrl.collectAsStateWithLifecycle()
    // App scope, so a refresh survives rotation.
    val refresh: () -> Unit = { container.scope.launch { container.session.refresh() } }

    LaunchedEffect(Unit) {
        if (session == SessionState.Starting) container.session.refresh()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val s = session) {
            SessionState.Starting -> Loading()
            SessionState.LoggedOut -> LoginScreen(container.isDemo, onLoggedIn = refresh)
            is SessionState.Unreachable -> Unreachable(s.error, onRetry = refresh)
            is SessionState.LoggedIn -> LoggedIn(container, s, start, refresh)
        }
        challengeUrl?.let { url ->
            ChallengeScreen(url, container.userAgent, onResult = container.challenge::resolve)
        }
        if (!container.isDemo) UpdatePrompt(container.updates, container.installer)
    }
}

@Composable
private fun LoggedIn(container: AppContainer, state: SessionState.LoggedIn, start: StartOptions, refresh: () -> Unit) {
    val nav = rememberNavController()
    val inbox by container.inbox.state.collectAsStateWithLifecycle()
    val activity by container.activity.state.collectAsStateWithLifecycle()
    val notes by container.notes.state.collectAsStateWithLifecycle()
    val explore by container.explore.state.collectAsStateWithLifecycle()
    val openRequest by container.openSubmission.collectAsStateWithLifecycle()
    val tabRequest by container.tabRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var openedStartScreen by rememberSaveable { mutableStateOf(false) }
    val scope = container.scope

    LaunchedEffect(Unit) {
        // "settings" or "user/<name>", from the CI screenshots.
        val screen = start.screen
        val known = screen == "settings" || screen == "saved" || screen == "upload" || screen == "uploads" ||
            screen?.startsWith("user/") == true || screen?.startsWith("journal/") == true
        if (!openedStartScreen && screen != null && known) nav.navigate(screen)
        // CI: the form with a picture in it (the emulator has no files to pick).
        if (!openedStartScreen && screen == "upload-demo" && container.isDemo) {
            container.uploads.discardDraft()
            container.uploads.addDemoPicture()
            nav.navigate("upload")
        }
        openedStartScreen = true
    }
    // Android 13+ asks once, right after the first login, so the background check can notify.
    LaunchedEffect(Unit) {
        val settings = container.settings
        if (Build.VERSION.SDK_INT >= 33 && !container.isDemo && !Notifier.permissionGranted(context) &&
            settings.checkInterval.first() != CheckInterval.Off && !settings.askedForNotifications()
        ) {
            settings.setAskedForNotifications()
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // A tapped notification: back to the tabs, where MainShell picks the tab.
    LaunchedEffect(tabRequest) {
        if (tabRequest != null) nav.popBackStack("main", inclusive = false)
    }
    // Files shared from another app, or a tapped upload notification.
    val screenRequest by container.screenRequest.collectAsStateWithLifecycle()
    LaunchedEffect(screenRequest) {
        val route = screenRequest ?: return@LaunchedEffect
        container.consumeScreenRequest()
        if (nav.currentDestination?.route != route) nav.navigate(route) { launchSingleTop = true }
    }
    // A furaffinity.net link from another app.
    LaunchedEffect(openRequest) {
        val id = openRequest ?: return@LaunchedEffect
        container.consumeOpenRequest()
        nav.navigate("viewer/$id/$id")
    }

    fun openUser(name: String) = nav.navigate("user/${name.lowercase()}")

    // The viewer at [id], swiping through [ids]: the list waits in the container, the route has its ticket.
    fun openList(ids: List<Long>, id: Long) {
        if (ids.size <= 1) nav.navigate("viewer/$id/$id") else nav.navigate("viewer-list/${container.viewerLists.put(ids)}/$id")
    }

    // A link in FA text: submissions and user pages open in the app, everything else outside after asking.
    var outsideLink by rememberSaveable { mutableStateOf<String?>(null) }
    fun openLink(url: String) {
        when (val link = FaLink.of(url)) {
            is FaLink.Submission -> nav.navigate("viewer/${link.id}/${link.id}")
            is FaLink.User -> openUser(link.name)
            is FaLink.Journal -> nav.navigate("journal/${link.id}")
            is FaLink.External -> outsideLink = link.url
        }
    }

    // +Fav, -Fav and Save from any grid (long-press), and +Fav for the inbox selection.
    val appSnackbar = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    val quick = remember(container) {
        fun say(message: String) {
            uiScope.launch {
                appSnackbar.currentSnackbarData?.dismiss()
                appSnackbar.showSnackbar(message)
            }
        }
        val working = MutableStateFlow(0)
        fun busy(block: suspend () -> Unit) = scope.launch {
            working.update { it + 1 }
            try {
                block()
            } finally {
                working.update { it - 1 }
            }
        }
        val faved = { id: Long ->
            (container.submissions.loaded.value[id] as? SubmissionResult.Loaded)?.submission?.fav?.faved == true
        }
        QuickActions(
            loaded = container.submissions.loaded,
            favorited = container.submissions.favorited,
            working = working,
            saved = container.settings.savedSubmissions,
            check = { id ->
                scope.launch {
                    try {
                        container.submissions.load(id)
                    } catch (e: IOException) {
                        say(e.message ?: context.getString(R.string.error_network))
                    }
                }
            },
            fav = { previews ->
                busy {
                    var added = 0
                    var already = 0
                    var failure: String? = null
                    for (preview in previews) {
                        val before = faved(preview.id)
                        try {
                            when (val result = container.submissions.setFaved(preview.id, true)) {
                                is SubmissionResult.Loaded -> when {
                                    result.submission.fav?.faved != true -> failure = context.getString(R.string.quick_fav_failed)
                                    before -> already++
                                    else -> added++
                                }
                                is SubmissionResult.Unavailable -> failure = result.message
                            }
                        } catch (e: IOException) {
                            failure = e.message ?: context.getString(R.string.error_network)
                            break
                        }
                    }
                    say(
                        when {
                            added == 0 && already == 0 -> failure ?: context.getString(R.string.quick_fav_failed)
                            previews.size == 1 && already == 1 -> context.getString(R.string.quick_already_faved, previews.single().title)
                            previews.size == 1 -> context.getString(R.string.quick_faved_one, previews.single().title)
                            else -> context.resources.getQuantityString(R.plurals.quick_faved_many, added + already, added + already)
                        },
                    )
                }
            },
            unfav = { preview ->
                busy {
                    try {
                        val result = container.submissions.setFaved(preview.id, false)
                        val gone = (result as? SubmissionResult.Loaded)?.submission?.fav?.faved == false
                        say(
                            if (gone) {
                                context.getString(R.string.quick_unfaved, preview.title)
                            } else {
                                (result as? SubmissionResult.Unavailable)?.message ?: context.getString(R.string.quick_unfav_failed)
                            },
                        )
                    } catch (e: IOException) {
                        say(e.message ?: context.getString(R.string.error_network))
                    }
                }
            },
            toggleSaved = { item -> scope.launch { container.settings.toggleSavedSubmission(item) } },
        )
    }

    // The top bar's upload button follows the queue.
    val uploadStore by container.uploads.store.collectAsStateWithLifecycle()
    val uploadIndicator = remember(uploadStore.jobs) {
        val active = uploadStore.jobs.filter { it.active }
        UploadIndicator(
            active = active.size,
            progress = active.firstOrNull { it.status == UploadStatus.Sending }?.progress,
        )
    }

    fun viewerActions() = ViewerActions(
        repo = container.submissions,
        scope = scope,
        userAgent = container.userAgent,
        openSubmissions = ::openList,
        openUser = ::openUser,
        openJournal = { id -> nav.navigate("journal/$id") },
        saved = container.settings.savedSubmissions,
        toggleSaved = { item -> scope.launch { container.settings.toggleSavedSubmission(item) } },
        back = { nav.popBackStack() },
        openComic = { id -> nav.navigate("comic/$id") },
    )

    val openOutside = remember { { url: String -> outsideLink = url } }
    val gridColumns by container.settings.gridColumns.collectAsStateWithLifecycle(AppSettings.DEFAULT_COLUMNS)
    val sfw by container.settings.sfw.collectAsStateWithLifecycle(false)
    val blocklist by remember(container) { container.fa.header.map { it?.blocklist }.filterNotNull() }
        .collectAsStateWithLifecycle(Blocklist.None)
    val contentFilter = remember(sfw, blocklist) { ContentFilter(sfw, blocklist) }
    var surprise by remember { mutableStateOf(false) }
    val showSurprise = remember { { surprise = true } }
    outsideLink?.let { url -> OutsideLinkDialog(url, onDismiss = { outsideLink = null }) }
    CompositionLocalProvider(
        LocalQuickActions provides quick,
        LocalOpenOutside provides openOutside,
        LocalGridColumns provides gridColumns,
        LocalContentFilter provides contentFilter,
        LocalPullSurprise provides showSurprise,
    ) {
        Box(Modifier.fillMaxSize()) {
            NavHost(nav, startDestination = "main") {
                composable("main") {
                    MainShell(
                        state = state,
                        inbox = InboxTab(
                            state = inbox,
                            refresh = {
                                scope.launch {
                                    container.inbox.refresh()
                                    container.fa.header.value?.user?.username?.let { container.submissions.syncFavorites(it) }
                                }
                            },
                            loadMore = { scope.launch { container.inbox.loadMore() } },
                            remove = { ids -> scope.launch { container.inbox.remove(ids) } },
                            clearAll = { scope.launch { container.inbox.clearAll() } },
                            open = { id -> nav.navigate("inbox-viewer/$id") },
                        ),
                        activity = ActivityTab(
                            openUser = ::openUser,
                            state = activity,
                            refresh = { scope.launch { container.activity.refresh() } },
                            remove = { item -> scope.launch { container.activity.remove(listOf(item)) } },
                            clear = { section -> scope.launch { container.activity.clear(section) } },
                            open = { item ->
                                if (item.kind == ActivityKind.Shouts) {
                                    // A shout is on your own page: open that, at the shout.
                                    nav.navigate("user/${state.user.username.lowercase()}?shout=${item.checkbox.value}")
                                } else {
                                    // Submissions, journals and profiles open in the app.
                                    val url = item.link ?: item.actor?.let { FaUrls.absolute("/user/${it.name}/") }
                                    if (url != null) openLink(url)
                                }
                            },
                        ),
                        notes = NotesTab(
                            state = notes,
                            refresh = { scope.launch { container.notes.refresh() } },
                            loadMore = { scope.launch { container.notes.loadMore() } },
                            folder = { folder -> scope.launch { container.notes.show(folder) } },
                            open = { id -> nav.navigate("note/$id") },
                            compose = { nav.navigate("compose/0") },
                        ),
                        explore = ExploreTab(
                            state = explore,
                            refresh = { scope.launch { container.explore.refresh() } },
                            loadMore = { scope.launch { container.explore.loadMore() } },
                            search = { query, customized -> scope.launch { container.explore.search(query, customized) } },
                            browse = { filters -> scope.launch { container.explore.browse(filters) } },
                            closeSearch = { scope.launch { container.explore.closeSearch() } },
                            openSubmissions = { _, id -> nav.navigate("explore-viewer/$id") },
                            openUser = ::openUser,
                        ),
                        startTab = start.tab,
                        tabRequest = tabRequest,
                        onTabRequestHandled = container::consumeTabRequest,
                        demo = container.isDemo,
                        onRefresh = refresh,
                        onOpenSettings = { nav.navigate("settings") },
                        onOpenProfile = ::openUser,
                        onOpenSaved = { nav.navigate("saved") },
                        onLogout = container::logout,
                        uploads = uploadIndicator,
                        onUpload = { nav.navigate("upload") },
                        onOpenUploads = { nav.navigate("uploads") },
                    )
                }
                composable("upload") {
                    UploadScreen(
                        UploadActions(
                            repo = container.uploads,
                            scope = scope,
                            username = state.user.username,
                            demo = container.isDemo,
                            back = { nav.popBackStack() },
                            openUploads = { nav.navigate("uploads") { launchSingleTop = true } },
                            submitted = { count, title ->
                                nav.popBackStack()
                                uiScope.launch {
                                    appSnackbar.currentSnackbarData?.dismiss()
                                    val message = if (count == 1) {
                                        context.getString(R.string.upload_started_one, title)
                                    } else {
                                        context.resources.getQuantityString(R.plurals.upload_started_many, count, count)
                                    }
                                    val result = appSnackbar.showSnackbar(message, actionLabel = context.getString(R.string.upload_started_show), duration = SnackbarDuration.Long)
                                    if (result == SnackbarResult.ActionPerformed) nav.navigate("uploads") { launchSingleTop = true }
                                }
                            },
                        ),
                    )
                }
                composable("uploads") {
                    UploadsScreen(
                        UploadsActions(
                            repo = container.uploads,
                            scope = scope,
                            back = { nav.popBackStack() },
                            newUpload = { nav.navigate("upload") { popUpTo("uploads") { inclusive = true } } },
                            openSubmission = { id -> nav.navigate("viewer/$id/$id") },
                            edit = { nav.navigate("upload") { popUpTo("uploads") { inclusive = true } } },
                        ),
                    )
                }
                composable(
                    "inbox-viewer/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: 0L
                    // The inbox list as it was when the viewer opened, so removing a submission doesn't
                    // pull the page out from under the reader.
                    val ids = rememberSaveable {
                        inbox.items.filterNot { contentFilter.hides(it.preview) }.map { it.preview.id }
                            .ifEmpty { listOf(id) }.let { if (id in it) it else listOf(id) + it }
                    }
                    ViewerScreen(ids = ids, startId = id, actions = viewerActions())
                }
                composable(
                    "viewer/{ids}/{id}",
                    arguments = listOf(
                        navArgument("ids") { type = NavType.StringType },
                        navArgument("id") { type = NavType.LongType },
                    ),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: 0L
                    val ids = entry.arguments?.getString("ids").orEmpty()
                        .split(',').mapNotNull { it.toLongOrNull() }.ifEmpty { listOf(id) }
                    ViewerScreen(ids = ids, startId = id, actions = viewerActions())
                }
                composable(
                    "viewer-list/{ticket}/{id}",
                    arguments = listOf(
                        navArgument("ticket") { type = NavType.LongType },
                        navArgument("id") { type = NavType.LongType },
                    ),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: 0L
                    val ticket = entry.arguments?.getLong("ticket") ?: 0L
                    val ids = remember(ticket) { container.viewerLists.get(ticket)?.takeIf { id in it } ?: listOf(id) }
                    ViewerScreen(ids = ids, startId = id, actions = viewerActions())
                }
                composable(
                    "explore-viewer/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: 0L
                    // The results as they are now: swiping on past the last one loads the next page.
                    val shown = remember(explore.items, contentFilter) {
                        explore.items.filterNot { contentFilter.hides(it) }.map { it.id }
                    }
                    val ids = if (id in shown) shown else listOf(id)
                    ViewerScreen(
                        ids = ids,
                        startId = id,
                        actions = viewerActions(),
                        onNearEnd = if (explore.hasNext && id in shown) ({ scope.launch { container.explore.loadMore() } }) else null,
                    )
                }
                composable("comic/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    val id = entry.arguments?.getLong("id") ?: 0L
                    ComicScreen(
                        startId = id,
                        repo = container.comics,
                        settings = container.settings,
                        onOpen = { page -> nav.navigate("viewer/$page/$page") },
                        onBack = {
                            container.comics.close()
                            nav.popBackStack()
                        },
                    )
                }
                composable("note/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    val id = entry.arguments?.getLong("id") ?: 0L
                    NoteScreen(
                        id = id,
                        repo = container.notes,
                        scope = scope,
                        onLink = ::openLink,
                        onReply = { note -> nav.navigate("compose/${note.id}") },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("compose/{reply}", arguments = listOf(navArgument("reply") { type = NavType.LongType })) { entry ->
                    val replyTo = entry.arguments?.getLong("reply") ?: 0L
                    val opened by container.notes.opened.collectAsStateWithLifecycle()
                    val draft = (opened[replyTo] as? NoteResult.Loaded)?.note?.reply ?: NoteDraft("", "", "")
                    ComposeScreen(
                        draft = draft,
                        scope = scope,
                        send = container.notes::send,
                        onDone = { nav.popBackStack() },
                    )
                }
                composable(
                    "user/{name}?shout={shout}",
                    arguments = listOf(
                        navArgument("name") { type = NavType.StringType },
                        navArgument("shout") {
                            type = NavType.StringType
                            nullable = true
                        },
                    ),
                ) { entry ->
                    val name = entry.arguments?.getString("name").orEmpty()
                    ProfileScreen(
                        name = name,
                        shout = entry.arguments?.getString("shout")?.toLongOrNull(),
                        repo = container.profiles,
                        actions = ProfileActions(
                            openSubmissions = ::openList,
                            openLink = ::openLink,
                            writeNote = { to -> nav.navigate("compose-to/$to") },
                            openJournal = { id -> nav.navigate("journal/$id") },
                            saved = container.settings.savedArtists,
                            toggleSaved = { artist -> scope.launch { container.settings.toggleSavedArtist(artist) } },
                            back = { nav.popBackStack() },
                        ),
                    )
                }
                composable("compose-to/{to}", arguments = listOf(navArgument("to") { type = NavType.StringType })) { entry ->
                    ComposeScreen(
                        draft = NoteDraft(entry.arguments?.getString("to").orEmpty(), "", ""),
                        scope = scope,
                        send = container.notes::send,
                        onDone = { nav.popBackStack() },
                    )
                }
                composable("saved") {
                    val submissions by container.settings.savedSubmissions.collectAsStateWithLifecycle(emptyList())
                    val artists by container.settings.savedArtists.collectAsStateWithLifecycle(emptyList())
                    SavedScreen(
                        submissions = submissions,
                        artists = artists,
                        actions = SavedActions(
                            openSubmissions = ::openList,
                            openUser = ::openUser,
                            removeSubmission = { item -> scope.launch { container.settings.toggleSavedSubmission(item) } },
                            removeArtist = { artist -> scope.launch { container.settings.toggleSavedArtist(artist) } },
                            back = { nav.popBackStack() },
                        ),
                    )
                }
                composable("journal/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    JournalScreen(
                        id = entry.arguments?.getLong("id") ?: 0L,
                        actions = JournalActions(
                            repo = container.journals,
                            openLink = ::openLink,
                            openUser = ::openUser,
                            back = { nav.popBackStack() },
                        ),
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        user = state.user,
                        settings = container.settings,
                        updates = container.updates,
                        version = BuildConfig.VERSION_NAME,
                        onBack = { nav.popBackStack() },
                        onLogout = container::logout,
                        onFaSettings = { nav.navigate("fa-settings") },
                        onChooseFaSettings = { nav.navigate("fa-settings-pick") },
                    )
                }
                composable("fa-settings") {
                    val chosen by container.settings.faSettings.collectAsStateWithLifecycle(null)
                    chosen?.let { ids ->
                        FaSettingsView(
                            repository = container.faSettings,
                            chosen = ids,
                            onChoose = { nav.navigate("fa-settings-pick") },
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
                composable("fa-settings-pick") {
                    val chosen by container.settings.faSettings.collectAsStateWithLifecycle(null)
                    chosen?.let { ids ->
                        FaSettingsPicker(
                            chosen = ids,
                            onConfirm = { picked ->
                                scope.launch {
                                    container.settings.setFaSettings(picked)
                                    // To the page itself, without the picker or a second page behind it.
                                    nav.navigate("fa-settings") {
                                        popUpTo("settings")
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
            // Messages from grid actions, above the tab bar.
            SnackbarHost(
                appSnackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 80.dp),
            )
            // The pull carries on: the surprise slides down from the top, and fades away on a tap.
            androidx.compose.animation.AnimatedVisibility(
                visible = surprise,
                enter = slideInVertically(tween(420, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(300)),
                exit = fadeOut(tween(260)) + scaleOut(tween(260), targetScale = 1.08f),
            ) { PullSurprise(onDismiss = { surprise = false }) }
        }
    }
}

@Composable
private fun Loading() {
    FurryLoader()
}

@Composable
private fun Unreachable(error: SessionError, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Bubble(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.error_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    errorText(error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

@Composable
fun errorText(error: SessionError): String = when (error) {
    SessionError.ChallengeCancelled -> stringResource(R.string.error_challenge_cancelled)
    is SessionError.Network -> error.message
}
