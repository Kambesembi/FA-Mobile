package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.data.model.FilterOption
import com.kambesembi.famobile.data.model.Keywords
import com.kambesembi.famobile.data.model.Rating
import com.kambesembi.famobile.data.model.UploadChoice
import com.kambesembi.famobile.data.model.UploadDetails
import com.kambesembi.famobile.data.model.UploadItem
import com.kambesembi.famobile.data.model.UploadKind
import com.kambesembi.famobile.data.model.UploadLists
import java.util.Locale
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/** One named control of a form, with what a browser would send for it as the page stands. */
data class FormControl(
    val name: String,
    /** "hidden", "text", "textarea", "select", "radio", "checkbox", "file" or "submit". */
    val type: String,
    val value: String,
    val checked: Boolean = false,
    /** For radios and checkboxes: the text next to it. */
    val label: String = "",
    val options: List<FilterOption> = emptyList(),
)

/** A form read from a page: where it goes and its controls in page order. */
data class HtmlForm(val action: String, val controls: List<FormControl>) {

    fun named(name: String): List<FormControl> = controls.filter { it.name == name }

    /** The first control whose name passes [test]: FA's names, with some room for renames. */
    fun find(test: (String) -> Boolean): FormControl? = controls.firstOrNull { test(it.name) }

    companion object {
        fun of(form: Element): HtmlForm {
            val controls = form.select("input[name], textarea[name], select[name], button[name]").mapNotNull { e ->
                val name = e.attr("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                when (e.tagName()) {
                    "textarea" -> FormControl(name, "textarea", e.wholeText())
                    "select" -> {
                        val options = e.select("option").map { o ->
                            val group = o.parent()?.takeIf { it.tagName() == "optgroup" }?.attr("label")?.ifBlank { null }
                            FilterOption(o.attr("value").ifEmpty { o.text().trim() }, o.text().trim(), group)
                        }
                        val selected = e.selectFirst("option[selected]")?.let { o -> o.attr("value").ifEmpty { o.text().trim() } }
                        FormControl(name, "select", selected ?: options.firstOrNull()?.value.orEmpty(), options = options)
                    }
                    "button" -> if (e.attr("type").ifEmpty { "submit" } == "submit") FormControl(name, "submit", e.attr("value").ifEmpty { e.text().trim() }) else null
                    else -> {
                        val type = e.attr("type").lowercase(Locale.ROOT).ifEmpty { "text" }
                        when (type) {
                            "radio", "checkbox" -> FormControl(name, type, e.attr("value").ifEmpty { "on" }, e.hasAttr("checked"), label(form, e))
                            "submit" -> FormControl(name, "submit", e.attr("value"))
                            "file" -> FormControl(name, "file", "")
                            "button", "reset", "image" -> null
                            else -> FormControl(name, if (type == "hidden") "hidden" else "text", e.attr("value"))
                        }
                    }
                }
            }
            return HtmlForm(form.attr("action").substringBefore('#').trim(), controls)
        }

        /** The words next to a radio or checkbox: its <label>, else the text right after it. */
        private fun label(form: Element, input: Element): String {
            val id = input.id()
            if (id.isNotEmpty()) form.ownerDocument()?.selectFirst("label[for=$id]")?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            input.parents().firstOrNull { it.tagName() == "label" }?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            return when (val next = input.nextSibling()) {
                is TextNode -> next.text().trim()
                is Element -> next.text().trim()
                else -> ""
            }
        }
    }
}

/** FA's first upload page (/submit/): the file form with its key. */
data class SubmitPage(val form: HtmlForm, val kinds: Set<UploadKind>, val maxFileSize: Long?) {
    /** What goes with the files: the hidden fields as given, and the chosen kind. */
    fun fields(kind: UploadKind): List<Pair<String, String>> =
        form.controls.filter { it.type == "hidden" || it.type == "text" }.map { it.name to it.value } +
            ("submission_type" to kind.value)
}

