package com.kambesembi.famobile.ui.inbox

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.InboxState
import com.kambesembi.famobile.data.model.Rating
import com.kambesembi.famobile.data.model.SubmissionPreview
import com.kambesembi.famobile.ui.components.Bubble
import com.kambesembi.famobile.ui.components.FurryLoader
import com.kambesembi.famobile.ui.components.LocalContentFilter
import com.kambesembi.famobile.ui.components.gridThumbSize
import com.kambesembi.famobile.ui.components.rememberFaved
import com.kambesembi.famobile.ui.components.submissionGridCells

/** The submission inbox as a grid: tap opens the viewer, long-press starts selecting. */
@Composable
fun InboxScreen(
    state: InboxState,
    selection: Set<Long>,
    demo: Boolean,
    onOpen: (Long) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        !state.loaded && state.error == null -> FurryLoader()
        !state.loaded -> Message(stringResource(R.string.error_title), state.error, onRetry)
        state.items.isEmpty() -> Message(stringResource(R.string.inbox_empty), stringResource(R.string.inbox_empty_hint), null)
        else -> Grid(state, selection, demo, onOpen, onToggleSelect, onLoadMore)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Grid(
    state: InboxState,
    selection: Set<Long>,
    demo: Boolean,
    onOpen: (Long) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyStaggeredGridState()
    val loadMore by rememberUpdatedState(onLoadMore)
    val total = state.items.size

    // Infinite scroll: ask for the next page while a dozen thumbnails are still ahead.
    LaunchedEffect(gridState, total) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= gridState.layoutInfo.totalItemsCount - 12) loadMore() }
    }

    // A thumbnail size close to the column width in pixels.
    val thumbSize = gridThumbSize()
    val selecting = selection.isNotEmpty()
    val filter = LocalContentFilter.current

    LazyVerticalStaggeredGrid(
        columns = submissionGridCells(),
        state = gridState,
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalItemSpacing = 5.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (demo) {
            item(span = StaggeredGridItemSpan.FullLine) { DemoNote() }
        }
        for (entry in state.items) {
            val preview = entry.preview
            if (filter.hides(preview)) continue
            item(key = preview.id) {
                SubmissionCard(
                    preview = preview,
                    thumbSize = thumbSize,
                    selected = preview.id in selection,
                    faved = rememberFaved(preview.id),
                    modifier = Modifier.combinedClickable(
                        onClick = { if (selecting) onToggleSelect(preview.id) else onOpen(preview.id) },
                        onLongClick = { onToggleSelect(preview.id) },
                    ),
                )
            }
        }
        item(key = "footer", span = StaggeredGridItemSpan.FullLine) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                when {
                    state.loadingMore -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    state.error != null -> Button(onClick = loadMore) { Text(stringResource(R.string.retry)) }
                    state.nextPath == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.DoneAll, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.inbox_end),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Just the thumbnail, framed like on FA: grey for general, blue for mature, red for adult. Title and
 * artist are in the viewer. A selected card gets a thicker amber frame and a check mark.
 */
@Composable
fun SubmissionCard(
    preview: SubmissionPreview,
    thumbSize: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    /** In the favorites already: a small heart in the corner. */
    faved: Boolean = false,
) {
    val shape = MaterialTheme.shapes.medium
    Box(
        Modifier
            .clip(shape)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else frameColor(preview.rating),
                shape,
            )
            .then(modifier),
    ) {
        AsyncImage(
            model = preview.thumbnail(thumbSize),
            contentDescription = preview.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio((preview.aspectRatio ?: 1f).coerceIn(0.55f, 1.8f))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        if (faved && !selected) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = stringResource(R.string.quick_faved),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                    .padding(4.dp)
                    .size(14.dp),
            )
        }
        if (selected) {
            Box(Modifier.matchParentSize().background(Color(0x33000000)))
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

/** The thin frame around a thumbnail: the rating colour, toned down so it doesn't compete with the art. */
@Composable
private fun frameColor(rating: Rating): Color = when (rating) {
    Rating.General -> ratingColor(rating).copy(alpha = 0.45f)
    else -> ratingColor(rating).copy(alpha = 0.8f)
}

/** FA's rating colours: blue for mature, red for adult, a quiet grey for general. */
@Composable
fun ratingColor(rating: Rating): Color = when (rating) {
    Rating.General -> if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF9A9DA4) else Color(0xFFB4B7BE)
    Rating.Mature -> Color(0xFF3D7BD9)
    Rating.Adult -> Color(0xFFD94848)
}

@Composable
private fun Message(title: String, body: String?, onRetry: (() -> Unit)?) {
    // A list, even for one bubble: pull-to-refresh needs something to drag.
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillParentMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                MessageBubble(title, body, onRetry)
            }
        }
    }
}

@Composable
private fun MessageBubble(title: String, body: String?, onRetry: (() -> Unit)?) {
    run {
        Bubble(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (body != null) {
                Spacer(Modifier.size(6.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onRetry != null) {
                Spacer(Modifier.size(12.dp))
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

@Composable
private fun DemoNote() {
    Text(
        stringResource(R.string.demo_mode),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onTertiary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
