package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.data.model.FaSetting
import com.kambesembi.famobile.data.model.FaSettingEditor
import com.kambesembi.famobile.data.model.FaSettingValue
import com.kambesembi.famobile.data.model.SettingSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Reads what an FA control page's form is set to, the way the page shows it. */
object FaSettingsParser {

    /** Contact fields that aren't contacts: the site search. */
    private val notContacts = setOf("q")

    /** The contacts form: the one with the website field. */
    private const val CONTACT_FORM = "form:has(input[name=website])"

    fun read(doc: Document, setting: FaSetting): FaSettingValue = when (val source = setting.source) {
        is SettingSource.Field -> field(doc, source.name)
        is SettingSource.Ticks -> source.names.mapNotNull { name ->
            doc.selectFirst("input[type=checkbox][name=$name]")?.takeIf { it.hasAttr("checked") }?.let { labelOf(doc, it) }
        }.let { if (it.isEmpty()) FaSettingValue.Empty else FaSettingValue.Items(it) }
        SettingSource.BlockedTags -> blockedTags(doc).let { if (it.isEmpty()) FaSettingValue.Empty else FaSettingValue.Items(it) }
        SettingSource.Contacts -> doc.select(CONTACT_FORM).select("input[type=text], input[type=number], input[type=email]")
            .filter { it.attr("name") !in notContacts && it.attr("value").isNotBlank() }
            .map { "${labelOf(doc, it).ifEmpty { it.attr("name") }}: ${it.attr("value").trim()}" }
            .let { if (it.isEmpty()) FaSettingValue.Empty else FaSettingValue.Items(it) }
    }

    /** How [setting] can be changed, with what it is set to now; null when it's changed on FA only. */
    fun editor(doc: Document, setting: FaSetting): FaSettingEditor? {
        if (!setting.editable) return null
        return when (val source = setting.source) {
            is SettingSource.Field -> fieldEditor(doc, source.name)
            is SettingSource.Ticks -> {
                val boxes = source.names.mapNotNull { name -> doc.selectFirst("input[type=checkbox][name=$name]")?.let { name to it } }
                if (boxes.isEmpty()) null else FaSettingEditor.Multi(
                    boxes.map { (name, box) -> name to labelOf(doc, box).ifEmpty { name } },
                    boxes.filter { it.second.hasAttr("checked") }.map { it.first }.toSet(),
                )
            }
            // FA's own script changes them one by one, with the key it puts on the page.
            SettingSource.BlockedTags -> if (tagKey(doc).isEmpty()) null else FaSettingEditor.Tags(blockedTags(doc))
            else -> null
        }
    }

    /** The blocked tags, which FA lists on `<body>`. */
    fun blockedTags(doc: Document): List<String> =
        doc.body().attr("data-tag-blocklist").trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }

    /** The key FA's tag-blocking requests need; empty when the page has none. */
    fun tagKey(doc: Document): String = doc.body().attr("data-tag-blocklist-nonce").trim()

    private fun fieldEditor(doc: Document, name: String): FaSettingEditor? {
        doc.selectFirst("select[name=$name]")?.let { select ->
            val options = select.select("option").map { (if (it.hasAttr("value")) it.attr("value") else it.text().trim()) to it.text().trim() }
            val chosen = select.selectFirst("option[selected]") ?: select.selectFirst("option")
            return FaSettingEditor.Choice(options, chosen?.let { if (it.hasAttr("value")) it.attr("value") else it.text().trim() })
        }
        doc.selectFirst("textarea[name=$name]")?.let { return FaSettingEditor.Free(it.wholeText(), multiline = true) }
        val inputs = doc.select("input[name=$name]")
        val first = inputs.firstOrNull() ?: return null
        return when (first.attr("type").lowercase()) {
            "radio" -> FaSettingEditor.Choice(
                inputs.map { it.attr("value") to labelOf(doc, it).ifEmpty { it.attr("value") } },
                inputs.firstOrNull { it.hasAttr("checked") }?.attr("value"),
            )
            "checkbox" -> FaSettingEditor.Toggle(first.hasAttr("checked"))
            "text", "number", "" -> FaSettingEditor.Free(first.attr("value"), multiline = false)
            else -> null
        }
    }

    private fun field(doc: Document, name: String): FaSettingValue {
        doc.selectFirst("select[name=$name]")?.let { select ->
            val chosen = select.selectFirst("option[selected]") ?: select.selectFirst("option")
            return chosen?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { FaSettingValue.Text(it) } ?: FaSettingValue.Empty
        }
        doc.selectFirst("textarea[name=$name]")?.let { area ->
            val text = area.wholeText().trim()
            return if (text.isEmpty()) FaSettingValue.Empty else FaSettingValue.Text(text, long = true)
        }
        val inputs = doc.select("input[name=$name]")
        val first = inputs.firstOrNull() ?: return FaSettingValue.Empty
        return when (first.attr("type").lowercase()) {
            "radio" -> inputs.firstOrNull { it.hasAttr("checked") }?.let { FaSettingValue.Text(labelOf(doc, it).ifEmpty { it.attr("value") }) }
                ?: FaSettingValue.Empty
            "checkbox" -> FaSettingValue.Flag(first.hasAttr("checked"))
            else -> first.attr("value").trim().takeIf { it.isNotEmpty() }?.let { FaSettingValue.Text(it) } ?: FaSettingValue.Empty
        }
    }

    /** A control's label: `<label for=id>`, the label around it, or the name of its row. */
    private fun labelOf(doc: Document, input: Element): String {
        val id = input.id()
        if (id.isNotEmpty()) doc.selectFirst("label[for=$id]")?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        input.parents().firstOrNull { it.tagName() == "label" }?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        var row: Element? = input.parent()
        repeat(4) {
            row?.selectFirst("h4, h3, strong, th, .control-panel-item-name")?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            row = row?.parent()
        }
        return ""
    }
}
