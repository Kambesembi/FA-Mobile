package com.kambesembi.famobile.data.model

/** FA's note folders, by their /controls/switchbox/<path>/ name. */
enum class NoteFolder(val path: String) {
    Inbox("inbox"),
    Unread("unread"),
    Sent("sent"),
    HighPriority("high_prio"),
    MediumPriority("medium_prio"),
    LowPriority("low_prio"),
    Archive("archive"),
    Trash("trash"),
}

/** Where a note can go: FA's "move_to" values. */
enum class NoteMove(val value: String) { Trash("trash"), Archive("archive"), Unread("unread") }

/** A row of the notes list. [correspondent] is the sender, or the recipient in Sent. */
data class NoteSummary(
    val id: Long,
    val subject: String,
    val correspondent: UserRef?,
    val sentAt: Long?,
    val unread: Boolean,
)

data class NoteListPage(
    val notes: List<NoteSummary>,
    val nextPath: String?,
    /** The compose form's key, needed to send a note. */
    val sendKey: String?,
    /** The value FA's list form sends as "manage_notes". */
    val manageValue: String,
)

/** FA's reply form under a note: recipient, "RE:" subject and the quoted text, ready to edit. */
data class NoteDraft(val to: String, val subject: String, val message: String)

data class Note(
    val id: Long,
    val subject: String,
    val from: UserRef?,
    val to: UserRef?,
    val sentAt: Long?,
    val bodyHtml: String,
    val reply: NoteDraft?,
    val sendKey: String?,
)
