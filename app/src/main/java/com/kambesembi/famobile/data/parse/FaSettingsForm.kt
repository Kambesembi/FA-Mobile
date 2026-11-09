package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.data.model.FaSetting
import com.kambesembi.famobile.data.model.FaSettingEditor
import com.kambesembi.famobile.data.model.SettingSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** A settings form ready to send: where, how, and every field as a browser would send it. */
data class FilledForm(val action: String, val multipart: Boolean, val fields: List<Pair<String, String>>, val fileFields: List<String>)

/**
 * Changing one entry the way the website does it: FA's own form with all its fields as they are (hidden
 * ones included), only this entry changed, sent with its save button.
 */
object FaSettingsForm {

    /** Submit buttons that must never be pressed by mistake. */
    private val dangerous = Regex("delete|remove|disable|deactivate", RegexOption.IGNORE_CASE)

    fun fill(doc: Document, setting: FaSetting, edit: FaSettingEditor, pagePath: String): FilledForm? {
        val names = when (val source = setting.source) {
            is SettingSource.Field -> listOf(source.name)
            is SettingSource.Ticks -> source.names
            else -> return null
        }
        val form = names.firstNotNullOfOrNull { name -> doc.selectFirst("[name=\"$name\"]")?.parents()?.firstOrNull { it.tagName() == "form" } }
            ?: return null
        if (!form.attr("method").equals("post", ignoreCase = true)) return null
        val fields = successful(form)
        val changed = when (edit) {
            is FaSettingEditor.Choice -> replace(fields, names[0], listOfNotNull(edit.selected))
            is FaSettingEditor.Free -> replace(fields, names[0], listOf(edit.text))
            is FaSettingEditor.Toggle -> replace(fields, names[0], if (edit.on) listOf(checkboxValue(form, names[0])) else emptyList())
            is FaSettingEditor.Multi -> names.fold(fields) { acc, name ->
                replace(acc, name, if (name in edit.ticked) listOf(checkboxValue(form, name)) else emptyList())
            }
            is FaSettingEditor.Tags -> return null
        }
        val action = form.attr("action").trim().ifEmpty { pagePath }
        val files = form.select("input[type=file][name]").filterNot { it.hasAttr("disabled") }.map { it.attr("name") }
        return FilledForm(action, form.attr("enctype").contains("multipart", ignoreCase = true), changed + listOfNotNull(saveButton(form)), files)
    }

    /** What a browser sends for [form]: named, enabled controls, ticked boxes only, the chosen options. */
    internal fun successful(form: Element): List<Pair<String, String>> = buildList {
        for (el in form.select("input, select, textarea")) {
            val name = el.attr("name")
            if (name.isEmpty() || el.hasAttr("disabled")) continue
            when (el.tagName()) {
                "select" -> {
                    val options = el.select("option")
                    val chosen = options.filter { it.hasAttr("selected") }
                        .ifEmpty { if (el.hasAttr("multiple")) emptyList() else options.take(1) }
                    chosen.forEach { add(name to optionValue(it)) }
                }
                "textarea" -> add(name to el.`val`())
                else -> when (el.attr("type").lowercase()) {
                    "submit", "button", "image", "reset", "file" -> Unit
                    "checkbox", "radio" -> if (el.hasAttr("checked")) add(name to el.attr("value").ifEmpty { "on" })
                    else -> add(name to el.attr("value"))
                }
            }
        }
    }

    /** The form's save button, if it has a name to send; never one that deletes or disables. */
    private fun saveButton(form: Element): Pair<String, String>? = form
        .select("button[name], input[type=submit][name]")
        .filter { it.attr("type").lowercase() !in setOf("button", "reset") }
        .firstOrNull { !dangerous.containsMatchIn(it.attr("name") + " " + it.attr("value") + " " + it.text()) }
        ?.let { it.attr("name") to it.attr("value") }

    private fun optionValue(option: Element) = if (option.hasAttr("value")) option.attr("value") else option.text().trim()

    private fun checkboxValue(form: Element, name: String) =
        form.selectFirst("input[type=checkbox][name=\"$name\"]")?.attr("value")?.ifEmpty { "on" } ?: "on"

    /** [fields] with [name]'s values swapped for [values], in the same place (or at the end). */
    private fun replace(fields: List<Pair<String, String>>, name: String, values: List<String>): List<Pair<String, String>> {
        val at = fields.indexOfFirst { it.first == name }
        val rest = fields.filterNot { it.first == name }
        val insert = values.map { name to it }
        if (at < 0) return rest + insert
        val before = fields.take(at).count { it.first != name }
        return rest.take(before) + insert + rest.drop(before)
    }
}
