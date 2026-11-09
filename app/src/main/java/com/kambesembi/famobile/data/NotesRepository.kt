package com.kambesembi.famobile.data

import com.kambesembi.famobile.data.model.Note
import com.kambesembi.famobile.data.model.NoteFolder
import com.kambesembi.famobile.data.model.NoteMove
import com.kambesembi.famobile.data.model.NoteSummary
import com.kambesembi.famobile.data.parse.NotesParser
import com.kambesembi.famobile.data.parse.SystemMessageParser
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NotesState(
    val folder: NoteFolder = NoteFolder.Inbox,
    val notes: List<NoteSummary> = emptyList(),
    val nextPath: String? = null,
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

/** The result of opening one note. */
sealed interface NoteResult {
    data class Loaded(val note: Note) : NoteResult
    data class Failed(val message: String) : NoteResult
}

/**
 * FA's notes. The folder lives on FA's side (/controls/switchbox/<folder>/ switches it), so every
 * refresh switches to the folder shown here first.
 */
class NotesRepository(private val fa: FaClient) {

    private val _state = MutableStateFlow(NotesState())
    val state: StateFlow<NotesState> = _state.asStateFlow()

    private val _opened = MutableStateFlow<Map<Long, NoteResult>>(emptyMap())
    val opened: StateFlow<Map<Long, NoteResult>> = _opened.asStateFlow()

    private val loading = Mutex()
    private var sendKey: String? = null
    private var manageValue = "1"

    suspend fun show(folder: NoteFolder) {
        if (folder == _state.value.folder && _state.value.loaded) return
        _state.update { NotesState(folder = folder) }
        refresh()
    }

    suspend fun refresh() {
        val folder = _state.value.folder
        _state.update { it.copy(refreshing = true, error = null) }
        loading.withLock {
            try {
                val page = fa.read("/controls/switchbox/${folder.path}/") { NotesParser.list(it) }
                remember(page.sendKey, page.manageValue)
                _state.update {
                    if (it.folder != folder) it // switched again meanwhile
                    else it.copy(notes = page.notes, nextPath = page.nextPath, loaded = true, refreshing = false)
                }
            } catch (e: IOException) {
                _state.update { it.copy(refreshing = false, error = e.message ?: "Network error") }
            }
        }
    }

    suspend fun loadMore() {
        val next = _state.value.nextPath ?: return
        if (_state.value.loadingMore || loading.isLocked) return
        _state.update { it.copy(loadingMore = true, error = null) }
        loading.withLock {
            try {
                val page = fa.read(next) { NotesParser.list(it) }
                remember(page.sendKey, page.manageValue)
                _state.update { s ->
                    val known = s.notes.map { it.id }.toHashSet()
                    s.copy(
                        notes = s.notes + page.notes.filter { it.id !in known },
                        nextPath = page.nextPath?.takeIf { it != next },
                        loadingMore = false,
                    )
                }
            } catch (e: IOException) {
                _state.update { it.copy(loadingMore = false, error = e.message ?: "Network error") }
            }
        }
    }

    /** Opens a note, which marks it read on FA. */
    suspend fun open(id: Long, force: Boolean = false) {
        if (!force && _opened.value[id] is NoteResult.Loaded) return
        val result = try {
            val doc = fa.page("/msg/pms/1/$id/").document
            val note = NotesParser.note(doc, id)
            if (note != null) {
                remember(note.sendKey, null)
                NoteResult.Loaded(note)
            } else {
                NoteResult.Failed(SystemMessageParser.message(doc) ?: "This note couldn't be shown.")
            }
        } catch (e: IOException) {
            NoteResult.Failed(e.message ?: "Network error")
        }
        _opened.update { it + (id to result) }
        if (result is NoteResult.Loaded) markLocally(setOf(id), unread = false)
    }

    /** Trash, archive or mark unread on FA. */
    suspend fun move(ids: Set<Long>, to: NoteMove): Boolean {
        if (ids.isEmpty()) return true
        val form = listOf("manage_notes" to manageValue) + ids.map { "items[]" to it.toString() } + ("move_to" to to.value)
        return try {
            fa.post("/msg/pms/", form)
            when (to) {
                NoteMove.Unread -> markLocally(ids, unread = true)
                else -> _state.update { s -> s.copy(notes = s.notes.filter { it.id !in ids }) }
            }
            true
        } catch (e: IOException) {
            _state.update { it.copy(error = e.message ?: "Network error") }
            false
        }
    }

    /** Sends a note; returns null when it went out, otherwise what went wrong. */
    suspend fun send(to: String, subject: String, message: String): String? {
        return try {
            val key = sendKey ?: fa.read("/msg/pms/") { NotesParser.list(it) }.sendKey.also { sendKey = it }
                ?: return "Couldn't prepare the note. Please try again."
            val page = fa.post("/msg/send/", listOf("key" to key, "to" to to.trim(), "subject" to subject, "message" to message))
            val doc = page.document
            // Back on the notes page means sent; anything else is FA explaining why not.
            if (doc.selectFirst(".messagecenter-mail-container, .c-noteListItem") != null) {
                if (_state.value.folder == NoteFolder.Sent) refresh()
                null
            } else {
                SystemMessageParser.message(doc) ?: doc.selectFirst(".notice-message, .error, .redirect-message")?.text()?.trim()
                    ?.ifEmpty { null } ?: "Fur Affinity didn't confirm the note was sent."
            }
        } catch (e: IOException) {
            e.message ?: "Network error"
        }
    }

    private fun markLocally(ids: Set<Long>, unread: Boolean) {
        _state.update { s -> s.copy(notes = s.notes.map { if (it.id in ids) it.copy(unread = unread) else it }) }
    }

    private fun remember(key: String?, manage: String?) {
        if (key != null) sendKey = key
        if (manage != null) manageValue = manage
    }

    fun clear() {
        _state.value = NotesState()
        _opened.value = emptyMap()
        sendKey = null
    }
}
