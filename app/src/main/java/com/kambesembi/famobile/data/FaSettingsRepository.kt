package com.kambesembi.famobile.data

import com.kambesembi.famobile.data.model.FaSetting
import com.kambesembi.famobile.data.model.FaSettingEditor
import com.kambesembi.famobile.data.model.FaSettingValue
import com.kambesembi.famobile.data.model.FaSettingsPage
import com.kambesembi.famobile.data.net.FormFile
import com.kambesembi.famobile.data.parse.FaSettingsForm
import com.kambesembi.famobile.data.parse.FaSettingsParser
import com.kambesembi.famobile.data.parse.SystemMessageParser
import java.io.IOException
import kotlinx.coroutines.delay
import org.json.JSONException
import org.json.JSONObject

/** One entry on the page: what it is set to, and how it can be changed (null: on FA only). */
data class FaSettingEntry(val setting: FaSetting, val value: FaSettingValue, val editor: FaSettingEditor?)

/** Reads the chosen entries from FA's control pages, each page once, and changes them the way FA's forms do. */
class FaSettingsRepository(private val fa: FaClient) {

    /** The [ids] in catalogue order, grouped by page; throws [IOException] like any page load. */
    suspend fun load(ids: Set<String>): List<Pair<FaSettingsPage, List<FaSettingEntry>>> {
        val chosen = FaSetting.all.filter { it.id in ids }
        return chosen.groupBy { it.page }.map { (page, settings) ->
            page to fa.read(page.path) { doc -> settings.map { entry(doc, it) } }
        }
    }

    /**
     * Sends [edit] with the rest of FA's form as it is, then reads the page again: the change counts
     * only once FA shows it. Throws [IOException] with FA's message (or ours) when it didn't stick.
     */
    suspend fun save(setting: FaSetting, edit: FaSettingEditor): FaSettingEntry {
        if (edit is FaSettingEditor.Tags) return saveTags(setting, edit.tags)
        val path = setting.page.path
        val form = fa.read(path) { FaSettingsForm.fill(it, setting, edit, path) }
            ?: throw IOException("This setting can't be changed from the app; use \"Change on FA\".")
        val answer = if (form.multipart) {
            fa.postMultipart(form.action, form.fields, form.fileFields.map { FormFile(it, null) })
        } else {
            fa.post(form.action, form.fields)
        }
        val after = fa.read(path) { entry(it, setting) }
        if (!sameState(after.editor, edit)) {
            throw IOException(SystemMessageParser.message(answer.document) ?: "Fur Affinity didn't take the change.")
        }
        return after
    }

    /** Blocked tags go one by one, as FA's own script sends them, then the page is read again. */
    private suspend fun saveTags(setting: FaSetting, tags: List<String>): FaSettingEntry {
        val path = setting.page.path
        val (key, now) = fa.read(path) { FaSettingsParser.tagKey(it) to FaSettingsParser.blockedTags(it) }
        if (key.isEmpty()) throw IOException("This setting can't be changed from the app; use \"Change on FA\".")
        val wanted = tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        val had = now.associateBy { it.lowercase() }
        val problems = buildList {
            for ((low, tag) in had) if (low !in wanted) add(tagBlocking("remove-tag", key, tag))
            for (tag in wanted) if (tag !in had) add(tagBlocking("add-tag", key, tag))
        }.filterNotNull()
        val after = fa.read(path) { entry(it, setting) }
        val got = (after.editor as? FaSettingEditor.Tags)?.tags.orEmpty().map { it.lowercase() }.toSet()
        if (got != wanted.toSet()) throw IOException(problems.firstOrNull() ?: "Fur Affinity didn't take the change.")
        return after
    }

    /** One add or remove; FA's error text, or null when it went through (or said nothing we can read). */
    private suspend fun tagBlocking(action: String, key: String, tag: String): String? {
        repeat(4) {
            val answer = fa.postMultipart(TAG_BLOCKING, listOf("action" to action, "key" to key, "tag_name" to tag), emptyList())
            val body = try {
                JSONObject(answer.document.body().text())
            } catch (e: JSONException) {
                return null
            }
            if (body.optString("error") != "rate-limited") {
                return if (body.optBoolean("success")) null else body.optString("error").ifEmpty { null }
            }
            // Too quick: FA says how long to wait, as its own script does.
            delay((body.optDouble("time-left", 1.0).coerceIn(0.2, 10.0) * 1000).toLong())
        }
        return "Fur Affinity asks to slow down; try again in a moment."
    }

    private fun entry(doc: org.jsoup.nodes.Document, setting: FaSetting) =
        FaSettingEntry(setting, FaSettingsParser.read(doc, setting), FaSettingsParser.editor(doc, setting))

    private fun sameState(now: FaSettingEditor?, wanted: FaSettingEditor): Boolean = when (wanted) {
        is FaSettingEditor.Choice -> (now as? FaSettingEditor.Choice)?.selected == wanted.selected
        is FaSettingEditor.Toggle -> (now as? FaSettingEditor.Toggle)?.on == wanted.on
        is FaSettingEditor.Multi -> (now as? FaSettingEditor.Multi)?.ticked == wanted.ticked
        is FaSettingEditor.Free -> (now as? FaSettingEditor.Free)?.text?.let { normal(it) == normal(wanted.text) } == true
        is FaSettingEditor.Tags -> (now as? FaSettingEditor.Tags)?.tags?.map { it.lowercase() }?.toSet() == wanted.tags.map { it.lowercase() }.toSet()
    }

    /** Line ends and edge spaces as FA may store them. */
    private fun normal(text: String) = text.replace("\r\n", "\n").trim()

    private companion object {
        const val TAG_BLOCKING = "/route/tag_blocking"
    }
}
