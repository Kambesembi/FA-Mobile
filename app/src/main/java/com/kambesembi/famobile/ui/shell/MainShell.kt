package com.kambesembi.famobile.ui.shell

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.ActivityState
import com.kambesembi.famobile.data.InboxState
import com.kambesembi.famobile.data.NotesState
import com.kambesembi.famobile.data.SessionState
import com.kambesembi.famobile.data.model.ActivityItem
import com.kambesembi.famobile.data.model.ActivitySection
import com.kambesembi.famobile.data.model.NoteFolder
import com.kambesembi.famobile.data.parse.Counts
import com.kambesembi.famobile.data.parse.LoggedInUser
import com.kambesembi.famobile.ui.activity.ActivityScreen
import com.kambesembi.famobile.ui.components.Avatar
import com.kambesembi.famobile.ui.components.BottomBarHeight
import com.kambesembi.famobile.ui.components.LocalContentFilter
import com.kambesembi.famobile.ui.components.LocalQuickActions
import com.kambesembi.famobile.ui.components.PullRefresh
import com.kambesembi.famobile.ui.components.TopBarHeight
import com.kambesembi.famobile.ui.components.isWide
import com.kambesembi.famobile.ui.errorText
import com.kambesembi.famobile.ui.explore.ExploreScreen
import com.kambesembi.famobile.ui.explore.ExploreTab
import com.kambesembi.famobile.ui.inbox.InboxScreen
import com.kambesembi.famobile.ui.notes.NotesScreen

enum class Tab(
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val badge: (Counts) -> Int,
) {
    Inbox(R.string.tab_inbox, Icons.Outlined.Collections, Icons.Filled.Collections, { it.submissions }),
    Activity(R.string.tab_activity, Icons.Outlined.Notifications, Icons.Filled.Notifications, { it.activity }),
    Notes(R.string.tab_notes, Icons.Outlined.Mail, Icons.Filled.Mail, { it.notes }),
    Explore(R.string.tab_explore, Icons.Outlined.Explore, Icons.Filled.Explore, { 0 }),
}

/** What the Inbox tab needs: its state and what it can ask for. */
class InboxTab(
    val state: InboxState,
    val refresh: () -> Unit,
    val loadMore: () -> Unit,
    val remove: (Set<Long>) -> Unit,
    val clearAll: () -> Unit,
    val open: (Long) -> Unit,
)

/** What the Notes tab needs. */
class NotesTab(
    val state: NotesState,
    val refresh: () -> Unit,
    val loadMore: () -> Unit,
    val folder: (NoteFolder) -> Unit,
    val open: (Long) -> Unit,
    val compose: () -> Unit,
)

