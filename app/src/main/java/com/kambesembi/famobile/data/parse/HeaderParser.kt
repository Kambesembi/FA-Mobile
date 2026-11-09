package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.data.FaUrls
import com.kambesembi.famobile.data.model.Blocklist
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Reads the logged-in user and unread counts from the header that every modern-theme FA page carries. */
object HeaderParser {

    private val userHref = Regex("/user/([^/?#]+)")
    private val counterText = Regex("""^([\d,.]+)\s*([A-Za-z])$""")
    private val counterTitle = Regex("""^([\d,.]+)\s+(.+)$""")
    private val myFaText = Regex("""\(\s*(.+?)\s*\)""")

    fun parse(doc: Document): PageHeader {
        val user = parseUser(doc)
        val present = user != null || doc.selectFirst("#ddmenu, nav, .mobile-navigation, .mobile-notification-bar") != null
        val body = doc.body()
        val blocklist = if (user != null && body.hasAttr("data-tag-blocklist")) {
            Blocklist.of(body.attr("data-tag-blocklist"), body.attr("data-user-blocklist"), body.attr("data-tag-blocklist-hide-tagless"))
        } else {
            null
        }
        val sfw = doc.selectFirst(".sfw-toggle input[type=checkbox]")?.hasAttr("checked")
        return PageHeader(user, if (user == null) Counts() else parseCounts(doc), present, blocklist, sfw)
    }

    private fun parseUser(doc: Document): LoggedInUser? {
        val avatar = doc.selectFirst("img.loggedin_user_avatar")
        val avatarLink = avatar?.parents()?.firstOrNull { it.tagName() == "a" }
        val mobileLink = doc.selectFirst("div.mobile-navigation h2 a[href*=/user/]")
        val myFa = doc.selectFirst("#my-username")

        val href = avatarLink?.attr("href")?.takeIf { userHref.containsMatchIn(it) }
            ?: mobileLink?.attr("href")
            ?: myFa?.attr("href")
            ?: return null
        val username = userHref.find(href)?.groupValues?.get(1) ?: return null

        val displayName = avatar?.attr("alt")?.trim()?.takeIf { it.isNotEmpty() }
            ?: mobileLink?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: myFa?.text()?.let { myFaText.find(it)?.groupValues?.get(1) }
            ?: username
        val avatarUrl = avatar?.attr("src")?.takeIf { it.isNotBlank() }?.let(FaUrls::absolute)
        return LoggedInUser(username, displayName, avatarUrl)
    }

    private fun parseCounts(doc: Document): Counts {
        // The desktop bar and the mobile bar carry the same counters; read one of them.
        val links = doc.select(".message-bar-desktop a.notification-container")
            .ifEmpty { doc.select("a.notification-container") }
        val found = mutableMapOf<Char, Int>()
        for (link in links) {
            val (kind, count) = readCounter(link) ?: continue
            found.putIfAbsent(kind, count)
        }
        return Counts(
            submissions = found['S'] ?: 0,
            watches = found['W'] ?: 0,
            comments = found['C'] ?: 0,
            favorites = found['F'] ?: 0,
            journals = found['J'] ?: 0,
            notes = found['N'] ?: 0,
            tickets = found['T'] ?: 0,
        )
    }

    /** "6S" → ('S', 6). Falls back to the title ("6 Submission Notifications") and the link target. */
    private fun readCounter(link: Element): Pair<Char, Int>? {
        val text = link.text().trim()
        counterText.find(text)?.let { m ->
            val n = number(m.groupValues[1]) ?: return@let
            return m.groupValues[2].uppercase()[0] to n
        }
        val title = counterTitle.find(link.attr("title").trim()) ?: return null
        val n = number(title.groupValues[1]) ?: return null
        val kind = kindFromWords(title.groupValues[2]) ?: kindFromHref(link.attr("href")) ?: return null
        return kind to n
    }

    private fun number(s: String): Int? = s.replace(",", "").replace(".", "").toIntOrNull()

    private fun kindFromWords(words: String): Char? {
        val w = words.lowercase()
        return when {
            "submission" in w -> 'S'
            "watch" in w -> 'W'
            "comment" in w || "shout" in w -> 'C'
            "favorite" in w || "favourite" in w -> 'F'
            "journal" in w -> 'J'
            "note" in w -> 'N'
            "ticket" in w -> 'T'
            else -> null
        }
    }

    private fun kindFromHref(href: String): Char? = when {
        "/msg/submissions" in href -> 'S'
        "/msg/pms" in href -> 'N'
        "#watches" in href -> 'W'
        "#comments" in href || "#shouts" in href -> 'C'
        "#favorites" in href -> 'F'
        "#journals" in href -> 'J'
        "troubletickets" in href -> 'T'
        else -> null
    }
}
