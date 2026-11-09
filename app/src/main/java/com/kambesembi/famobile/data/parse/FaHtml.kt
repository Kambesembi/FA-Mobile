package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.data.FaUrls
import org.jsoup.Jsoup

/** Where a link inside FA text should go. */
sealed interface FaLink {
    data class Submission(val id: Long) : FaLink
    data class User(val name: String) : FaLink
    data class Journal(val id: Long) : FaLink
    data class External(val url: String) : FaLink

    companion object {
        private val view = Regex("""^https?://(?:www\.)?furaffinity\.net/(?:view|full)/(\d+)""")
        private val user = Regex("""^https?://(?:www\.)?furaffinity\.net/user/([^/?#]+)""")
        private val journal = Regex("""^https?://(?:www\.)?furaffinity\.net/journal/(\d+)""")
        private val outside = Regex("""^https?://(?:www\.)?furaffinity\.net/externalurl/?\?(?:.*&)?q=([^&#]+)""")

        fun of(url: String): FaLink {
            val absolute = FaUrls.absolute(url)
            // FA sends outside links through its "you are leaving" page; the app asks itself instead.
            outside.find(absolute)?.let { match ->
                val target = runCatching { java.net.URLDecoder.decode(match.groupValues[1], "UTF-8") }.getOrNull()
                if (target != null && Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(target)) return of(target)
            }
            view.find(absolute)?.let { return Submission(it.groupValues[1].toLong()) }
            user.find(absolute)?.let { return User(it.groupValues[1]) }
            journal.find(absolute)?.let { return Journal(it.groupValues[1].toLong()) }
            return External(absolute)
        }
    }
}

/** Turns FA's description and comment HTML into the simple HTML Android's text renderer understands. */
object FaHtml {

    /** FA text with its user icons: [html] marks icon n as [ICON_START] n [ICON_END]; [icons] has their image URLs. */
    data class Prepared(val html: String, val icons: List<String>)

    const val ICON_START = ''
    const val ICON_END = ''

    /** [center] and [right] text, kept between these markers for [prepareWithIcons]'s caller to align. */
    const val CENTER_START = ''
    const val CENTER_END = ''
    const val RIGHT_START = ''
    const val RIGHT_END = ''

    /** The user icons become their name. */
    fun prepare(html: String): String = prepare(html, keepIcons = false).html

    /** The user icons stay, as markers for the avatars. */
    fun prepareWithIcons(html: String): Prepared = prepare(html, keepIcons = true)

    private fun prepare(html: String, keepIcons: Boolean): Prepared {
        if (html.isBlank()) return Prepared("", emptyList())
        val icons = mutableListOf<String>()
        val doc = Jsoup.parseBodyFragment(html, FaUrls.BASE + "/")
        doc.select("script, style, noscript").remove()
        for (link in doc.select("a[href]")) {
            val absolute = link.absUrl("href")
            if (absolute.isNotEmpty()) link.attr("href", absolute)
        }
        // :iconname: is the avatar with the name after it (inside the link), :nameicon: the avatar alone.
        for (img in doc.select("img")) {
            val name = img.attr("alt").ifBlank { img.attr("title") }.trim()
            val link = img.parents().firstOrNull { it.tagName() == "a" && it.attr("href").contains("/user/") }
            val src = img.absUrl("src")
            when {
                link == null || name.isEmpty() -> img.remove()
                keepIcons && src.isNotEmpty() -> {
                    img.replaceWith(org.jsoup.nodes.TextNode("$ICON_START${icons.size}$ICON_END"))
                    icons += src
                }
                // Text only: the name stands in for the avatar, unless it follows already.
                link.text().isBlank() -> img.replaceWith(org.jsoup.nodes.TextNode(name))
                else -> img.remove()
            }
        }
        // FA's [hr] is a styled span; Android's renderer knows no <hr> either.
        for (rule in doc.select("span.bbcode_hr, hr")) {
            // A line of its own: break before it too, unless it already starts a line.
            val before = generateSequence(rule.previousSibling()) { it.previousSibling() }
                .firstOrNull { it !is org.jsoup.nodes.TextNode || !it.isBlank }
            if (before != null && !(before is org.jsoup.nodes.Element && before.tagName() == "br")) rule.before("<br>")
            rule.after("<br>")
            rule.replaceWith(org.jsoup.nodes.TextNode("——————"))
        }
        if (keepIcons) {
            for (block in doc.select("code.bbcode_center, code.bbcode_right")) {
                val center = block.hasClass("bbcode_center")
                // Only the outermost block is aligned; one inside another just keeps its text.
                if (block.parents().none { it.hasClass("bbcode_center") || it.hasClass("bbcode_right") }) {
                    block.prependText((if (center) CENTER_START else RIGHT_START).toString())
                    block.appendText((if (center) CENTER_END else RIGHT_END).toString())
                }
            }
        }
        return Prepared(doc.body().html().trim(), icons)
    }
}
