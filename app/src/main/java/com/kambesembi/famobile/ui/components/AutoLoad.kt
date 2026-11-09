package com.kambesembi.famobile.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kambesembi.famobile.R
import kotlinx.coroutines.delay

/**
 * Infinite scroll for a grid the content filter thins out. A page that adds nothing visible makes the
 * next one wait a little longer, and after [MAX_EMPTY_PAGES] of them in a row it waits for a tap: a
 * search the blocklist empties would otherwise page through FA until FA refuses (503).
 */
class AutoLoad internal constructor() {
    internal var emptyPages by mutableIntStateOf(0)
    internal var lastTotal = -1
    internal var lastShown = 0

    /** Waiting for a tap on "Load more". */
    val paused: Boolean get() = emptyPages >= MAX_EMPTY_PAGES

    internal var load: () -> Unit = {}

    /** The tap: go on paging. */
    fun resume() {
        emptyPages = 0
        load()
    }

    private companion object {
        const val MAX_EMPTY_PAGES = 3
    }
}

/**
 * Calls [loadMore] near the end of the grid. [total] counts the loaded items, [shown] those the filter
 * lets through; [key] changes with a new list (another search, a refresh).
 */
@Composable
fun rememberAutoLoad(key: Any?, gridState: LazyStaggeredGridState, total: Int, shown: Int, loadMore: () -> Unit): AutoLoad {
    val auto = remember(key) { AutoLoad() }
    val more by rememberUpdatedState(loadMore)
    auto.load = { more() }
    LaunchedEffect(auto, total) {
        if (auto.lastTotal in 0 until total) {
            if (shown <= auto.lastShown) auto.emptyPages++ else auto.emptyPages = 0
        } else if (total < auto.lastTotal) {
            auto.emptyPages = 0
        }
        auto.lastTotal = total
        auto.lastShown = shown
    }
    LaunchedEffect(auto, gridState, total, auto.paused) {
        if (auto.paused) return@LaunchedEffect
        if (auto.emptyPages > 0) delay(EMPTY_PAGE_PAUSE_MS * auto.emptyPages)
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= gridState.layoutInfo.totalItemsCount - 12) more() }
    }
    return auto
}

private const val EMPTY_PAGE_PAUSE_MS = 1200L

/** The grid's footer while [AutoLoad.paused]: how much the filter hid, and a button to go on. */
@Composable
fun PausedPaging(hidden: Int, onResume: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            pluralStringResource(R.plurals.grid_hidden, hidden, hidden),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        OutlinedButton(onClick = onResume) { Text(stringResource(R.string.grid_load_more)) }
    }
}
