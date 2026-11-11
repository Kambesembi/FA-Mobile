package com.kambesembi.famobile.data

import com.kambesembi.famobile.data.model.ActivityItem
import com.kambesembi.famobile.data.model.ActivitySection
import com.kambesembi.famobile.data.model.FormField
import com.kambesembi.famobile.data.parse.ActivityParser
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ActivityState(
    val sections: List<ActivitySection> = emptyList(),
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    /** A removal is on its way to FA. */
    val removing: Boolean = false,
    val error: String? = null,
)

/** FA's /msg/others/: watches, comments, favorites, journals and shouts. */
class ActivityRepository(private val fa: FaClient) {

    private val _state = MutableStateFlow(ActivityState())
    val state: StateFlow<ActivityState> = _state.asStateFlow()

    suspend fun refresh() {
        _state.update { it.copy(refreshing = true, error = null) }
        try {
            val sections = fa.read(PAGE) { ActivityParser.parse(it) }
            _state.update { it.copy(sections = sections, loaded = true, refreshing = false) }
        } catch (e: IOException) {
            _state.update { it.copy(refreshing = false, error = e.message ?: Words.get(Words.Text.Network)) }
        }
    }

    /** Removes notifications (the comments, favs etc. themselves stay on FA). */
    suspend fun remove(items: Collection<ActivityItem>): Boolean {
        if (items.isEmpty()) return true
        val keys = items.map { it.key }.toSet()
        // Each section has its own remove button; FA removes every checked item with any of them.
        val button = _state.value.sections.firstOrNull { s -> s.items.any { it.key in keys } }?.remove
            ?: FormField("remove-all", "Remove Selected")
        val form = items.map { it.checkbox.name to it.checkbox.value } + (button.name to button.value)
        return submit(form) { sections ->
            sections.map { s -> s.copy(items = s.items.filter { it.key !in keys }) }.filter { it.items.isNotEmpty() }
        }
    }

    /** Removes every notification of a section, including ones not shown. */
    suspend fun clear(section: ActivitySection): Boolean {
        val nuke = section.nuke ?: return remove(section.items)
        return submit(listOf(nuke.name to nuke.value)) { sections -> sections.filter { it.sectionId != section.sectionId } }
    }

    private suspend fun submit(form: List<Pair<String, String>>, apply: (List<ActivitySection>) -> List<ActivitySection>): Boolean {
        _state.update { it.copy(removing = true, error = null) }
        return try {
            val page = fa.post(PAGE, form)
            // FA answers with the updated page; fall back to the local change if it can't be read.
            val fresh = fa.parsed(page) { doc -> ActivityParser.parse(doc).takeIf { doc.selectFirst("form[action*=/msg/others]") != null } }
            _state.update { it.copy(sections = fresh ?: apply(it.sections), removing = false) }
            true
        } catch (e: IOException) {
            _state.update { it.copy(removing = false, error = e.message ?: Words.get(Words.Text.Network)) }
            false
        }
    }

    fun clear() {
        _state.value = ActivityState()
    }

    private companion object {
        const val PAGE = "/msg/others/"
    }
}
