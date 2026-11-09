package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.data.model.FilterOption
import com.kambesembi.famobile.data.model.FilterOptions
import com.kambesembi.famobile.data.model.Rating
import com.kambesembi.famobile.data.model.ResultsPage
import org.jsoup.nodes.Document

/** /browse/ and /search/: a grid of figures, FA's filter lists, and whether a next page exists. */
object ExploreParser {

    private val total = Regex("""of\s+([\d,]+)\)""")

    fun browse(doc: Document): ResultsPage = ResultsPage(
        items = doc.select("#gallery-browse figure[id^=sid-]").mapNotNull { ParseUtils.preview(it) }.distinctBy { it.id },
        hasNext = nextEnabled(doc),
        total = null,
        options = options(doc, category = "cat", type = "atype", ratingPrefix = "rating_"),
    )

    fun search(doc: Document): ResultsPage = ResultsPage(
        items = doc.select("#gallery-search-results figure[id^=sid-]").mapNotNull { ParseUtils.preview(it) }.distinctBy { it.id },
        hasNext = nextEnabled(doc),
        total = ParseUtils.number(doc.selectFirst("#query-stats")?.text()?.let { total.find(it)?.groupValues?.get(1) }),
        options = options(doc, category = "category", type = "arttype", ratingPrefix = "rating-"),
    )

    /** Browse has a "Next" submit button in a GET form; search a `button[name=page]` "Next". */
    private fun nextEnabled(doc: Document): Boolean =
        doc.select("button").any { it.text().trim().equals("Next", ignoreCase = true) && !it.hasAttr("disabled") }

    private fun options(doc: Document, category: String, type: String, ratingPrefix: String): FilterOptions {
        // Checkboxes, disabled when the account can't see that rating. Logged out, browse has only
        // hidden fields, so only General.
        val ratings = mutableSetOf(Rating.General)
        val checked = mutableSetOf<Rating>()
        for ((rating, name) in listOf(Rating.General to "general", Rating.Mature to "mature", Rating.Adult to "adult")) {
            val box = doc.selectFirst("input[type=checkbox][name=$ratingPrefix$name]")
            if (box != null && !box.hasAttr("disabled")) ratings += rating
            if (box != null && box.hasAttr("checked") && !box.hasAttr("disabled")) checked += rating
        }
        return FilterOptions(
            categories = select(doc, category),
            types = select(doc, type),
            species = select(doc, "species"),
            genders = select(doc, "gender"),
            ratings = ratings,
            checkedRatings = checked.ifEmpty { setOf(Rating.General) },
            perPage = doc.selectFirst("select[name=perpage] option[selected]")?.attr("value")?.toIntOrNull(),
        )
    }

    private fun select(doc: Document, name: String): List<FilterOption> {
        val select = doc.select("select[name=$name]").firstOrNull { it.selectFirst("option") != null } ?: return emptyList()
        return select.select("option").map { option ->
            val group = option.parent()?.takeIf { it.tagName() == "optgroup" }?.attr("label")?.ifBlank { null }
            FilterOption(option.attr("value"), option.text().trim(), group)
        }
    }
}
