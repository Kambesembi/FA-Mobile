package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.data.FaUrls
import com.kambesembi.famobile.data.model.Note
import com.kambesembi.famobile.data.model.NoteDraft
import com.kambesembi.famobile.data.model.NoteListPage
import com.kambesembi.famobile.data.model.NoteSummary
import com.kambesembi.famobile.data.model.UserRef
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** FA's notes: the list at /msg/pms/ (50 per page) and a single note at /msg/pms/<page>/<id>/. */
object NotesParser {

    private val noteId = Regex("""/msg/pms/\d+/(\d+)/""")

    fun list(doc: Document): NoteListPage {
        val notes = doc.select(".c-noteListItem").mapNotNull { row ->
            val link = row.selectFirst("a.notelink") ?: return@mapNotNull null
            val id = noteId.find(link.attr("href"))?.groupValues?.get(1)?.toLongOrNull()
                ?: row.selectFirst("input[name=items[]]")?.attr("value")?.toLongOrNull()
                ?: return@mapNotNull null
            NoteSummary(
                id = id,
                subject = (row.selectFirst(".c-noteListItem__subject") ?: link).text().trim(),
                correspondent = row.selectFirst(".note-list-sender .c-usernameBlock")?.let(::user),
                sentAt = row.selectFirst(".note-list-senddate .popup_date[data-time]")?.attr("data-time")?.toLongOrNull(),
                unread = link.classNames().any { it == "unread" || it == "note-unread" },
            )
        }
        val next = doc.select(".note-pagination a[href*=/msg/pms/]").lastOrNull()
            ?.takeIf { !it.text().trim().startsWith("Prev", ignoreCase = true) }
            ?.attr("href")
        return NoteListPage(
            notes = notes,
            nextPath = next,
            sendKey = sendKey(doc),
            manageValue = doc.selectFirst("input[name=manage_notes]")?.attr("value")?.ifEmpty { null } ?: "1",
        )
    }

    fun note(doc: Document, id: Long): Note? {
        val section = doc.selectFirst(".messagecenter-mail-note-preview-pane section#message") ?: return null
        val people = section.select(".addresses .c-usernameBlock")
        val body = section.selectFirst(".section-body > .user-submitted-links")?.clone()
        body?.select(".noteWarningMessage, script")?.remove()
        val form = doc.selectFirst("form#note-form")
        val draft = form?.let {
            NoteDraft(
                to = it.selectFirst("input[name=to]")?.attr("value").orEmpty(),
                subject = it.selectFirst("input[name=subject]")?.attr("value").orEmpty(),
                message = it.selectFirst("textarea[name=message]")?.wholeText().orEmpty(),
            )
        }
        return Note(
            id = id,
            subject = section.selectFirst(".section-header h2")?.text()?.trim().orEmpty(),
            from = people.getOrNull(0)?.let(::user),
            to = people.getOrNull(1)?.let(::user),
            sentAt = section.selectFirst(".addresses .popup_date[data-time]")?.attr("data-time")?.toLongOrNull(),
            bodyHtml = body?.html()?.trim()?.replace(Regex("""(\s*<br>\s*)+$"""), "").orEmpty(),
            reply = draft,
            sendKey = sendKey(doc),
        )
    }

    private fun sendKey(doc: Document): String? =
        doc.selectFirst("form[action*=/msg/send] input[name=key]")?.attr("value")?.ifEmpty { null }

    private fun user(block: Element): UserRef? {
        val href = block.selectFirst("a[href*=/user/]")?.attr("href")
        val name = ParseUtils.userName(href) ?: return null
        val display = block.selectFirst(".js-displayName")?.text()?.trim().orEmpty()
        // The notes pages show no avatars; FA serves them by name too (the number in its own URLs only busts caches).
        return UserRef(name, display.ifEmpty { name }, FaUrls.avatar(name))
    }
}
