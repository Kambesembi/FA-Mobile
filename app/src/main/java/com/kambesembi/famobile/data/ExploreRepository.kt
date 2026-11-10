package com.kambesembi.famobile.data

import com.kambesembi.famobile.data.model.BrowseFilters
import com.kambesembi.famobile.data.model.FilterOptions
import com.kambesembi.famobile.data.model.ResultsPage
import com.kambesembi.famobile.data.model.SearchQuery
import com.kambesembi.famobile.data.model.SubmissionPreview
import com.kambesembi.famobile.data.parse.ExploreParser
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ExploreState(
    val browse: BrowseFilters = BrowseFilters(),
    /** Null while browsing; the words and options while searching. */
    val search: SearchQuery? = null,
    /** True once the filters were changed in the app: until then FA's account defaults apply. */
    val browseCustomized: Boolean = false,
    val searchCustomized: Boolean = false,
    val items: List<SubmissionPreview> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = false,
    val total: Int? = null,
    /** FA's lists for the filter sheet, from the last browse and search page. */
    val browseOptions: FilterOptions = FilterOptions(),
    val searchOptions: FilterOptions = FilterOptions(),
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
) {
    val ids: List<Long> get() = items.map { it.id }
}

/**
 * The Explore tab: browsing the newest submissions, or search results. A first plain visit uses the
 * account's own defaults on FA (ratings, results per page); the page then says what those were, so
 * later pages and changed filters ask for exactly that.
 */
class ExploreRepository(private val fa: FaClient) {

    private val _state = MutableStateFlow(ExploreState())
    val state: StateFlow<ExploreState> = _state.asStateFlow()

    private val loading = Mutex()

    /** Bumped on every new query, so an answer for an older one is dropped. */
    private var generation = 0

    suspend fun browse(filters: BrowseFilters) {
        _state.update { it.copy(browse = filters, search = null, browseCustomized = true) }
        refresh()
    }

    suspend fun search(query: SearchQuery, customized: Boolean) {
        _state.update { it.copy(search = query, searchCustomized = customized) }
        refresh()
    }

    /** Back from search results to browsing. */
    suspend fun closeSearch() {
        _state.update { it.copy(search = null) }
        refresh()
    }

    suspend fun refresh() {
        val ticket = ++generation
        _state.update { it.copy(refreshing = true, error = null, items = if (it.loaded) it.items else emptyList()) }
        loading.withLock {
            try {
                val start = _state.value
                val page = load(start, 1)
                if (ticket != generation) return
                _state.update { apply(it, page, 1, reset = true) }
            } catch (e: IOException) {
                if (ticket == generation) _state.update { it.copy(refreshing = false, error = e.message ?: "Network error") }
            }
        }
    }

    suspend fun loadMore() {
        val current = _state.value
        if (!current.loaded || !current.hasNext || current.loadingMore || current.refreshing) return
        val ticket = generation
        _state.update { it.copy(loadingMore = true, error = null) }
        loading.withLock {
            try {
                val next = current.page + 1
                val page = load(_state.value, next)
                if (ticket != generation) {
                    // The query changed meanwhile: its own refresh shows the results, this page is dropped.
                    _state.update { it.copy(loadingMore = false) }
                    return
                }
                _state.update { apply(it, page, next, reset = false) }
            } catch (e: IOException) {
                _state.update { if (ticket == generation) it.copy(loadingMore = false, error = e.message ?: "Network error") else it.copy(loadingMore = false) }
            }
        }
    }

    private suspend fun load(state: ExploreState, page: Int): ResultsPage {
        val search = state.search
        return if (search == null) {
            val path = if (page == 1 && !state.browseCustomized) FIRST_BROWSE else state.browse.path(page, perPage(state.browseOptions))
            fa.read(path) { ExploreParser.browse(it) }
        } else {
            val path = if (page == 1 && !state.searchCustomized) {
                "/search/?q=" + URLEncoder.encode(search.text, "UTF-8")
            } else {
                search.path(page, perPage(state.searchOptions))
            }
            fa.read(path) { ExploreParser.search(it) }
        }
    }

    private fun apply(state: ExploreState, page: ResultsPage, number: Int, reset: Boolean): ExploreState {
        val searching = state.search != null
        // A first plain visit shows the account's defaults: keep them for the next pages.
        val browse = if (!searching && !state.browseCustomized && number == 1) state.browse.copy(ratings = page.options.checkedRatings) else state.browse
        val search = if (searching && !state.searchCustomized && number == 1) state.search?.copy(ratings = page.options.checkedRatings) else state.search
        return state.copy(
            browse = browse,
            search = search,
            items = (if (reset) page.items else state.items + page.items).distinctBy { it.id },
            page = number,
            hasNext = page.hasNext && page.items.isNotEmpty(),
            total = page.total,
            browseOptions = if (!searching && !page.options.isEmpty) page.options else state.browseOptions,
            searchOptions = if (searching && !page.options.isEmpty) page.options else state.searchOptions,
            loaded = true,
            refreshing = false,
            loadingMore = false,
            error = null,
        )
    }

    private fun perPage(options: FilterOptions) = options.perPage ?: DEFAULT_PER_PAGE

    fun clear() {
        generation++
        _state.value = ExploreState()
    }

    private companion object {
        const val FIRST_BROWSE = "/browse/"
        const val DEFAULT_PER_PAGE = 72
    }
}
