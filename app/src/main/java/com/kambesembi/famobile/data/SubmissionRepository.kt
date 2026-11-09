package com.kambesembi.famobile.data

import com.kambesembi.famobile.data.model.GalleryKind
import com.kambesembi.famobile.data.model.PostOutcome
import com.kambesembi.famobile.data.model.Submission
import com.kambesembi.famobile.data.model.SubmissionResult
import com.kambesembi.famobile.data.parse.ProfileParser
import com.kambesembi.famobile.data.parse.SubmissionParser
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Submission pages, kept in memory while swiping back and forth so each loads once. */
class SubmissionRepository(private val fa: FaClient) {

    private val _loaded = MutableStateFlow<Map<Long, SubmissionResult>>(emptyMap())

    /** Everything loaded so far; screens observe their id in here. */
    val loaded: StateFlow<Map<Long, SubmissionResult>> = _loaded.asStateFlow()

    private val order = ArrayDeque<Long>()

    private val _favorited = MutableStateFlow<Set<Long>>(emptySet())

    /** The account's newest favorites on FA (its first favorites page), wherever they were faved. */
    val favorited: StateFlow<Set<Long>> = _favorited.asStateFlow()

    /** Reads the newest favorites page of [user]; a failure keeps what was known. */
    suspend fun syncFavorites(user: String) {
        try {
            _favorited.value = fa.read(GalleryKind.Favorites.firstPage(user.lowercase())) { doc ->
                ProfileParser.gallery(doc, user).items.map { it.id }.toSet()
            }
        } catch (_: IOException) {
        }
    }

    /** Loads a submission unless it is already here. Throws IOException on network trouble. */
    suspend fun load(id: Long, force: Boolean = false): SubmissionResult {
        if (!force) _loaded.value[id]?.let { return it }
        val result = fa.read("/view/$id/") { SubmissionParser.parse(it, id) }
        store(id, result)
        return result
    }

    /** Favs or unfavs; FA answers with the submission page, so the new state comes from FA itself. */
    suspend fun toggleFav(submission: Submission): SubmissionResult {
        val path = submission.fav?.path ?: return SubmissionResult.Loaded(submission)
        val result = fa.read(path) { SubmissionParser.parse(it, submission.id) }
        store(submission.id, result)
        return result
    }

    /**
     * Favs ([faved] true) or unfavs a submission that is only known by its id (a grid): loads its page
     * for FA's link first, and changes nothing if it already is that way.
     */
    suspend fun setFaved(id: Long, faved: Boolean): SubmissionResult {
        val current = load(id)
        val submission = (current as? SubmissionResult.Loaded)?.submission ?: return current
        val now = submission.fav?.faved ?: return current
        return if (now != faved) toggleFav(submission) else current
    }

    /** Posts a comment, or a reply to the comment [replyTo]. */
    suspend fun comment(submission: Submission, text: String, replyTo: Long? = null): PostOutcome<Submission> {
        val form = submission.commentForm ?: return PostOutcome.Failed(null)
        return fa.postReply(
            form = form,
            text = text,
            replyTo = replyTo,
            reloadPath = "/view/${submission.id}/",
            before = submission.comments.map { it.id }.toSet(),
            parse = { doc ->
                when (val result = SubmissionParser.parse(doc, submission.id)) {
                    is SubmissionResult.Loaded -> Parsed(result.submission, null)
                    is SubmissionResult.Unavailable -> Parsed(null, result.message)
                }
            },
            ids = { page -> page.comments.map { it.id }.toSet() },
            keep = { page -> store(page.id, SubmissionResult.Loaded(page)) },
        )
    }

    private fun store(id: Long, result: SubmissionResult) {
        synchronized(order) {
            order.remove(id)
            order.addLast(id)
            val evicted = if (order.size > CACHE_SIZE) order.removeFirst() else null
            _loaded.update { map -> (map + (id to result)).let { if (evicted != null) it - evicted else it } }
        }
    }

    fun clear() {
        synchronized(order) { order.clear() }
        _loaded.value = emptyMap()
        _favorited.value = emptySet()
    }

    private companion object {
        const val CACHE_SIZE = 60
    }
}
