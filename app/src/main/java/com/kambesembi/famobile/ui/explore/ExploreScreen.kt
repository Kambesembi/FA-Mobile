package com.kambesembi.famobile.ui.explore

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.ExploreState
import com.kambesembi.famobile.data.model.BrowseFilters
import com.kambesembi.famobile.data.model.SearchQuery
import com.kambesembi.famobile.ui.components.FurryLoader
import com.kambesembi.famobile.ui.components.PausedPaging
import com.kambesembi.famobile.ui.components.QuickCard
import com.kambesembi.famobile.ui.components.gridThumbSize
import com.kambesembi.famobile.ui.components.rememberAutoLoad
import com.kambesembi.famobile.ui.components.submissionGridCells
import com.kambesembi.famobile.ui.components.visible
import com.kambesembi.famobile.ui.viewer.SectionTitle
import java.text.NumberFormat

/** What the Explore tab needs: its state, the pins, and what it can ask for. */
class ExploreTab(
    val state: ExploreState,
    val refresh: () -> Unit,
    val loadMore: () -> Unit,
    val search: (query: SearchQuery, customized: Boolean) -> Unit,
    val browse: (BrowseFilters) -> Unit,
    val closeSearch: () -> Unit,
    val openSubmissions: (ids: List<Long>, start: Long) -> Unit,
    val openUser: (name: String) -> Unit,
)

@Composable
fun ExploreScreen(tab: ExploreTab, demo: Boolean) {
    val state = tab.state
    val search = state.search
    var text by rememberSaveable { mutableStateOf(search?.text.orEmpty()) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyStaggeredGridState()
    val loadMore by rememberUpdatedState(tab.loadMore)
    val total = state.items.size

    LaunchedEffect(search?.text) { if (search != null) text = search.text }
    // A new query starts at the top; coming back from the viewer keeps the place.
    var shownQuery by rememberSaveable { mutableStateOf("") }
    val queryKey = (search?.toString() ?: "browse") + state.browse
    LaunchedEffect(queryKey) {
        if (shownQuery.isNotEmpty() && shownQuery != queryKey) gridState.scrollToItem(0)
        shownQuery = queryKey
    }

    BackHandler(enabled = search != null) {
        text = ""
        tab.closeSearch()
    }

    fun submit() {
        val words = text.trim()
        if (words.isEmpty()) return
        // Only hide the keyboard: moving focus away here would hand a hardware Enter key's release
        // to the next button (the account menu) and click it.
        keyboard?.hide()
        // Keep the options of the search before, just with new words.
        val base = search?.copy(text = words) ?: SearchQuery(words, ratings = state.browse.ratings)
        tab.search(base, state.searchCustomized && search != null)
    }

    val thumbSize = gridThumbSize()
    val shown = state.items.visible()
    val autoLoad = rememberAutoLoad(queryKey, gridState, total, shown.size) { loadMore() }
    val customized = if (search != null) state.searchCustomized && !search.isDefault else state.browseCustomized && !state.browse.isDefault

    LazyVerticalStaggeredGrid(
        columns = submissionGridCells(),
        state = gridState,
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalItemSpacing = 5.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "search", span = StaggeredGridItemSpan.FullLine) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                if (search != null) {
                    IconButton(onClick = {
                        text = ""
                        focus.clearFocus()
                        tab.closeSearch()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.explore_back_to_browse))
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.explore_search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = { text = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.explore_clear_text))
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { filtersOpen = true }) {
                    BadgedBox(badge = { if (customized) Badge() }) {
                        Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.explore_filters))
                    }
                }
            }
        }

        if (demo) {
            item(key = "demo", span = StaggeredGridItemSpan.FullLine) {
                Text(
                    stringResource(R.string.demo_mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        item(key = "heading", span = StaggeredGridItemSpan.FullLine) {
            val format = remember { NumberFormat.getIntegerInstance() }
            val title = when {
                search == null -> stringResource(R.string.explore_newest)
                state.total != null -> stringResource(R.string.explore_results_count, format.format(state.total), search.text)
                else -> stringResource(R.string.explore_results, search.text)
            }
            Box(Modifier.padding(top = 6.dp)) { SectionTitle(title) }
        }

        items(shown.size, key = { shown[it].id }) { index ->
            val preview = shown[index]
            QuickCard(preview, thumbSize, onOpen = { tab.openSubmissions(shown.map { it.id }, preview.id) })
        }

        item(key = "footer", span = StaggeredGridItemSpan.FullLine) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                when {
                    state.refreshing && state.items.isEmpty() -> Box(Modifier.fillMaxWidth().height(520.dp)) { FurryLoader() }
                    state.loadingMore ->
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    autoLoad.paused && state.hasNext && state.error == null -> PausedPaging(total - shown.size, autoLoad::resume)
                    state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            state.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.size(8.dp))
                        Button(onClick = if (state.items.isEmpty()) tab.refresh else tab.loadMore) { Text(stringResource(R.string.retry)) }
                    }
                    state.loaded && state.items.isEmpty() -> Text(
                        stringResource(if (search != null) R.string.explore_no_results else R.string.gallery_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.loaded && !state.hasNext -> Row(verticalAlignment = Alignment.CenterVertically) {
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
    }

    if (filtersOpen) {
        if (search != null) {
            SearchFilterSheet(
                query = search,
                options = state.searchOptions,
                onApply = { tab.search(it.copy(text = text.trim().ifEmpty { search.text }), true) },
                onDismiss = { filtersOpen = false },
            )
        } else {
            BrowseFilterSheet(
                filters = state.browse,
                options = state.browseOptions,
                onApply = tab.browse,
                onDismiss = { filtersOpen = false },
            )
        }
    }
}
