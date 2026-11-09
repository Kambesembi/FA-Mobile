package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.data.FaUrls
import com.kambesembi.famobile.data.model.ActivityItem
import com.kambesembi.famobile.data.model.ActivityKind
import com.kambesembi.famobile.data.model.ActivitySection
import com.kambesembi.famobile.data.model.FormField
import com.kambesembi.famobile.data.model.UserRef
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * FA's /msg/others/ page: one section per kind (watches, comments, favorites, journals, …), a list
 * item with a checkbox per notification. Field and button names are read from the page rather than
 * assumed, so a kind the app doesn't know yet still lists and removes.
 */
object ActivityParser {

    fun parse(doc: Document): List<ActivitySection> {
        val form = doc.selectFirst("form[action*=/msg/others]") ?: return emptyList()
        return form.select("section[id^=messages-]").mapNotNull { section(it) }
    }

    private fun section(section: Element): ActivitySection? {
        val kind = ActivityKind.from(section.id())
        val items = section.select("li").mapNotNull { item(kind, it) }
        if (items.isEmpty()) return null
        val header = section.selectFirst(".section-header .highlight, .section-header h2, h2, h3")
        return ActivitySection(
            kind = kind,
            sectionId = section.id(),
            title = header?.text()?.trim().orEmpty().ifEmpty { section.id().removePrefix("messages-") },
            items = items,
            remove = button(section, "remove-"),
            nuke = button(section, "nuke-"),
        )
    }

    private fun item(kind: ActivityKind, li: Element): ActivityItem? {
        val box = li.selectFirst("input[type=checkbox][name]") ?: return null
        val value = box.attr("value").ifEmpty { return null }
        // The name link rather than the avatar link next to it (watches show both).
        val actorLink = li.selectFirst(".c-usernameBlockSimple a[href*=/user/], .c-usernameBlock a[href*=/user/]")
            ?: li.select("a[href*=/user/]").firstOrNull { ParseUtils.userName(it.attr("href")) != null }
        val actor = actorLink?.let { a ->
            val name = ParseUtils.userName(a.attr("href")) ?: return@let null
            val display = a.selectFirst(".c-usernameBlockSimple__displayName, .js-displayName")?.text()?.trim()
                ?: a.text().trim()
            val avatar = li.selectFirst("img.avatar, img[src*=a.furaffinity.net]")?.attr("src")
            // Journals and some other kinds carry no avatar; FA serves every user's under their name.
            UserRef(name, display.ifEmpty { name }, ParseUtils.url(avatar) ?: FaUrls.avatar(name))
        }
        val target = li.selectFirst("a[href*=/view/], a[href*=/journal/], a[href*=/full/]")
        return ActivityItem(
            kind = kind,
            checkbox = FormField(box.attr("name"), value),
            actor = actor,
            title = target?.text()?.trim()?.ifEmpty { null },
            link = target?.attr("href")?.let(ParseUtils::url),
            postedAt = li.selectFirst(".popup_date[data-time]")?.attr("data-time")?.toLongOrNull(),
            text = li.text().trim(),
        )
    }

    private fun button(section: Element, prefix: String): FormField? {
        val b = section.select("button[name], input[type=submit][name]").firstOrNull { it.attr("name").startsWith(prefix) }
            ?: return null
        return FormField(b.attr("name"), b.attr("value").ifEmpty { b.text().trim() })
    }
}
