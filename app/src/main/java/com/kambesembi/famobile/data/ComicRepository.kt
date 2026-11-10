package com.kambesembi.famobile.data

import com.kambesembi.famobile.data.model.Rating
import com.kambesembi.famobile.data.model.SearchQuery
import com.kambesembi.famobile.data.model.Submission
import com.kambesembi.famobile.data.model.SubmissionResult
import com.kambesembi.famobile.data.parse.ComicParser
import com.kambesembi.famobile.data.parse.ExploreParser
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A comic being put together: the pages so far, in order, and whether more are coming. */
data class ComicState(
    val startId: Long,
    val pages: List<Submission> = emptyList(),
    val loading: Boolean = true,
    /** Why it stopped early (the pages so far stay). */
    val error: String? = null,
    /** A numbered title, but FA's search found no other pages. */
    val alone: Boolean = false,
)

/**
 * Loads a whole series, page by page: by the artist's First/Prev/Next links, or, without those, by
 * FA's search for the same title with other page numbers. Pages show up as they load.
 */
class ComicRepository(
    private val fa: FaClient,
    private val submissions: SubmissionRepository,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<ComicState?>(null)
    val state: StateFlow<ComicState?> = _state.asStateFlow()

    /** The walk through the pages. It belongs to the app, not the screen, so turning the phone doesn't end it. */
    private var job: Job? = null

    /**
     * Starts loading the comic of [startId], unless it is loading already or is all there. Call from
     * the main thread.
     */
    fun open(startId: Long) {
        val current = _state.value
        val same = current != null && current.startId == startId
        if (same && (job?.isActive == true || (current?.loading == false && current.error == null))) return
        job?.cancel()
        _state.value = ComicState(startId)
        job = scope.launch(Dispatchers.Default) { load(startId) }
    }

    /** Only the comic [startId] is changed: a walk that was replaced leaves the new one alone. */
    private inline fun change(startId: Long, crossinline to: (ComicState) -> ComicState) =
        _state.update { s -> if (s != null && s.startId == startId) to(s) else s }

    private suspend fun load(startId: Long) {
        try {
            val first = submissions.load(startId)
            val start = (first as? SubmissionResult.Loaded)?.submission ?: run {
                change(startId) { it.copy(loading = false, error = (first as? SubmissionResult.Unavailable)?.message.orEmpty()) }
                return
            }
            change(startId) { it.copy(pages = listOf(start)) }
            if (!ComicParser.links(start.descriptionHtml).isEmpty) byLinks(start) else byTitle(start)
            change(startId) { it.copy(loading = false) }
        } catch (e: IOException) {
            change(startId) { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
        }
    }

    /** The reader was left: stop loading and forget the pages. */
    fun close() {
        job?.cancel()
        job = null
        _state.value = null
    }

    private suspend fun page(id: Long): Submission? =
        (submissions.load(id) as? SubmissionResult.Loaded)?.submission

    private fun add(comic: Long, page: Submission, before: Boolean) = change(comic) { s ->
        s.copy(pages = if (before) listOf(page) + s.pages else s.pages + page)
    }

    private fun full() = (_state.value?.pages?.size ?: 0) >= MAX_PAGES

    /** Forward by "Next" first (what one reads next), then back by "Prev", or from "First" up. */
    private suspend fun byLinks(start: Submission) {
        val seen = mutableSetOf(start.id)
        var n = start
        while (!full()) {
            val next = ComicParser.links(n.descriptionHtml).next ?: break
            if (!seen.add(next)) break
            pause()
            n = page(next) ?: break
            add(start.id, n, before = false)
        }
        val startLinks = ComicParser.links(start.descriptionHtml)
        if (startLinks.prev != null) {
            var p = start
            while (!full()) {
                val links = ComicParser.links(p.descriptionHtml)
                val prev = links.prev ?: links.first?.takeIf { it !in seen } ?: break
                if (!seen.add(prev)) break
                pause()
                p = page(prev) ?: break
                add(start.id, p, before = true)
            }
        } else if (startLinks.first != null && startLinks.first !in seen) {
            // Only "First": from there forward until this page.
            val gap = mutableListOf<Submission>()
            var id: Long? = startLinks.first
            while (id != null && id != start.id && id !in seen && !full()) {
                seen.add(id)
                pause()
                val p = page(id) ?: break
                gap += p
                change(start.id) { s -> s.copy(pages = gap + s.pages.filter { it.id !in gap.map(Submission::id) }) }
                id = ComicParser.links(p.descriptionHtml).next
            }
        }
    }

    /** "Name - Page 9": the artist's other "Name" pages by FA's search, in page order. */
    private suspend fun byTitle(start: Submission) {
        val numbered = ComicParser.numberedTitle(start.title) ?: return
        val artist = start.artist.name.ifBlank { return }
        val want = ComicParser.normalize(numbered.base)
        val byNumber = sortedMapOf(numbered.number to start.id)
        val query = SearchQuery(text = ComicParser.searchText(numbered.base, artist), ratings = Rating.entries.toSet())
        for (pageNo in 1..5) {
            if (pageNo > 1) pause()
            val results = fa.read(query.path(pageNo, 72)) { ExploreParser.search(it) }
            for (item in results.items) {
                val t = ComicParser.numberedTitle(item.title) ?: continue
                if (ComicParser.normalize(t.base) != want || t.number in byNumber) continue
                byNumber[t.number] = item.id
            }
            if (!results.hasNext || results.items.isEmpty()) break
        }
        if (byNumber.size < 2) {
            change(start.id) { it.copy(alone = true) }
            return
        }
        val order = byNumber.values.toList().take(MAX_PAGES)
        val loaded = mutableMapOf(start.id to start)
        for (id in order) {
            if (id !in loaded) {
                pause()
                loaded[id] = page(id) ?: continue
            }
            change(start.id) { s -> s.copy(pages = order.mapNotNull { loaded[it] }) }
        }
    }

    /** A little room between page loads, to be kind to FA. */
    private suspend fun pause() = delay(200)

    private companion object {
        const val MAX_PAGES = 150
    }
}
