package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.data.FaUrls
import com.kambesembi.famobile.data.model.Comment
import com.kambesembi.famobile.data.model.Rating
import com.kambesembi.famobile.data.model.ReplyForm
import com.kambesembi.famobile.data.model.SubmissionKind
import com.kambesembi.famobile.data.model.SubmissionPreview
import com.kambesembi.famobile.data.model.UserRef
import kotlin.math.roundToInt
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal object ParseUtils {

    private val userPath = Regex("/user/([^/?#]+)")
    private val digits = Regex("""\d[\d,.]*""")
    private val thumbSize = Regex("""@\d+-""")
    private val viewId = Regex("""/view/(\d+)""")
    private val widthPercent = Regex("""width:\s*([\d.]+)%""")
    private val digitsOnly = Regex("""\d+""")

    fun userName(href: String?): String? = href?.let { userPath.find(it)?.groupValues?.get(1) }

    fun viewId(href: String?): Long? = href?.let { viewId.find(it)?.groupValues?.get(1)?.toLongOrNull() }

    /** "1,234" → 1234; null when there is no number. */
    fun number(text: String?): Int? =
        text?.let { digits.find(it)?.value?.replace(",", "")?.replace(".", "")?.toIntOrNull() }

    fun url(src: String?): String? = src?.trim()?.takeIf { it.isNotEmpty() }?.let(FaUrls::absolute)

    /** "//t.furaffinity.net/123@300-456.jpg" → "https://t.furaffinity.net/123@{size}-456.jpg". */
    fun thumbnailTemplate(src: String?): String? =
        url(src)?.let { if (thumbSize.containsMatchIn(it)) thumbSize.replaceFirst(it, "@{size}-") else it }

    fun classWithPrefix(element: Element, prefix: String): String? =
        element.classNames().firstOrNull { it.startsWith(prefix) }

    /**
     * A thumbnail figure as FA draws it in the inbox, galleries and search
     * (`figure#sid-123.r-general.t-image`) and in "more from this artist" (`figure#sid_123.r-general`).
     */
    fun preview(figure: Element, fallbackArtist: UserRef? = null): SubmissionPreview? {
        val id = figure.id().removePrefix("sid-").removePrefix("sid_").toLongOrNull() ?: return null
        val img = figure.selectFirst("img")
        val viewLink = figure.select("figcaption a[href*=/view/]").firstOrNull()
        val artistLink = figure.select("figcaption a[href*=/user/]").firstOrNull()
        val title = viewLink?.attr("title")?.ifBlank { viewLink.text() }
            ?: img?.attr("alt")?.ifBlank { img.attr("title") }
            ?: ""
        val artist = artistLink?.let { link ->
            userName(link.attr("href"))?.let { UserRef(it, link.attr("title").trim().ifBlank { link.text().trim() }) }
        } ?: fallbackArtist
        val width = img?.attr("data-width")?.toFloatOrNull()
        val height = img?.attr("data-height")?.toFloatOrNull()
        return SubmissionPreview(
            id = id,
            title = title.trim(),
            artist = artist,
            thumbnailTemplate = thumbnailTemplate(img?.attr("src")),
            aspectRatio = if (width != null && height != null && width > 0 && height > 0) width / height else null,
            rating = Rating.from(classWithPrefix(figure, "r-")),
            kind = SubmissionKind.from(classWithPrefix(figure, "t-") ?: "t-image"),
            tags = img?.takeIf { it.hasAttr("data-tags") }?.attr("data-tags")?.lowercase()
                ?.split(Regex("\\s+"))?.filter { it.isNotEmpty() },
        )
    }

    /** A comment or shout (`.comment_container` with an `a.comment_anchor#cid:123` or `#shout-123`). */
    fun comment(container: Element): Comment? {
        val id = container.selectFirst("a.comment_anchor")?.id()?.let { digitsOnly.find(it)?.value }?.toLongOrNull() ?: return null
        // FA indents replies by narrowing them: 100% for top level, 3% less per level.
        val width = widthPercent.find(container.attr("style"))?.groupValues?.get(1)?.toFloatOrNull() ?: 100f
        val depth = ((100f - width) / 3f).roundToInt().coerceIn(0, 12)
        val userLink = container.selectFirst(".c-usernameBlock__userName, comment-username a[href*=/user/]")
        val name = ParseUtils.userName(userLink?.attr("href"))
        val author = name?.let {
            UserRef(
                name = it,
                displayName = container.selectFirst(".js-displayName")?.text()?.trim()?.ifEmpty { null } ?: it,
                avatarUrl = ParseUtils.url(container.selectFirst("img.comment_useravatar")?.attr("src")),
            )
        }
        val text = container.selectFirst("comment-user-text")
        return Comment(
            id = id,
            depth = depth,
            author = author,
            postedAt = container.selectFirst("comment-date .popup_date, .popup_date")?.attr("data-time")?.toLongOrNull(),
            html = (text?.selectFirst(".user-submitted-links") ?: text)?.html()?.trim().orEmpty(),
            byOriginalPoster = container.selectFirst(".comment_op_marker") != null,
            hidden = text == null || author == null,
        )
    }

    /**
     * A form with a text area (comments, shouts): where it posts, its hidden fields and submit
     * button as the page has them, and the text area's name and length limit.
     */
    fun replyForm(form: Element?): ReplyForm? {
        form ?: return null
        val area = form.selectFirst("textarea[name]") ?: return null
        val path = form.attr("action").substringBefore('#').trim().ifEmpty { return null }
        val hidden = form.select("input[type=hidden][name]")
            .filter { it.attr("name").isNotBlank() }
            .map { it.attr("name") to it.attr("value") }
        val submit = form.select("button[type=submit][name], input[type=submit][name]")
            .firstOrNull { it.attr("name").isNotBlank() }
            ?.let { it.attr("name") to it.attr("value").ifEmpty { it.text().trim() } }
        return ReplyForm(
            path = path,
            fields = hidden + listOfNotNull(submit),
            textField = area.attr("name"),
            maxLength = area.attr("maxlength").toIntOrNull()?.takeIf { it > 0 },
        )
    }

    /** The target of a "Next"/"Older" button: FA uses a GET form or a plain link. */
    fun buttonTarget(doc: Document, label: String): String? {
        doc.select("form[method=get]").firstOrNull { form ->
            form.select("button").any { it.text().trim().equals(label, ignoreCase = true) }
        }?.attr("action")?.takeIf { it.isNotBlank() }?.let { return it }
        return doc.select("a[href].button").firstOrNull { it.text().trim().equals(label, ignoreCase = true) }
            ?.attr("href")?.takeIf { it.isNotBlank() }
    }
}

/** FA's "System Message" pages: login required, submission not found, account disabled, … */
object SystemMessageParser {
    fun message(doc: Document): String? {
        val notice = doc.selectFirst("section.notice-message .section-body") ?: return bare(doc)
        val text = (notice.selectFirst(".redirect-message") ?: notice.selectFirst("p") ?: notice).text().trim()
        return text.ifEmpty { null }
    }

    /** FA's bare error page: one section headed "System Error", its text, and a "go back" button. */
    private fun bare(doc: Document): String? {
        val section = doc.select("section").firstOrNull { s ->
            s.selectFirst(".section-header h2")?.text()?.contains("System Error", ignoreCase = true) == true
        } ?: return null
        val body = section.selectFirst(".section-body")?.clone() ?: return null
        body.select(".alignright, a.button, .button").remove()
        return body.text().trim().ifEmpty { null }
    }
}