/** FA's details form, after the file went up. [folderGroups]: folder id → the group it's listed under. */
data class FinalizePage(val form: HtmlForm, val folderGroups: Map<String, String> = emptyMap()) {
    private fun select(vararg names: String) = names.firstNotNullOfOrNull { n -> form.named(n).firstOrNull { it.type == "select" } }

    val category get() = select("cat", "category")
    val theme get() = select("atype", "theme", "arttype")
    val species get() = select("species")

    /** The lists as FA offers them on this form, without its "All" / "Unspecified" defaults. */
    val lists: UploadLists
        get() = UploadLists(
            categories = category?.options.orEmpty().filterNot(UploadParser::isNoChoice),
            themes = theme?.options.orEmpty().filterNot(UploadParser::isNoChoice),
            species = species?.options.orEmpty().filterNot(UploadParser::isNoChoice),
            exact = true,
        )

    /** The account's folders as offered here. */
    val folders: List<FilterOption>
        get() = form.controls.filter { it.type == "checkbox" && it.name.startsWith("folder") }
            .map { FilterOption(it.value, it.label, folderGroups[it.value]) }
}

object UploadParser {

    private val viewId = Regex("""/view/(\d+)""")
    private val folderLink = Regex("""/folder/(\d+)/""")

    /** The file form on /submit/, or null when the page has none (logged out, not allowed to upload). */
    fun submitPage(doc: Document): SubmitPage? {
        val element = doc.select("form").firstOrNull { it.selectFirst("input[type=file][name=submission]") != null }
            ?: doc.selectFirst("form#myform") ?: return null
        val form = HtmlForm.of(element)
        if (form.action.isEmpty()) return null
        val kinds = form.named("submission_type").mapNotNull { c -> UploadKind.entries.firstOrNull { it.value == c.value } }.toSet()
        return SubmitPage(
            form = form,
            kinds = kinds.ifEmpty { UploadKind.entries.toSet() },
            maxFileSize = form.named("MAX_FILE_SIZE").firstOrNull()?.value?.toLongOrNull(),
        )
    }

    /** The details form that follows the file: the one asking for a title. */
    fun finalizePage(doc: Document): FinalizePage? {
        val element = doc.select("form").firstOrNull { f ->
            f.selectFirst("input[name=title], textarea[name=title]") != null && f.attr("action").isNotBlank()
        } ?: return null
        // Folders come in groups: a heading, then its folders (the last heading is for those in none).
        val groups = mutableMapOf<String, String>()
        for (box in element.select(".assign_folders")) {
            var group: String? = null
            for (e in box.select("h3, h4, input[type=checkbox]")) {
                if (e.tagName() != "input") {
                    group = e.text().trim().takeUnless { it.isEmpty() || it.startsWith("Folders not in", ignoreCase = true) }
                } else if (group != null) {
                    groups[e.attr("value")] = group
                }
            }
        }
        return FinalizePage(HtmlForm.of(element), groups)
    }

    /** FA's "All", "Any" and "Unspecified / Any": what the lists start at, not a real choice. */
    fun isNoChoice(option: FilterOption): Boolean =
        option.label.equals("All", ignoreCase = true) || option.label.equals("Any", ignoreCase = true) ||
            option.label.startsWith("Unspecified", ignoreCase = true)

    private val floodWait = Regex("""(?i)wait\s+(\d+)\s+sec""")

    /**
     * FA's flood protection ("Flood protection. Please wait 16 seconds before trying again."): how
     * many seconds it wants, or null for any other message.
     */
    fun floodSeconds(message: String?): Int? {
        if (message == null || !message.contains("flood", ignoreCase = true)) return null
        return floodWait.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 30
    }

    /** The new submission, once FA has sent us there. */
    fun submissionId(url: String): Long? = viewId.find(url)?.groupValues?.get(1)?.toLongOrNull()

