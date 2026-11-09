package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.data.model.Comment
import com.kambesembi.famobile.data.model.FavState
import com.kambesembi.famobile.data.model.Rating
import com.kambesembi.famobile.data.model.Submission
import com.kambesembi.famobile.data.model.SubmissionKind
import com.kambesembi.famobile.data.model.SubmissionResult
import com.kambesembi.famobile.data.model.UserRef
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** /view/<id>/: one submission with its description, details and comments. */
object SubmissionParser {

    fun parse(doc: Document, id: Long): SubmissionResult {
        val page = doc.selectFirst("#submission_page")
            ?: return SubmissionResult.Unavailable(SystemMessageParser.message(doc) ?: "This submission can't be shown.")

        val artist = artist(doc) ?: UserRef("", "")
        val img = doc.selectFirst("img#submissionImg")
        val stats = doc.selectFirst(".submission-page-stats")

        return SubmissionResult.Loaded(
            Submission(
                id = id,
                title = (doc.selectFirst(".submission-title h2")?.text() ?: img?.attr("alt") ?: "").trim(),
                artist = artist,
                postedAt = doc.selectFirst(".submission-description-header .popup_date, .submission-title ~ * .popup_date")
                    ?.attr("data-time")?.toLongOrNull(),
                kind = SubmissionKind.from(ParseUtils.classWithPrefix(page, "page-content-type-")),
                rating = Rating.from(
                    stats?.selectFirst("[class*=c-contentRating--]")?.let { ParseUtils.classWithPrefix(it, "c-contentRating--") },
                ),
                imageUrl = ParseUtils.url(img?.attr("data-fullview-src")?.ifBlank { null } ?: img?.attr("src")),
                previewUrl = ParseUtils.url(img?.attr("data-preview-src")),
                downloadUrl = ParseUtils.url(
                    doc.select(".submission-controls-upper a, #submission-options a")
                        .firstOrNull { it.text().trim().equals("Download", ignoreCase = true) }
                        ?.attr("href"),
                ),
                descriptionHtml = doc.selectFirst(".submission-description-text")?.html()?.trim().orEmpty(),
                views = stat(stats, "Views"),
                commentCount = stat(stats, "Comments"),
                favoriteCount = stat(stats, "Favorites"),
                info = info(doc),
                keywords = doc.select(".submission-tags a[href*=@keywords]").map { it.text().trim() }.filter { it.isNotEmpty() },
                fav = doc.selectFirst("a[href^=/fav/], a[href^=/unfav/]")?.attr("href")?.let {
                    FavState(faved = it.startsWith("/unfav/"), path = it)
                },
                comments = doc.select("#comments-submission .comment_container").mapNotNull(ParseUtils::comment),
                moreFromArtist = doc.select("#minigallery figure[id^=sid_], #minigallery figure[id^=sid-]")
                    .mapNotNull { ParseUtils.preview(it, artist) }
                    .filter { it.id != id },
                commentForm = ParseUtils.replyForm(doc.selectFirst("form#add_comment_form")),
            ),
        )
    }

    private fun artist(doc: Document): UserRef? {
        val header = doc.selectFirst(".submission-description-artist") ?: return null
        val name = ParseUtils.userName(header.selectFirst("a[href*=/user/]")?.attr("href")) ?: return null
        val display = header.selectFirst(".c-usernameBlockSimple__displayName, .js-displayName")?.text()?.trim()
        val avatar = ParseUtils.url(header.selectFirst("img.submission-user-icon, img.avatar")?.attr("src"))
        return UserRef(name, display?.ifEmpty { null } ?: name, avatar)
    }

    private fun stat(stats: Element?, title: String): Int? =
        stats?.selectFirst("div[title=$title] > div")?.let { ParseUtils.number(it.text()) }

    /** Two parallel lists of spans: labels in `span.highlight`, values in the span after it. */
    private fun info(doc: Document): List<Pair<String, String>> {
        val box = doc.selectFirst(".submission-content-stats") ?: return emptyList()
        val spans = box.children().filter { it.tagName() == "span" }
        val labels = spans.firstOrNull { it.hasClass("highlight") }?.children()?.map { it.text().trim() } ?: return emptyList()
        val values = spans.firstOrNull { !it.hasClass("highlight") }?.children()?.map { it.text().trim() } ?: return emptyList()
        return labels.zip(values).filter { (label, value) -> label.isNotEmpty() && value.isNotEmpty() }
    }
}
