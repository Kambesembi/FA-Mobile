package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.data.Words
import com.kambesembi.famobile.data.model.Journal
import com.kambesembi.famobile.data.model.JournalResult
import com.kambesembi.famobile.data.model.Rating
import org.jsoup.nodes.Document

/**
 * /journal/<id>/: the author's user header, the title block (`#c-journalTitleTop`), the text in
 * `.journal-content`, and comments with the same markup and form as on submissions.
 */
object JournalParser {

    fun parse(doc: Document, id: Long): JournalResult {
        val title = doc.selectFirst("#c-journalTitleTop__subject")?.text()?.trim()
        if (title == null || doc.selectFirst("userpage-nav-header") == null) {
            return JournalResult.Unavailable(SystemMessageParser.message(doc) ?: Words.get(Words.Text.NoJournal))
        }
        return JournalResult.Loaded(
            Journal(
                id = id,
                title = title,
                author = ProfileParser.user(doc, ""),
                postedAt = doc.selectFirst("#c-journalTitleTop__date .popup_date, #c-journalTitleBottom .popup_date")
                    ?.attr("data-time")?.toLongOrNull(),
                rating = Rating.from(doc.selectFirst("#c-journalTitleTop__contentRating")?.className()),
                bodyHtml = doc.selectFirst(".journal-content")?.html()?.trim().orEmpty(),
                comments = doc.select("#comments-journal .comment_container").mapNotNull(ParseUtils::comment),
                commentForm = ParseUtils.replyForm(doc.selectFirst("form#add_comment_form")),
            ),
        )
    }
}
