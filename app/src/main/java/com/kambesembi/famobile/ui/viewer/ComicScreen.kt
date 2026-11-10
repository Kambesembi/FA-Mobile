package com.kambesembi.famobile.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.AppSettings
import com.kambesembi.famobile.data.ComicRepository
import com.kambesembi.famobile.data.model.Submission
import com.kambesembi.famobile.data.parse.ComicParser
import com.kambesembi.famobile.ui.components.FurryLoader
import com.kambesembi.famobile.ui.components.MediaWidth
import com.kambesembi.famobile.ui.components.TopBarHeight
import com.kambesembi.famobile.ui.components.readableSide
import kotlinx.coroutines.launch

/**
 * A whole series on one screen: every page under the next (or side by side, one per swipe), in the
 * order the artist linked them. Tap a page to zoom; its label opens the page's submission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicScreen(
    startId: Long,
    repo: ComicRepository,
    settings: AppSettings,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val state by repo.state.collectAsStateWithLifecycle()
    val horizontal by settings.comicHorizontal.collectAsStateWithLifecycle(false)
    val scope = rememberCoroutineScope()
    var zoom by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(startId) { repo.open(startId) }
    // Leaving the reader (the arrow or the back gesture) ends the loading; a zoomed page closes first.
    BackHandler(enabled = zoom == null, onBack = onBack)

    val comic = state?.takeIf { it.startId == startId }
    val pages = comic?.pages.orEmpty()
    val start = pages.firstOrNull { it.id == startId }
    val title = start?.let { ComicParser.numberedTitle(it.title)?.base ?: it.title }.orEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                expandedHeight = TopBarHeight,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                comic?.loading == true -> pluralStringResource(R.plurals.comic_loading, pages.size, pages.size)
                                else -> pluralStringResource(R.plurals.comic_pages, pages.size, pages.size)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { settings.setComicHorizontal(!horizontal) } }) {
                        Icon(
                            if (horizontal) Icons.Outlined.ViewDay else Icons.Outlined.ViewCarousel,
                            contentDescription = stringResource(if (horizontal) R.string.comic_vertical else R.string.comic_horizontal),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                pages.isEmpty() && comic?.error != null -> Message(stringResource(R.string.comic_failed, comic.error), onRetry = { repo.open(startId) })
                pages.isEmpty() -> FurryLoader()
                horizontal -> Pages(pages, startId, onZoom = { zoom = it }, onOpen = onOpen)
                else -> Strip(pages, startId, comic, onZoom = { zoom = it }, onOpen = onOpen, onRetry = { repo.open(startId) })
            }
        }
    }

    pages.firstOrNull { it.id == zoom }?.let { page ->
        page.imageUrl?.let { url -> ZoomImage(url, page.previewUrl, page.title) { zoom = null } }
    }
}

/** Top to bottom, like a web comic. New pages above keep the one being read in place. */
@Composable
private fun Strip(
    pages: List<Submission>,
    startId: Long,
    comic: com.kambesembi.famobile.data.ComicState?,
    onZoom: (Long) -> Unit,
    onOpen: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    val list = rememberLazyListState()
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(start = readableSide(6.dp, MediaWidth), end = readableSide(6.dp, MediaWidth), bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(pages, key = { _, p -> p.id }) { i, page ->
            Column {
                PageLabel(i, pages.size, page, onOpen)
                PageImage(page, fit = false, onZoom = onZoom)
            }
        }
        item(key = "end") {
            when {
                comic?.loading == true -> Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(pluralStringResource(R.plurals.comic_loading, pages.size, pages.size), style = MaterialTheme.typography.bodyMedium)
                }
                comic?.error != null -> Message(stringResource(R.string.comic_failed, comic.error), onRetry)
                comic?.alone == true -> Message(stringResource(R.string.comic_alone), null)
                else -> Text(
                    stringResource(R.string.comic_end),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
    // Start at the page the reader came from, once it's there.
    var placed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pages.size) {
        if (!placed) {
            val i = pages.indexOfFirst { it.id == startId }
            if (i > 0) list.scrollToItem(i)
            if (i >= 0) placed = true
        }
    }
}

/** One page per swipe, each fitted to the screen. */
@Composable
private fun Pages(pages: List<Submission>, startId: Long, onZoom: (Long) -> Unit, onOpen: (Long) -> Unit) {
    val initial = remember { pages.indexOfFirst { it.id == startId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = initial) { pages.size }
    // Pages loaded in front: stay on the same page.
    val currentId by remember { derivedStateOf { pages.getOrNull(pager.currentPage)?.id } }
    LaunchedEffect(pages.size) {
        val i = pages.indexOfFirst { it.id == currentId }
        if (i >= 0 && i != pager.currentPage) pager.scrollToPage(i)
    }
    HorizontalPager(pager, key = { pages[it].id }, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { i ->
        val page = pages[i]
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            PageLabel(i, pages.size, page, onOpen)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PageImage(page, fit = true, onZoom = onZoom)
            }
        }
    }
}

@Composable
private fun PageLabel(index: Int, total: Int, page: Submission, onOpen: (Long) -> Unit) {
    Text(
        stringResource(R.string.comic_page_label, index + 1, total, page.title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(page.id) }
            .padding(horizontal = 4.dp, vertical = 10.dp),
    )
}

@Composable
private fun PageImage(page: Submission, fit: Boolean, onZoom: (Long) -> Unit) {
    val url = page.imageUrl ?: page.previewUrl ?: return
    val ratio = page.aspectRatio
    val shape = MaterialTheme.shapes.medium
    val base = Modifier
        .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
        .clickable { onZoom(page.id) }
    if (ratio != null) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val width = if (fit) minOf(maxWidth, maxHeight * ratio) else maxWidth
            ProgressiveImage(
                fullUrl = url,
                previewUrl = page.previewUrl,
                contentDescription = page.title,
                sized = true,
                modifier = base.size(width, width / ratio),
            )
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = page.title,
            contentScale = ContentScale.Fit,
            modifier = base.fillMaxWidth().heightIn(min = 120.dp),
        )
    }
}

@Composable
private fun Message(text: String, onRetry: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}
