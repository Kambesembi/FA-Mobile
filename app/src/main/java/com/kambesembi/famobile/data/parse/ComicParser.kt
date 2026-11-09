package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.data.FaUrls
import java.util.Locale
import org.jsoup.Jsoup

/** The series links an artist put in a description: "First", "« Prev", "Next »", "Latest". */
data class ComicLinks(val first: Long? = null, val prev: Long? = null, val next: Long? = null, val last: Long? = null) {
    val isEmpty: Boolean get() = first == null && prev == null && next == null && last == null
}

/** A title with a page number: "Aquarium Encounter - Page 9" → base "Aquarium Encounter", 9. */
data class NumberedTitle(val base: String, val number: Int)

/** Finding the pages of a comic, the way the FA-ImprovedLayout extension does it. */
object ComicParser {

    private val viewId = Regex("""/(?:view|full)/(\d+)""")
    private val numbered = Regex(
        """^(.*?)[\s\-–—:|,.(\[]*(?:page|pg|part|pt|chapter|ch|episode|ep|no)?\.?\s*#?\s*(\d{1,4})(?:\s*(?:/|of)\s*\d{1,4})?[\s)\]]*$""",
        RegexOption.IGNORE_CASE,
    )
    private val trailing = Regex("""[\s\-–—:|,.(\[#]+$""")
    private val nonWord = Regex("""[^a-z0-9]+""")

    /** The first link of each kind among the description's links to other submissions. */
    fun links(descriptionHtml: String): ComicLinks {
        if (descriptionHtml.isBlank()) return ComicLinks()
        val doc = Jsoup.parseBodyFragment(descriptionHtml, FaUrls.BASE + "/")
        var links = ComicLinks()
        for (a in doc.select("a[href]")) {
            val id = viewId.find(a.attr("href"))?.groupValues?.get(1)?.toLongOrNull() ?: continue
            val text = a.text().ifBlank { a.attr("title") }.trim().lowercase(Locale.ROOT)
            links = when (kind(text)) {
                Kind.First -> if (links.first == null) links.copy(first = id) else links
                Kind.Last -> if (links.last == null) links.copy(last = id) else links
                Kind.Prev -> if (links.prev == null) links.copy(prev = id) else links
                Kind.Next -> if (links.next == null) links.copy(next = id) else links
                null -> links
            }
        }
        return links
    }

    private enum class Kind { First, Last, Prev, Next }

    private fun kind(text: String): Kind? = when {
        text.isEmpty() -> null
        "first" in text -> Kind.First
        Regex("last|latest|newest").containsMatchIn(text) -> Kind.Last
        Regex("""prev|back|<<|«|‹|←|^<$""").containsMatchIn(text) -> Kind.Prev
        Regex("""next|>>|»|›|→|^>$""").containsMatchIn(text) -> Kind.Next
        else -> null
    }

    /** The base title and page number, or null when the title has none (a year doesn't count). */
    fun numberedTitle(title: String): NumberedTitle? {
        val m = numbered.find(title.trim()) ?: return null
        val number = m.groupValues[2].toIntOrNull() ?: return null
        if (number in 1900..2100) return null
        val base = m.groupValues[1].replace(trailing, "").trim()
        if (base.length < 3) return null
        return NumberedTitle(base, number)
    }

    /** Whether a submission looks like part of a series, from its page alone (no request). */
    fun isSeries(title: String, descriptionHtml: String): Boolean =
        numberedTitle(title) != null || !links(descriptionHtml).isEmpty

    /** For comparing base titles: lower case, words only. */
    fun normalize(base: String): String = base.lowercase(Locale.ROOT).replace(nonWord, " ").trim()

    /** FA's extended search for the artist's submissions with this base title. */
    fun searchText(base: String, artist: String): String {
        val words = base.replace(Regex("""["@!&|()\-]"""), " ").replace(Regex("""\s+"""), " ").trim()
        return "@title \"$words\" @lower $artist"
    }
}
