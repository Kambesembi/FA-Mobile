package com.kambesembi.famobile.data.notify

import com.kambesembi.famobile.data.parse.Counts

/** FA's counter categories, each with its own notification channel. [tab] is the tab a tap opens. */
enum class Category(val tab: Int) {
    Submissions(0),
    Comments(1),
    Journals(1),
    Favorites(1),
    Watches(1),
    Notes(2),
    Tickets(1),
    ;

    fun of(counts: Counts): Int = when (this) {
        Submissions -> counts.submissions
        Comments -> counts.comments
        Journals -> counts.journals
        Favorites -> counts.favorites
        Watches -> counts.watches
        Notes -> counts.notes
        Tickets -> counts.tickets
    }
}

/**
 * A category that grew: [added] since the last look, [total] unread now, and [lines] saying who did
 * what (newest first), when the check could find out.
 */
data class News(val category: Category, val added: Int, val total: Int, val lines: List<NewsLine> = emptyList())

object ActivityCheck {

    /**
     * What grew between the counters last seen (in the app or by the previous check) and now. With
     * nothing seen yet there is no news: the first check only takes stock.
     */
    fun news(seen: Counts?, now: Counts): List<News> {
        if (seen == null) return emptyList()
        return Category.entries.mapNotNull { c ->
            val added = c.of(now) - c.of(seen)
            if (added > 0) News(c, added, c.of(now)) else null
        }
    }

    fun encode(counts: Counts): String = Category.entries.joinToString(",") { it.of(counts).toString() }

    fun decode(text: String?): Counts? {
        val n = text?.split(',')?.map { it.toIntOrNull() ?: return null } ?: return null
        if (n.size != Category.entries.size) return null
        return Counts(
            submissions = n[Category.Submissions.ordinal],
            comments = n[Category.Comments.ordinal],
            journals = n[Category.Journals.ordinal],
            favorites = n[Category.Favorites.ordinal],
            watches = n[Category.Watches.ordinal],
            notes = n[Category.Notes.ordinal],
            tickets = n[Category.Tickets.ordinal],
        )
    }
}
