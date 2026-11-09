package com.kambesembi.famobile.data.notify

import com.kambesembi.famobile.data.model.ActivityKind
import com.kambesembi.famobile.data.model.ActivitySection
import com.kambesembi.famobile.data.model.DatedPreview
import com.kambesembi.famobile.data.model.NoteSummary

/** What a notification line says. */
enum class LineKind { Watch, SubmissionComment, JournalComment, Shout, Favorite, Journal, Note, Submission }

/** One new thing: who did it (display name, and URL name for the avatar), and what it is about (a title, a note's subject). */
data class NewsLine(val kind: LineKind, val actor: String, val subject: String?, val user: String? = null)

/**
 * Picks the newest items behind a counter that grew, so a notification can say "artist2 is now
 * watching you" instead of "1 new watcher". Pure functions over parsed pages, so they are testable.
 */
object NewsDetails {

    /** The pages whose items explain this category; null when nothing does. */
    fun source(category: Category): Source? = when (category) {
        Category.Comments, Category.Journals, Category.Favorites, Category.Watches -> Source.Activity
        Category.Notes -> Source.Notes
        Category.Submissions -> Source.Inbox
        Category.Tickets -> null
    }

    enum class Source { Activity, Notes, Inbox }

    private fun kinds(category: Category): Set<ActivityKind> = when (category) {
        Category.Comments -> setOf(ActivityKind.SubmissionComments, ActivityKind.JournalComments, ActivityKind.Shouts)
        Category.Journals -> setOf(ActivityKind.Journals)
        Category.Favorites -> setOf(ActivityKind.Favorites)
        Category.Watches -> setOf(ActivityKind.Watches)
        else -> emptySet()
    }

    private fun ActivityKind.line(): LineKind? = when (this) {
        ActivityKind.Watches -> LineKind.Watch
        ActivityKind.SubmissionComments -> LineKind.SubmissionComment
        ActivityKind.JournalComments -> LineKind.JournalComment
        ActivityKind.Shouts -> LineKind.Shout
        ActivityKind.Favorites -> LineKind.Favorite
        ActivityKind.Journals -> LineKind.Journal
        ActivityKind.Other -> null
    }

    /** The [count] newest items of this category on /msg/others/. */
    fun fromActivity(category: Category, sections: List<ActivitySection>, count: Int): List<NewsLine> {
        val wanted = kinds(category)
        return sections.filter { it.kind in wanted }
            .flatMap { it.items }
            .sortedByDescending { it.postedAt ?: 0L }
            .take(count)
            .mapNotNull { item ->
                val kind = item.kind.line() ?: return@mapNotNull null
                val actor = item.actor ?: return@mapNotNull null
                NewsLine(kind, actor.displayName, item.title, actor.name)
            }
    }

    /** The [count] newest unread notes, from the notes list. */
    fun fromNotes(notes: List<NoteSummary>, count: Int): List<NewsLine> =
        notes.filter { it.unread }.take(count).mapNotNull { note ->
            note.correspondent?.let { NewsLine(LineKind.Note, it.displayName, note.subject, it.name) }
        }

    /** The [count] newest inbox submissions. */
    fun fromInbox(items: List<DatedPreview>, count: Int): List<NewsLine> =
        items.take(count).mapNotNull { entry ->
            entry.preview.artist?.let { NewsLine(LineKind.Submission, it.displayName, entry.preview.title, it.name) }
        }

    /** At most this many lines per notification. */
    const val MAX_LINES = 5
}
