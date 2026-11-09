package com.kambesembi.famobile.data

import com.kambesembi.famobile.data.model.DatedPreview
import com.kambesembi.famobile.data.parse.InboxParser
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class InboxState(
    val items: List<DatedPreview> = emptyList(),
    /** Older page to load next; null once everything is loaded. */
    val nextPath: String? = null,
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    /** A removal is on its way to FA. */
    val removing: Boolean = false,
    val error: String? = null,
) {
    val ids: List<Long> get() = items.map { it.preview.id }
}

/** The submission inbox: newest first, 72 per page, loaded page by page while scrolling. */
class InboxRepository(private val fa: FaClient) {

    private val _state = MutableStateFlow(InboxState())
    val state: StateFlow<InboxState> = _state.asStateFlow()

    private val loading = Mutex()

    suspend fun refresh() {
        _state.update { it.copy(refreshing = true, error = null) }
        loading.withLock {
            try {
                val page = fa.read(FIRST_PAGE) { InboxParser.parse(it) }
                _state.update { it.copy(items = page.items, nextPath = page.nextPath, loaded = true, refreshing = false) }
            } catch (e: IOException) {
                _state.update { it.copy(refreshing = false, error = e.message ?: "Network error") }
            }
        }
    }

    /** Loads the next page, unless one is already loading or there is none. */
    suspend fun loadMore() {
        val next = _state.value.nextPath ?: return
        if (_state.value.loadingMore || loading.isLocked) return
        _state.update { it.copy(loadingMore = true, error = null) }
        loading.withLock {
            try {
                val page = fa.read(next) { InboxParser.parse(it) }
                _state.update { state ->
                    val known = state.ids.toHashSet()
                    state.copy(
                        items = state.items + page.items.filter { it.preview.id !in known },
                        nextPath = page.nextPath?.takeIf { it != next },
                        loadingMore = false,
                    )
                }
            } catch (e: IOException) {
                _state.update { it.copy(loadingMore = false, error = e.message ?: "Network error") }
            }
        }
    }

    /** Removes submissions from the inbox on FA (they stay on FA itself). */
    suspend fun remove(ids: Set<Long>): Boolean {
        if (ids.isEmpty()) return true
        return submit(listOf("messagecenter-action" to "remove_checked") + ids.map { "submissions[]" to it.toString() }) { state ->
            state.copy(items = state.items.filter { it.preview.id !in ids })
        }
    }

    /** Empties the whole inbox, every page of it. */
    suspend fun clearAll(): Boolean =
        submit(listOf("messagecenter-action" to "nuke_notifications")) { it.copy(items = emptyList(), nextPath = null) }

    private suspend fun submit(form: List<Pair<String, String>>, apply: (InboxState) -> InboxState): Boolean {
        _state.update { it.copy(removing = true, error = null) }
        return try {
            fa.post(FIRST_PAGE, form)
            _state.update { apply(it).copy(removing = false) }
            true
        } catch (e: IOException) {
            _state.update { it.copy(removing = false, error = e.message ?: "Network error") }
            false
        }
    }

    fun clear() {
        _state.value = InboxState()
    }

    private companion object {
        const val FIRST_PAGE = "/msg/submissions/new@72/"
    }
}
