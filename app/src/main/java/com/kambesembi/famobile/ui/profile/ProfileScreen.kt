package com.kambesembi.famobile.ui.profile

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.ProfileRepository
import com.kambesembi.famobile.data.model.GalleryKind
import com.kambesembi.famobile.data.model.JournalSummary
import com.kambesembi.famobile.data.model.ProfileResult
import com.kambesembi.famobile.data.model.SavedArtist
import com.kambesembi.famobile.data.model.SubmissionPreview
import com.kambesembi.famobile.data.model.UserProfile
import com.kambesembi.famobile.ui.components.Avatar
import com.kambesembi.famobile.ui.components.Bubble
import com.kambesembi.famobile.ui.components.FurryLoader
import com.kambesembi.famobile.ui.components.HtmlText
import com.kambesembi.famobile.ui.components.PausedPaging
import com.kambesembi.famobile.ui.components.PullRefresh
import com.kambesembi.famobile.ui.components.QuickCard
import com.kambesembi.famobile.ui.components.ReplySheet
import com.kambesembi.famobile.ui.components.TopBarHeight
import com.kambesembi.famobile.ui.components.gridThumbSize
import com.kambesembi.famobile.ui.components.rememberAutoLoad
import com.kambesembi.famobile.ui.components.submissionGridCells
import com.kambesembi.famobile.ui.components.visible
import com.kambesembi.famobile.ui.util.openExternally
import com.kambesembi.famobile.ui.util.shareLink
import com.kambesembi.famobile.ui.viewer.CollapsibleSection
import com.kambesembi.famobile.ui.viewer.CommentItem
import com.kambesembi.famobile.ui.viewer.CommentsTitle
import com.kambesembi.famobile.ui.viewer.Notice
import com.kambesembi.famobile.ui.viewer.RatingLabel
import com.kambesembi.famobile.ui.viewer.Section
import com.kambesembi.famobile.ui.viewer.SectionTitle
import com.kambesembi.famobile.ui.viewer.relativeTime
import java.io.IOException
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What a profile screen opens elsewhere. */
class ProfileActions(
    val openSubmissions: (ids: List<Long>, start: Long) -> Unit,
    /** Any link in profile text, shouts or journals: users and submissions open in the app. */
    val openLink: (url: String) -> Unit,
    val writeNote: (name: String) -> Unit,
    /** Opens a journal with its comments in the app. */
    val openJournal: (id: Long) -> Unit,
    val saved: Flow<List<SavedArtist>>,
    val toggleSaved: (SavedArtist) -> Unit,
    val back: () -> Unit,
)

/** Pages of a list that grows as the user scrolls: a gallery, scraps, favorites or journals. */
class Paged<T>(
    private val model: ViewModel,
    private val key: (T) -> Any,
    private val fetch: suspend (path: String?) -> Pair<List<T>, String?>,
) {
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var nextPath by mutableStateOf<String?>(null)
        private set
    var started by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Goes up each time a first page replaces the list (the first load, a refresh). */
    var generation by mutableStateOf(0)
        private set

    val finished: Boolean get() = started && nextPath == null

    fun loadMore() {
        if (loading || finished || error != null) return
        load(if (started) nextPath else null, reset = false)
    }

    fun retry() {
        error = null
        load(if (started) nextPath else null, reset = false)
    }

    fun refresh() = load(null, reset = true)

    private fun load(path: String?, reset: Boolean) {
        if (loading) return
        loading = true
        error = null
        model.viewModelScope.launch {
            try {
                val (page, next) = fetch(path)
                val fresh = reset || !started
                items = (if (reset) page else items + page).distinctBy(key)
                nextPath = next
                started = true
                if (fresh) generation++
            } catch (e: IOException) {
                error = e.message ?: "Network error"
            } finally {
                loading = false
            }
        }
    }
}

class ProfileModel(val name: String, private val repo: ProfileRepository) : ViewModel() {
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var watchBusy by mutableStateOf(false)
        private set

    val gallery = gallery(GalleryKind.Gallery)
    val scraps = gallery(GalleryKind.Scraps)
    val favorites = gallery(GalleryKind.Favorites)
    val journals = Paged<JournalSummary>(this, { it.id }) { path -> repo.journals(name, path).let { it.items to it.nextPath } }

    /** Posts a shout; the profile in the repository shows it once FA does. */
    suspend fun shout(profile: UserProfile, text: String) = repo.shout(profile, text)

    private fun gallery(kind: GalleryKind) =
        Paged<SubmissionPreview>(this, { it.id }) { path -> repo.gallery(name, kind, path).let { it.items to it.nextPath } }

