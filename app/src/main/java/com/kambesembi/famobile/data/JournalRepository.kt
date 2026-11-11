package com.kambesembi.famobile.data

import com.kambesembi.famobile.data.model.Journal
import com.kambesembi.famobile.data.model.JournalResult
import com.kambesembi.famobile.data.model.PostOutcome
import com.kambesembi.famobile.data.parse.JournalParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Journal pages with their comments, kept in memory while the app runs. */
class JournalRepository(private val fa: FaClient) {

    private val _loaded = MutableStateFlow<Map<Long, JournalResult>>(emptyMap())
    val loaded: StateFlow<Map<Long, JournalResult>> = _loaded.asStateFlow()

    /** Throws IOException on network trouble. */
    suspend fun load(id: Long, force: Boolean = false): JournalResult {
        if (!force) _loaded.value[id]?.let { return it }
        val result = fa.read("/journal/$id/") { JournalParser.parse(it, id) }
        _loaded.update { it.keepRecent(id, result) }
        return result
    }

    /** Posts a comment, or a reply to the comment [replyTo]. */
    suspend fun comment(journal: Journal, text: String, replyTo: Long? = null): PostOutcome<Journal> {
        val form = journal.commentForm ?: return PostOutcome.Failed(null)
        return fa.postReply(
            form = form,
            text = text,
            replyTo = replyTo,
            reloadPath = "/journal/${journal.id}/",
            before = journal.comments.map { it.id }.toSet(),
            parse = { doc ->
                when (val result = JournalParser.parse(doc, journal.id)) {
                    is JournalResult.Loaded -> Parsed(result.journal, null)
                    is JournalResult.Unavailable -> Parsed(null, result.message)
                }
            },
            ids = { page -> page.comments.map { it.id }.toSet() },
            keep = { page -> _loaded.update { it + (page.id to JournalResult.Loaded(page)) } },
        )
    }

    fun clear() {
        _loaded.value = emptyMap()
    }
}
