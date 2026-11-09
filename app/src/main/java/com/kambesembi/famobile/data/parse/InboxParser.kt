package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.data.model.DatedPreview
import com.kambesembi.famobile.data.model.InboxPage
import org.jsoup.nodes.Document

/** /msg/submissions/: the new submissions from watched artists, 72 per page, grouped by day. */
object InboxParser {

    fun parse(doc: Document): InboxPage {
        val root = doc.selectFirst("#messages-form") ?: doc
        val items = mutableListOf<DatedPreview>()
        var day: String? = null
        // One pass in page order, so each figure gets the day heading above it.
        for (element in root.select("h4.date-divider, figure[id^=sid-]")) {
            if (element.tagName() == "h4") {
                day = element.text().trim().ifEmpty { null }
            } else {
                ParseUtils.preview(element)?.let { items += DatedPreview(it, day) }
            }
        }
        return InboxPage(items.distinctBy { it.preview.id }, nextPath(doc))
    }

    /** "Next 72" → /msg/submissions/new~<id>@72/. The "Prev." button carries the class "prev". */
    private fun nextPath(doc: Document): String? =
        doc.select("a[href*=/msg/submissions/]")
            .firstOrNull { link ->
                !link.hasClass("prev") && link.text().trim().startsWith("Next", ignoreCase = true)
            }
            ?.attr("href")
}