    fun load(force: Boolean = false) {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                repo.load(name, force)
            } catch (e: IOException) {
                error = e.message ?: "Network error"
            } finally {
                loading = false
            }
        }
    }

    fun toggleWatch(profile: UserProfile) {
        if (watchBusy) return
        watchBusy = true
        viewModelScope.launch {
            try {
                repo.toggleWatch(profile)
            } catch (e: IOException) {
                error = e.message ?: "Network error"
            } finally {
                watchBusy = false
            }
        }
    }
}

private enum class ProfileTab(val label: Int) {
    Home(R.string.profile_tab_home),
    Gallery(R.string.profile_tab_gallery),
    Scraps(R.string.profile_tab_scraps),
    Favorites(R.string.profile_tab_favorites),
    Journals(R.string.profile_tab_journals),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(name: String, repo: ProfileRepository, actions: ProfileActions, shout: Long? = null) {
    val model: ProfileModel = viewModel(key = "profile-${name.lowercase()}") { ProfileModel(name, repo) }
    val profiles by repo.profiles.collectAsStateWithLifecycle()
    val result = profiles[name.lowercase()]
    val profile = (result as? ProfileResult.Loaded)?.profile
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val current = ProfileTab.entries[tab]
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val url = "https://www.furaffinity.net/user/${name.lowercase()}/"
    val tabState = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()

    LaunchedEffect(name) { if (result == null) model.load() }
    // Errors after the profile is on screen (watching, paging) are a snackbar; before, a notice.
    LaunchedEffect(model.error, profile != null) {
        val message = model.error ?: return@LaunchedEffect
        if (profile != null) {
            snackbar.showSnackbar(message)
            model.error = null
        }
    }
    LaunchedEffect(current) {
        when (current) {
            ProfileTab.Home -> Unit
            ProfileTab.Gallery -> model.gallery.loadMore()
            ProfileTab.Scraps -> model.scraps.loadMore()
            ProfileTab.Favorites -> model.favorites.loadMore()
            ProfileTab.Journals -> model.journals.loadMore()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    expandedHeight = TopBarHeight,
                    title = {
                        Text(
                            profile?.user?.displayName ?: name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = actions.back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        val savedList by actions.saved.collectAsStateWithLifecycle(emptyList())
                        if (profile != null) {
                            val saved = savedList.any { it.name == profile.user.name }
                            val savedText = stringResource(R.string.saved_added)
                            val removedText = stringResource(R.string.saved_removed)
                            IconButton(onClick = {
                                actions.toggleSaved(SavedArtist(profile.user.name, profile.user.displayName, profile.user.avatarUrl))
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    snackbar.showSnackbar(if (saved) removedText else savedText)
                                }
                            }) {
                                Icon(
                                    if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = stringResource(if (saved) R.string.profile_unsave else R.string.profile_save),
                                    tint = if (saved) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                )
                            }
                        }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_share)) },
                                    leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        context.shareLink(profile?.user?.displayName ?: name, url)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.open_in_browser)) },
                                    leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        context.openExternally(url)
                                    },
                                )
                            }
                        }
                    },
                )
                if (profile != null) {
                    PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                        ProfileTab.entries.forEachIndexed { index, entry ->
                            Tab(
                                selected = tab == index,
                                onClick = { tab = index },
                                text = { Text(stringResource(entry.label)) },
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                result is ProfileResult.Unavailable -> Notice(result.message, url) { model.load(force = true) }
                profile == null && model.error != null -> Notice(model.error.orEmpty(), url) { model.load(force = true) }
                profile == null -> FurryLoader()
                else -> {
                    val paged = when (current) {
                        ProfileTab.Gallery -> model.gallery
                        ProfileTab.Scraps -> model.scraps
                        ProfileTab.Favorites -> model.favorites
                        else -> null
                    }
                    PullRefresh(
                        isRefreshing = when (current) {
                            ProfileTab.Home -> model.loading
                            ProfileTab.Journals -> model.journals.loading && model.journals.started
                            else -> paged?.let { it.loading && it.started } == true
                        },
                        onRefresh = {
                            when (current) {
                                ProfileTab.Home -> model.load(force = true)
                                ProfileTab.Journals -> model.journals.refresh()
                                else -> paged?.refresh()
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        tabState.SaveableStateProvider(current.name) {
                            when (current) {
                                ProfileTab.Home -> Home(profile, model, actions, snackbar, shout)
                                ProfileTab.Journals -> Journals(model.journals, actions)
                                else -> Gallery(paged!!, actions)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Home(
    profile: UserProfile,
    model: ProfileModel,
    actions: ProfileActions,
    snackbar: SnackbarHostState,
    shout: Long? = null,
) {
    val format = remember { NumberFormat.getIntegerInstance() }
    val listState = rememberLazyListState()
    // Opened for a shout: scroll down until it (or, if it's gone, the shouts) is in view, once.
    var scrolledTo by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(shout, profile.shouts.size) {
        if (shout == null || scrolledTo) return@LaunchedEffect
        val wanted = if (profile.shouts.any { it.id == shout }) "s$shout" else "shouts-title"
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        repeat(40) {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == wanted }?.let { item ->
                listState.animateScrollToItem(item.index)
                scrolledTo = true
                return@LaunchedEffect
            }
            if (!listState.canScrollForward) return@LaunchedEffect
            listState.scrollBy(listState.layoutInfo.viewportSize.height * 0.8f)
            withFrameNanos { }
        }
    }
    var shouting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val posted = stringResource(R.string.shout_posted)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") { Header(profile, model.watchBusy, { model.toggleWatch(profile) }, actions) }

        if (profile.profileHtml.isNotBlank()) {
            item(key = "profile") {
                Section(stringResource(R.string.profile_section_profile)) {
                    HtmlText(profile.profileHtml, onLink = actions.openLink, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (profile.stats.isNotEmpty()) {
            item(key = "stats") {
                Section(stringResource(R.string.profile_section_stats)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        maxItemsInEachRow = 3,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        profile.stats.forEach { (label, value) ->
                            Column(Modifier.weight(1f)) {
                                Text(format.format(value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        if (profile.info.isNotEmpty()) {
            item(key = "info") {
                CollapsibleSection(
                    stringResource(R.string.profile_section_info),
                    key = "profile-info-${profile.user.name}",
                    summary = profile.info.take(2).joinToString(" · ") { (label, value) -> "$label: $value" },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        profile.info.forEach { (label, value) ->
                            Row {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        if (profile.shouts.isNotEmpty() || profile.shoutForm != null) {
            item(key = "shouts-title") {
                CommentsTitle(
                    stringResource(R.string.profile_shouts),
                    onWrite = profile.shoutForm?.let { { shouting = true } },
                    writeLabel = R.string.action_shout,
                )
            }
            items(profile.shouts, key = { "s${it.id}" }) { item ->
                if (item.id == shout) {
                    // The shout the notification was about, marked.
                    Box(
                        Modifier
                            .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large),
                    ) { CommentItem(item, actions.openLink) }
                } else {
                    CommentItem(item, actions.openLink)
                }
            }
        }
    }

    if (shouting) {
        ReplySheet(
            title = stringResource(R.string.shout_title, profile.user.displayName),
            hint = stringResource(R.string.shout_hint),
            quote = null,
            maxLength = profile.shoutForm?.maxLength,
            draftKey = "shout-${profile.user.name}",
            send = { text -> model.shout(profile, text) },
            onPosted = {
                shouting = false
                scope.launch { snackbar.showSnackbar(posted) }
            },
            onDismiss = { shouting = false },
        )
    }
}

@Composable
private fun Header(profile: UserProfile, watchBusy: Boolean, onWatch: () -> Unit, actions: ProfileActions) {
    val format = remember { NumberFormat.getIntegerInstance() }
    Bubble(Modifier.fillMaxWidth()) {
        val banner = profile.banner
        if (banner != null) {
            // FA's banners are 1850 x 300 at most: the box has that shape from the start, so nothing
            // moves when the image arrives.
            AsyncImage(
                banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(BANNER_RATIO)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Spacer(Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(profile.user.avatarUrl, profile.user.displayName, 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.user.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull("@" + profile.user.name, profile.title).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                profile.registeredAt?.let {
                    val date = remember(it) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it * 1000)) }
                    Text(
                        stringResource(R.string.profile_registered, date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val counts = listOfNotNull(
            profile.watchedBy?.let { stringResource(R.string.profile_watched_by, format.format(it)) },
            profile.watching?.let { stringResource(R.string.profile_watching, format.format(it)) },
        )
        if (counts.isNotEmpty()) {
            Spacer(Modifier.size(10.dp))
            Text(counts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        val watch = profile.watch
        if (watch != null || profile.notePath != null) {
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (watch != null) {
                    val label = stringResource(if (watch.watching) R.string.profile_unwatch else R.string.profile_watch)
                    val icon = if (watch.watching) Icons.Outlined.PersonRemove else Icons.Outlined.PersonAdd
                    val content: @Composable () -> Unit = {
                        if (watchBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(label)
                    }
                    // Watching is the quiet state; not yet watching gets the filled call to action.
                    if (watch.watching) {
                        OutlinedButton(onClick = onWatch, enabled = !watchBusy) { content() }
                    } else {
                        Button(onClick = onWatch, enabled = !watchBusy) { content() }
                    }
                }
                if (profile.notePath != null) OutlinedButton(onClick = { actions.writeNote(profile.user.name) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.profile_note))
                }
            }
        }
    }
}

@Composable
private fun Gallery(paged: Paged<SubmissionPreview>, actions: ProfileActions) {
    if (!paged.started) {
        FirstPage(paged)
        return
    }
    val gridState = rememberLazyStaggeredGridState()
    ToTopOnNewList(paged.generation) { gridState.scrollToItem(0) }
    val total = paged.items.size
    val thumbSize = gridThumbSize()
    val shown = paged.items.visible()
    val autoLoad = rememberAutoLoad(paged.generation, gridState, total, shown.size, paged::loadMore)

    LazyVerticalStaggeredGrid(
        columns = submissionGridCells(),
        state = gridState,
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalItemSpacing = 5.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(shown.size, key = { shown[it].id }) { index ->
            val preview = shown[index]
            QuickCard(preview, thumbSize, onOpen = { actions.openSubmissions(shown.map { it.id }, preview.id) })
        }
        item(key = "footer", span = StaggeredGridItemSpan.FullLine) {
            if (autoLoad.paused && !paged.finished && !paged.loading && paged.error == null) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    PausedPaging(total - shown.size, autoLoad::resume)
                }
            } else {
                ListFooter(paged, empty = R.string.gallery_empty)
            }
        }
    }
}

@Composable
private fun Journals(paged: Paged<JournalSummary>, actions: ProfileActions) {
    if (!paged.started) {
        FirstPage(paged)
        return
    }
    val listState = rememberLazyListState()
    ToTopOnNewList(paged.generation) { listState.scrollToItem(0) }
    val loadMore by rememberUpdatedState(paged::loadMore)
    val total = paged.items.size

    LaunchedEffect(listState, total) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= listState.layoutInfo.totalItemsCount - 3) loadMore() }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(paged.items, key = { it.id }) { journal -> JournalItem(journal, actions) }
        item(key = "footer") { ListFooter(paged, empty = R.string.journals_empty) }
    }
}

/** A journal in full, folded to a few lines when long; the title and the comments button open its page. */
@Composable
private fun JournalItem(journal: JournalSummary, actions: ProfileActions) {
    var expanded by rememberSaveable(journal.id) { mutableStateOf(false) }
    val long = journal.bodyHtml.length > 600
    Bubble(Modifier.fillMaxWidth()) {
        Text(
            journal.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { actions.openJournal(journal.id) },
        )
        Spacer(Modifier.size(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RatingLabel(journal.rating)
            journal.postedAt?.let {
                Text(relativeTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.size(10.dp))
        Box(
            Modifier
                .animateContentSize()
                .then(if (long && !expanded) Modifier.heightIn(max = 180.dp).clipToBounds() else Modifier),
        ) {
            HtmlText(journal.bodyHtml, onLink = actions.openLink, style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (long) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) R.string.journal_less else R.string.journal_more))
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { actions.openJournal(journal.id) }) {
                Text((journal.commentCount ?: 0).let { pluralStringResource(R.plurals.journal_comments, it, it) })
            }
        }
    }
}

/**
 * Scrolls to the top when a refresh replaced the list. Coming back from the viewer composes the
 * list again with the same generation, so the place is kept then.
 */
@Composable
private fun ToTopOnNewList(generation: Int, scroll: suspend () -> Unit) {
    var shown by rememberSaveable { mutableIntStateOf(generation) }
    LaunchedEffect(generation) {
        if (generation != shown) {
            scroll()
            shown = generation
        }
    }
}

/** Before the first page: a loader, or the error with a retry. Pull-to-refresh needs a list to drag. */
@Composable
private fun <T> FirstPage(paged: Paged<T>) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillParentMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                val error = paged.error
                if (error != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(8.dp))
                        Button(onClick = paged::retry) { Text(stringResource(R.string.retry)) }
                    }
                } else {
                    FurryLoader()
                }
            }
        }
    }
}

@Composable
private fun <T> ListFooter(paged: Paged<T>, empty: Int) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            paged.loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            paged.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(paged.error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                Button(onClick = paged::retry) { Text(stringResource(R.string.retry)) }
            }
            paged.finished && paged.items.isEmpty() -> Text(
                stringResource(empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            paged.finished -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DoneAll, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.gallery_end),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** FA's profile banner shape: 1850 x 300. */
private const val BANNER_RATIO = 1850f / 300f