    /** Why FA didn't go on: its system message, or an error shown above a form. */
    fun problem(doc: Document): String? =
        SystemMessageParser.message(doc)
            ?: doc.select(".error-message, .notice-message, .redirect-message, .alert, #error, .error")
                .map { it.text().trim() }
                .firstOrNull { it.isNotEmpty() }

    /**
     * The account's gallery folders, from its gallery page, each under the heading above it. Folders
     * in no group sit in FA's `ul.default-group`, wherever that list comes, and get no heading.
     */
    fun galleryFolders(doc: Document): List<FilterOption> {
        val out = mutableListOf<FilterOption>()
        for (box in doc.select(".user-folders")) {
            var group: String? = null
            for (e in box.select("h4, a[href*=/folder/]")) {
                if (e.tagName() == "h4") {
                    group = e.text().trim().ifEmpty { null }
                    continue
                }
                val id = folderLink.find(e.attr("href"))?.groupValues?.get(1) ?: continue
                out += FilterOption(id, e.text().trim(), if (e.closest("ul.default-group") != null) null else group)
            }
        }
        return out.distinctBy { it.value }
    }

    /**
     * The details form as it will be sent: FA's own defaults for everything, with what was chosen put
     * in. A choice that isn't on FA's list is left at FA's default rather than sent wrong.
     */
    fun fill(page: FinalizePage, item: UploadItem, details: UploadDetails): List<Pair<String, String>> {
        val form = page.form
        val out = mutableListOf<Pair<String, String>>()
        val descriptionName = form.find { it == "message" || it == "description" }?.name
        val keywordsName = form.find { it.contains("keyword") }?.name
        val choices = mapOf(
            page.category?.name to details.category,
            page.theme?.name to details.theme,
            page.species?.name to details.species,
        )
        val ratingValue = details.rating?.let { ratingValue(form, it) }
        var submitAdded = false
        for (c in form.controls) {
            when (c.type) {
                "file" -> Unit
                "submit" -> if (!submitAdded) {
                    out += c.name to c.value
                    submitAdded = true
                }
                "radio" -> {
                    if (c.name == "rating" && ratingValue != null) {
                        if (c.value == ratingValue) out += c.name to c.value
                    } else if (c.checked) {
                        out += c.name to c.value
                    }
                }
                "checkbox" -> {
                    val on = when {
                        c.name.contains("scrap") -> details.scraps
                        c.name.contains("lock_comment") || c.name.contains("disable_comment") -> details.lockComments
                        c.name.startsWith("folder") -> details.folders.any { it.value == c.value } ||
                            details.folders.any { it.label.equals(c.label, ignoreCase = true) && it.label.isNotEmpty() }
                        else -> c.checked
                    }
                    if (on) out += c.name to c.value
                }
                "select" -> out += c.name to (choices[c.name]?.let { pick(c.options, it) } ?: c.value)
                else -> out += c.name to when (c.name) {
                    "title" -> item.title
                    descriptionName -> details.description
                    keywordsName -> Keywords.join(details.keywords)
                    else -> c.value
                }
            }
        }
        // Folders FA didn't list on this form: nothing to tick.
        return out
    }

    /** The option for [choice]: same value and label, else the same label, else the same value. */
    internal fun pick(options: List<FilterOption>, choice: UploadChoice): String? =
        options.firstOrNull { it.value == choice.value && it.label.equals(choice.label, ignoreCase = true) }?.value
            ?: options.firstOrNull { it.label.equals(choice.label, ignoreCase = true) }?.value
            ?: options.firstOrNull { it.value == choice.value }?.value

    /** The rating radio's value: by its label ("General"), else FA's usual numbers. */
    private fun ratingValue(form: HtmlForm, rating: Rating): String {
        val radios = form.named("rating").filter { it.type == "radio" }
        // Labels carry an explanation after the name ("General Suitable for all-ages.").
        radios.firstOrNull { it.label.startsWith(rating.name, ignoreCase = true) }?.let { return it.value }
        return when (rating) {
            Rating.General -> "0"
            Rating.Mature -> "2"
            Rating.Adult -> "1"
        }
    }
}
