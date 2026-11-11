package com.kambesembi.famobile.data

import com.kambesembi.famobile.data.model.PostOutcome
import com.kambesembi.famobile.data.model.ReplyForm
import org.jsoup.nodes.Document

/** A parsed page, or FA's message when it showed one instead. */
internal data class Parsed<T>(val page: T?, val message: String?)

/**
 * Posts [text] with [form] and reads the page FA answers with. It counts as posted once a comment
 * (or shout) shows up that wasn't there before; if FA's answer doesn't show one yet, the page is
 * loaded once more. Whatever page was read last goes to [keep], so the screen shows FA's state.
 */
internal suspend fun <T> FaClient.postReply(
    form: ReplyForm,
    text: String,
    replyTo: Long?,
    reloadPath: String,
    before: Set<Long>,
    parse: (Document) -> Parsed<T>,
    ids: (T) -> Set<Long>,
    keep: (T) -> Unit,
): PostOutcome<T> {
    val answer = parsed(post(form.path, form.submit(text, replyTo)), parse)
    answer.page?.let(keep)
    if (answer.page != null && (ids(answer.page) - before).isNotEmpty()) return PostOutcome.Posted(answer.page)
    // FA's message instead of the page: that's its reason (too short, too fast, …).
    if (answer.page == null && answer.message != null) return PostOutcome.Failed(answer.message)
    val again = parsed(page(reloadPath), parse)
    again.page?.let(keep)
    return if (again.page != null && (ids(again.page) - before).isNotEmpty()) {
        PostOutcome.Posted(again.page)
    } else {
        PostOutcome.Failed(again.message)
    }
}
