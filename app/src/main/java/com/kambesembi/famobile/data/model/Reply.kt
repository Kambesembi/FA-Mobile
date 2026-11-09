package com.kambesembi.famobile.data.model

/**
 * One of FA's text forms: the comment form on submissions and journals, or the shout box on user
 * pages. The fields come from the page itself (action=reply, key=…, name=…), so only the text and,
 * for a reply, the comment id are filled in here.
 */
data class ReplyForm(
    val path: String,
    /** Hidden fields and the submit button, as on the page. */
    val fields: List<Pair<String, String>>,
    /** Name of the text area ("reply" for comments, "shout" for shouts). */
    val textField: String,
    /** The longest text FA takes (shouts: 255), when the page says. */
    val maxLength: Int?,
) {
    /** Comment forms can answer a single comment; the shout box can't. */
    val canReply: Boolean get() = fields.any { it.first == FIELD_REPLY_TO }

    /**
     * What to post. A reply does what FA's own script does: action=replyto and the comment's id in
     * the replyto field; a new comment leaves both as the page has them.
     */
    fun submit(text: String, replyTo: Long? = null): List<Pair<String, String>> {
        val out = fields.map { (name, value) ->
            when {
                replyTo != null && name == FIELD_ACTION && value == "reply" -> name to "replyto"
                replyTo != null && name == FIELD_REPLY_TO -> name to replyTo.toString()
                else -> name to value
            }
        }
        return out + (textField to text)
    }

    private companion object {
        const val FIELD_ACTION = "action"
        const val FIELD_REPLY_TO = "replyto"
    }
}

/** A journal page: the text, its comments and the form to add one. */
data class Journal(
    val id: Long,
    val title: String,
    val author: UserRef,
    /** Unix seconds. */
    val postedAt: Long?,
    val rating: Rating,
    val bodyHtml: String,
    val comments: List<Comment>,
    /** Null when logged out or when comments are off. */
    val commentForm: ReplyForm?,
) {
    val url: String get() = "https://www.furaffinity.net/journal/$id/"
}

sealed interface JournalResult {
    data class Loaded(val journal: Journal) : JournalResult
    data class Unavailable(val message: String) : JournalResult
}

/** What posting a comment or shout led to: the page as FA now shows it, or why it didn't work. */
sealed interface PostOutcome<out T> {
    data class Posted<T>(val page: T) : PostOutcome<T>
    data class Failed(val message: String?) : PostOutcome<Nothing>
}
