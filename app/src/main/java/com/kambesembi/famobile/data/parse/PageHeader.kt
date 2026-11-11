package com.kambesembi.famobile.data.parse

import com.kambesembi.famobile.data.model.Blocklist

/** The account a page was served to. [username] is the URL name (/user/<username>/), [displayName] what FA shows. */
data class LoggedInUser(
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
)

/** FA's unread counters from the header ("6S", "3C", …). */
data class Counts(
    val submissions: Int = 0,
    val watches: Int = 0,
    val comments: Int = 0,
    val favorites: Int = 0,
    val journals: Int = 0,
    val notes: Int = 0,
    val tickets: Int = 0,
) {
    /** Everything the Activity tab shows. */
    val activity: Int get() = watches + comments + favorites + journals
}

/** What every FA page tells us about the session, wherever it was loaded from. */
data class PageHeader(
    val user: LoggedInUser?,
    val counts: Counts,
    /**
     * False for pages without FA's menu bar, like its bare "System Error" page for a deleted
     * submission: no user there doesn't mean logged out.
     */
    val present: Boolean = true,
    /** The account's tag and user blocklist; null when the page doesn't carry it (logged out). */
    val blocklist: Blocklist? = null,
    /** FA's own SFW switch on this page (its "sfw" cookie); null when the page has none. */
    val sfw: Boolean? = null,
    /** FA's own "Log Out", which ends the session on FA's side; null when the page shows none. */
    val logout: LogoutLink? = null,
)

/** FA's logout as the page has it: a form to post (with its hidden fields), or a plain link. */
data class LogoutLink(val path: String, val fields: List<Pair<String, String>>, val post: Boolean)