/** What the Activity tab needs. */
class ActivityTab(
    val state: ActivityState,
    val refresh: () -> Unit,
    val remove: (ActivityItem) -> Unit,
    val clear: (ActivitySection) -> Unit,
    val open: (ActivityItem) -> Unit,
    val openUser: (String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    state: SessionState.LoggedIn,
    inbox: InboxTab,
    activity: ActivityTab,
    notes: NotesTab,
    explore: ExploreTab,
    startTab: Int,
    /** A tab asked for by a tapped notification; [onTabRequestHandled] once it is shown. */
    tabRequest: Int?,
    onTabRequestHandled: () -> Unit,
    demo: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: (name: String) -> Unit,
    onOpenSaved: () -> Unit,
    onLogout: () -> Unit,
    uploads: UploadIndicator = UploadIndicator(),
    onUpload: () -> Unit = {},
    onOpenUploads: () -> Unit = {},
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(startTab.coerceIn(0, Tab.entries.lastIndex)) }
    val tab = Tab.entries[tabIndex]
    val snackbar = remember { SnackbarHostState() }
    val errorMessage = state.error?.let { errorText(it) }
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    var confirmClear by remember { mutableStateOf(false) }
    val selecting = tab == Tab.Inbox && selection.isNotEmpty()
    val filter = LocalContentFilter.current
    val quickWorking = LocalQuickActions.current?.working?.collectAsStateWithLifecycle()?.value ?: 0

    LaunchedEffect(tabRequest) {
        if (tabRequest != null) {
            tabIndex = tabRequest.coerceIn(0, Tab.entries.lastIndex)
            selection = emptySet()
            onTabRequestHandled()
        }
    }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) snackbar.showSnackbar(errorMessage)
    }
    // Before the first page loads the inbox shows its own error; afterwards it goes in a snackbar.
    val inboxError = inbox.state.error.takeIf { inbox.state.loaded }
    LaunchedEffect(inboxError) {
        if (inboxError != null) snackbar.showSnackbar(inboxError)
    }
    val notesError = notes.state.error.takeIf { notes.state.loaded }
    LaunchedEffect(notesError) {
        if (notesError != null) snackbar.showSnackbar(notesError)
    }
    val activityError = activity.state.error.takeIf { activity.state.loaded }
    LaunchedEffect(activityError) {
        if (activityError != null) snackbar.showSnackbar(activityError)
    }
    // A tab loads when it is first shown, and again on its way back when FA's count for it went up
    // (something new came in while another tab was shown); Activity and Notes also when they were
    // last shown a while ago.
    val lastShown = remember { mutableMapOf<Tab, Pair<Int, Long>>() }
    val shownCount = tab.badge(state.counts)
    LaunchedEffect(tab, shownCount) {
        val now = SystemClock.elapsedRealtime()
        val before = lastShown[tab]
        lastShown[tab] = shownCount to now
        val more = before == null || shownCount > before.first
        val stale = more || now - before!!.second > TAB_STALE_MS
        when (tab) {
            // The inbox keeps its pages (and place) unless there are new submissions.
            Tab.Inbox -> if ((!inbox.state.loaded || (more && shownCount > 0)) && !inbox.state.refreshing) inbox.refresh()
            Tab.Activity -> if ((!activity.state.loaded || stale) && !activity.state.refreshing) activity.refresh()
            Tab.Notes -> if ((!notes.state.loaded || stale) && !notes.state.refreshing) notes.refresh()
            Tab.Explore -> if (!explore.state.loaded && !explore.state.refreshing) explore.refresh()
        }
    }
    // Back after a while away (a notification may have said there is something new): load again,
    // the counts and the tab on screen, without a pull.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reloadShown by rememberUpdatedState<() -> Unit> {
        onRefresh()
        when (tab) {
            Tab.Inbox -> if (inbox.state.loaded && !inbox.state.refreshing) inbox.refresh()
            Tab.Activity -> if (activity.state.loaded && !activity.state.refreshing) activity.refresh()
            Tab.Notes -> if (notes.state.loaded && !notes.state.refreshing) notes.refresh()
            Tab.Explore -> if (explore.state.loaded && !explore.state.refreshing) explore.refresh()
        }
    }
    DisposableEffect(lifecycle) {
        var leftAt = 0L
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> leftAt = SystemClock.elapsedRealtime()
                Lifecycle.Event.ON_START -> if (leftAt > 0 && SystemClock.elapsedRealtime() - leftAt > AWAY_REFRESH_MS) reloadShown()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Forget selected submissions that have left the inbox.
    LaunchedEffect(inbox.state.items) {
        val present = inbox.state.ids.toHashSet()
        if (selection.any { it !in present }) selection = selection.filter { it in present }.toSet()
    }
    BackHandler(enabled = selecting) { selection = emptySet() }
    val wide = isWide()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selecting) {
                TopAppBar(
                    expandedHeight = TopBarHeight,
                    navigationIcon = {
                        IconButton(onClick = { selection = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                    title = { Text(stringResource(R.string.inbox_selected, selection.size), fontWeight = FontWeight.SemiBold) },
                    actions = {
                        IconButton(onClick = {
                            selection = inbox.state.items.filterNot { filter.hides(it.preview) }.map { it.preview.id }.toSet()
                        }) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = stringResource(R.string.inbox_select_all))
                        }
                        val quick = LocalQuickActions.current
                        if (quick != null) {
                            IconButton(onClick = {
                                quick.fav(inbox.state.items.map { it.preview }.filter { it.id in selection })
                                selection = emptySet()
                            }) {
                                Icon(Icons.Outlined.FavoriteBorder, contentDescription = stringResource(R.string.inbox_fav_selected))
                            }
                        }
                        IconButton(onClick = {
                            inbox.remove(selection)
                            selection = emptySet()
                        }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.inbox_remove_selected))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                )
            } else {
                TopAppBar(
                    expandedHeight = TopBarHeight,
                    title = { Text(stringResource(tab.label), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        if (tab == Tab.Inbox && inbox.state.items.isNotEmpty()) {
                            // Asks first, so it can sit right in the bar.
                            IconButton(onClick = { confirmClear = true }) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.inbox_clear_all))
                            }
                        }
                        UploadButton(uploads, onClick = if (uploads.active > 0) onOpenUploads else onUpload)
                        AccountButton(state.user, onOpenSettings, onOpenProfile, onOpenSaved, onLogout, onOpenUploads)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        bottomBar = { if (!wide) BottomBar(tab, state.counts) { tabIndex = it.ordinal } },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == Tab.Notes) {
                ExtendedFloatingActionButton(
                    onClick = notes.compose,
                    icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    text = { Text(stringResource(R.string.note_compose)) },
                )
            }
        },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            if (wide) SideRail(tab, state.counts) { tabIndex = it.ordinal }
            PullRefresh(
                isRefreshing = when (tab) {
                    Tab.Inbox -> inbox.state.refreshing && inbox.state.loaded
                    Tab.Activity -> activity.state.refreshing && activity.state.loaded
                    Tab.Notes -> notes.state.refreshing && notes.state.loaded
                    Tab.Explore -> explore.state.refreshing && explore.state.loaded
                },
                onRefresh = when (tab) {
                    Tab.Inbox -> inbox.refresh
                    Tab.Activity -> activity.refresh
                    Tab.Notes -> notes.refresh
                    Tab.Explore -> explore.refresh
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                if (tab == Tab.Notes) {
                    NotesScreen(
                        state = notes.state,
                        demo = demo,
                        onFolder = notes.folder,
                        onOpen = notes.open,
                        onLoadMore = notes.loadMore,
                        onRetry = notes.refresh,
                    )
                } else if (tab == Tab.Activity) {
                    ActivityScreen(
                        state = activity.state,
                        demo = demo,
                        onOpen = activity.open,
                        onOpenUser = activity.openUser,
                        onRemove = activity.remove,
                        onClear = activity.clear,
                        onRetry = activity.refresh,
                    )
                } else if (tab == Tab.Inbox) {
                    InboxScreen(
                        state = inbox.state,
                        selection = selection,
                        demo = demo,
                        onOpen = inbox.open,
                        onToggleSelect = { id -> selection = if (id in selection) selection - id else selection + id },
                        onLoadMore = inbox.loadMore,
                        onRetry = inbox.refresh,
                    )
                } else {
                    ExploreScreen(explore, demo)
                }
            }
        }
        if (inbox.state.removing || activity.state.removing || quickWorking > 0) {
            Box(Modifier.padding(padding).fillMaxWidth()) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.inbox_clear_title)) },
            text = { Text(stringResource(R.string.inbox_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    inbox.clearAll()
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The tabs at the bottom: lower than Material's bar, for more room above. */
@Composable
private fun BottomBar(selected: Tab, counts: Counts, onSelect: (Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(BottomBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (tab in Tab.entries) {
                val on = tab == selected
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .padding(horizontal = 18.dp, vertical = 3.dp),
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        ) { TabIcon(tab, on, tab.badge(counts)) }
                    }
                    Text(
                        stringResource(tab.label),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** The tabs at the side, on tablets, unfolded foldables and phones held sideways. */
@Composable
private fun SideRail(selected: Tab, counts: Counts, onSelect: (Tab) -> Unit) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Spacer(Modifier.weight(1f))
        for (tab in Tab.entries) {
            NavigationRailItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { TabIcon(tab, tab == selected, tab.badge(counts)) },
                label = { Text(stringResource(tab.label), maxLines = 1, softWrap = false, overflow = TextOverflow.Visible) },
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun TabIcon(tab: Tab, selected: Boolean, count: Int) {
    BadgedBox(badge = {
        if (count > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Text(badgeText(count)) }
        }
    }) {
        Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null)
    }
}

private fun badgeText(count: Int): String = if (count > 999) "999+" else count.toString()

/** Uploads under way, for the top bar: how many, and how far the current one is (null: not sending). */
data class UploadIndicator(val active: Int = 0, val progress: Float? = null)

/** Upload, one tap from every tab. While files go up it shows how far, and opens the list instead. */
@Composable
private fun UploadButton(uploads: UploadIndicator, onClick: () -> Unit) {
    val description = if (uploads.active > 0) {
        pluralStringResource(R.plurals.uploads_running, uploads.active, uploads.active)
    } else {
        stringResource(R.string.upload_title)
    }
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Box(contentAlignment = Alignment.Center) {
            if (uploads.active > 0) {
                val progress = uploads.progress
                if (progress != null) {
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(34.dp), strokeWidth = 2.5.dp)
                } else {
                    CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 2.5.dp)
                }
            }
            Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(if (uploads.active > 0) 18.dp else 24.dp))
        }
    }
}

/** The avatar in the top bar; opens the account sheet. */
@Composable
private fun AccountButton(
    user: LoggedInUser,
    onOpenSettings: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenSaved: () -> Unit,
    onLogout: () -> Unit,
    onOpenUploads: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val description = stringResource(R.string.account_menu)

    IconButton(
        onClick = { open = true },
        modifier = Modifier
            .padding(end = 4.dp)
            .size(48.dp)
            .semantics { contentDescription = description },
    ) {
        Avatar(user.avatarUrl, user.displayName, 36.dp)
    }
    if (open) {
        AccountSheet(
            user = user,
            onDismiss = { open = false },
            onOpenSettings = {
                open = false
                onOpenSettings()
            },
            onOpenProfile = {
                open = false
                onOpenProfile(user.username)
            },
            onOpenSaved = {
                open = false
                onOpenSaved()
            },
            onLogout = {
                open = false
                confirmLogout = true
            },
            onOpenUploads = {
                open = false
                onOpenUploads()
            },
        )
    }
    if (confirmLogout) LogoutDialog(onConfirm = onLogout, onDismiss = { confirmLogout = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(
    user: LoggedInUser,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSaved: () -> Unit,
    onLogout: () -> Unit,
    onOpenUploads: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(user.avatarUrl, user.displayName, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(user.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "@" + user.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            SheetItem(Icons.Outlined.Person, stringResource(R.string.menu_profile), onClick = onOpenProfile)
            SheetItem(Icons.AutoMirrored.Outlined.ViewList, stringResource(R.string.uploads_title), onClick = onOpenUploads)
            SheetItem(Icons.Outlined.BookmarkBorder, stringResource(R.string.menu_saved), onClick = onOpenSaved)
            SheetItem(Icons.Outlined.Settings, stringResource(R.string.menu_settings), onClick = onOpenSettings)
            SheetItem(
                Icons.AutoMirrored.Outlined.Logout,
                stringResource(R.string.menu_logout),
                color = MaterialTheme.colorScheme.error,
                onClick = onLogout,
            )
        }
    }
}

@Composable
private fun SheetItem(icon: ImageVector, label: String, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}

@Composable
fun LogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_logout)) },
        text = { Text(stringResource(R.string.settings_logout_hint)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(stringResource(R.string.menu_logout)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A tab last shown this long ago loads again when it comes back. */
private const val TAB_STALE_MS = 2 * 60 * 1000L

/** Away from the app this long, and its pages load again on return. */
private const val AWAY_REFRESH_MS = 3 * 60 * 1000L
