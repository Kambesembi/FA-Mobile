package com.kambesembi.famobile.data.model

/** The kinds of notification on FA's /msg/others/ page, by the id of their section there. */
enum class ActivityKind(val sectionId: String) {
    Watches("messages-watches"),
    SubmissionComments("messages-comments-submission"),
    JournalComments("messages-comments-journal"),
    Shouts("messages-shouts"),
    Favorites("messages-favorites"),
    Journals("messages-journals"),
    Other(""),
    ;

    companion object {
        fun from(sectionId: String): ActivityKind = entries.firstOrNull { it.sectionId == sectionId } ?: Other
    }
}

/** A form field FA's message center submits: a checkbox, or the button that was pressed. */
data class FormField(val name: String, val value: String)

/**
 * One notification. [checkbox] is its checkbox ("journals[]" = id), [actor] who did it, [title] and
 * [link] what it is about (a submission, a journal, a comment on one); [text] the whole line as FA
 * shows it, for kinds the app doesn't know yet.
 */
data class ActivityItem(
    val kind: ActivityKind,
    val checkbox: FormField,
    val actor: UserRef?,
    val title: String?,
    val link: String?,
    val postedAt: Long?,
    val text: String,
) {
    val key: String get() = "${checkbox.name}=${checkbox.value}"
}

/** A section of /msg/others/, with the buttons that remove some or all of its items. */
data class ActivitySection(
    val kind: ActivityKind,
    val sectionId: String,
    val title: String,
    val items: List<ActivityItem>,
    val remove: FormField?,
    val nuke: FormField?,
)
