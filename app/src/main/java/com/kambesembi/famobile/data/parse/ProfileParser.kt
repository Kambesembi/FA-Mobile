package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.data.Words
import com.kambesembi.famobile.data.model.GalleryPage
import com.kambesembi.famobile.data.model.JournalPage
import com.kambesembi.famobile.data.model.JournalSummary
import com.kambesembi.famobile.data.model.ProfileResult
import com.kambesembi.famobile.data.model.Rating
import com.kambesembi.famobile.data.model.UserProfile
import com.kambesembi.famobile.data.model.UserRef
import com.kambesembi.famobile.data.model.WatchState
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * The user pages: /user/<name>/ (profile, stats, shouts), /gallery/, /scraps/, /favorites/ and
 * /journals/. They all share the `userpage-nav-header` with the avatar, names and Watch button.
 */
object ProfileParser {

    /** The header's user, or a bare one built from [name] when the page has no header. */
    fun user(doc: Document, name: String): UserRef {
        val details = doc.selectFirst("userpage-nav-user-details")
        val urlName = ParseUtils.userName(details?.selectFirst("a.c-usernameBlock__userName, a[href*=/user/]")?.attr("href")) ?: name
        val display = details?.selectFirst(".js-displayName")?.text()?.trim()?.ifEmpty { null } ?: urlName
        val avatar = ParseUtils.url(doc.selectFirst("userpage-nav-avatar img")?.attr("src"))
        return UserRef(urlName, display, avatar)
    }

    /**
     * The user's banner: FA's `<site-banner>` holds it on profiles that have one (FA's own seasonal
     * banner otherwise, which isn't the user's). The full-size one, whose 1850 x 300 shape is known.
     */
    fun banner(doc: Document): String? {
        val banner = doc.selectFirst("site-banner") ?: return null
        val src = banner.selectFirst("img[src*=profile_banner]")?.attr("src")
            ?: banner.select("source[srcset*=profile_banner]").firstOrNull { "mobile" !in it.attr("srcset") }
                ?.attr("srcset")?.trim()?.substringBefore(' ')
        return ParseUtils.url(src)
    }

    fun profile(doc: Document, name: String): ProfileResult {
        if (doc.selectFirst("userpage-nav-header") == null) {
            return ProfileResult.Unavailable(SystemMessageParser.message(doc) ?: Words.get(Words.Text.NoProfile))
        }
        val user = user(doc, name)
        val titleBlock = doc.selectFirst("userpage-nav-user-details .user-title")
        val title = titleBlock?.textNodes()?.joinToString("") { it.text() }
            ?.substringBefore('|')?.trim()?.ifEmpty { null }
        return ProfileResult.Loaded(
            UserProfile(
                user = user,
                title = title,
                registeredAt = titleBlock?.selectFirst(".popup_date")?.attr("data-time")?.toLongOrNull(),
                watch = watch(doc),
                notePath = doc.selectFirst("userpage-nav-interface-buttons a[href*=/newpm/]")?.attr("href"),
                profileHtml = doc.selectFirst(".userpage-layout-profile-container .userpage-profile")?.html()?.trim().orEmpty(),
                stats = stats(doc),
                info = info(doc),
                watchedBy = ParseUtils.number(doc.selectFirst("a[href*=/watchlist/to/]")?.text()),
                watching = ParseUtils.number(doc.selectFirst("a[href*=/watchlist/by/]")?.text()),
                recentSubmissions = doc.select("#gallery-latest-submissions figure[id^=sid-]")
                    .mapNotNull { ParseUtils.preview(it, user) },
                recentFavorites = doc.select("#gallery-latest-favorites figure[id^=sid-]")
                    .mapNotNull { ParseUtils.preview(it) },
                shouts = doc.select("#shouts .comment_container").mapNotNull(ParseUtils::comment),
                shoutForm = ParseUtils.replyForm(
                    doc.select("form").firstOrNull { it.selectFirst("textarea[name=shout]") != null },
                ),
                banner = banner(doc),
            ),
        )
    }

    /** FA's own Watch/Unwatch link. Logged out, its key is empty and it only leads to the login. */
    fun watch(doc: Document): WatchState? {
        val href = doc.selectFirst("a#watch-button")?.attr("href")?.trim().orEmpty()
        val key = href.substringAfter("key=", "")
        if (href.isEmpty() || key.isEmpty()) return null
        return WatchState(watching = href.startsWith("/unwatch/") || href.contains("/unwatch/"), path = href)
    }

    /** "Views: 1182879", "Submissions: 22", …: a `span.highlight` label and the number after it. */
    private fun stats(doc: Document): List<Pair<String, Int>> {
        val section = doc.select(".userpage-section-right").firstOrNull { it.selectFirst(".section-header h2")?.text()?.trim() == "Stats" }
            ?: return emptyList()
        return section.select("span.highlight").mapNotNull { label ->
            val value = generateSequence(label.nextSibling()) { it.nextSibling() }
                .takeWhile { it !is Element || it.tagName() != "span" }
                .filterIsInstance<TextNode>()
                .joinToString("") { it.text() }
            ParseUtils.number(value)?.let { label.text().trim().removeSuffix(":") to it }
        }
    }

    /** The "User Profile" table: Accepting Trades, Accepting Commissions, Species, … */
    private fun info(doc: Document): List<Pair<String, String>> =
        doc.select("#userpage-contact-item .table-row").mapNotNull { row ->
            val question = row.selectFirst(".userpage-profile-question") ?: return@mapNotNull null
            val label = question.text().trim()
            val value = row.textNodes().joinToString(" ") { it.text().trim() }.trim()
                .ifEmpty { row.children().filter { it != question }.joinToString(" ") { it.text().trim() }.trim() }
            if (label.isEmpty() || value.isEmpty()) null else label to value
        }

    /** /gallery/<name>/, /scraps/<name>/, /favorites/<name>/ (and their next pages). */
    fun gallery(doc: Document, name: String): GalleryPage {
        val owner = user(doc, name)
        val root = doc.selectFirst("section#gallery-gallery, section#gallery-favorites, .submission-list") ?: doc
        // Favorites are by other artists; their figures name them. Gallery figures name the owner.
        val items = root.select("figure[id^=sid-]").mapNotNull { ParseUtils.preview(it, owner) }
        return GalleryPage(items.distinctBy { it.id }, ParseUtils.buttonTarget(doc, "Next"))
    }

    /** /journals/<name>/: every journal in full, 25 per page, "Older" to the next. */
    fun journals(doc: Document): JournalPage {
        val items = doc.select("section[id^=jid:]").mapNotNull { section ->
            val id = section.id().removePrefix("jid:").toLongOrNull() ?: return@mapNotNull null
            val header = section.selectFirst(".section-header")
            JournalSummary(
                id = id,
                title = header?.selectFirst("h2")?.text()?.trim().orEmpty(),
                rating = Rating.from(header?.selectFirst("[class*=c-contentRating--]")?.className()),
                postedAt = header?.selectFirst(".popup_date")?.attr("data-time")?.toLongOrNull(),
                bodyHtml = section.selectFirst(".journal-body")?.html()?.trim().orEmpty(),
                commentCount = ParseUtils.number(section.selectFirst(".section-footer a[href*=/journal/] .font-large")?.text()),
            )
        }
        return JournalPage(items, ParseUtils.buttonTarget(doc, "Older"))
    }
}
