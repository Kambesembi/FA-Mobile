package com.kambesembi.famobile.data.model

/** /watch/<name>/?key=… or /unwatch/<name>/?key=…, FA's own link from the profile header. */
data class WatchState(val watching: Boolean, val path: String)

/** A user page: the header every profile page shares, plus what only the home page shows. */
data class UserProfile(
    val user: UserRef,
    /** The user title ("Watcher", "Artist", …), when set. */
    val title: String?,
    /** Unix seconds. */
    val registeredAt: Long?,
    /** Null when logged out, or on your own page. */
    val watch: WatchState?,
    /** /newpm/<name>/, when FA offers it. */
    val notePath: String?,
    val profileHtml: String,
    /** "Views", "Submissions", "Favs", … in FA's order. */
    val stats: List<Pair<String, Int>>,
    /** "Accepting Trades: No", "Species: Fox", … */
    val info: List<Pair<String, String>>,
    val watchedBy: Int?,
    val watching: Int?,
    val recentSubmissions: List<SubmissionPreview>,
    val recentFavorites: List<SubmissionPreview>,
    val shouts: List<Comment>,
    /** The shout box; null when logged out, on your own page, or when shouts are off. */
    val shoutForm: ReplyForm? = null,
    /** The user's own profile banner (1850 x 300 at most); null without one. */
    val banner: String? = null,
)

sealed interface ProfileResult {
    data class Loaded(val profile: UserProfile) : ProfileResult
    data class Unavailable(val message: String) : ProfileResult
}

/** The gallery, scraps and favorites pages. */
enum class GalleryKind(val path: String) {
    Gallery("gallery"),
    Scraps("scraps"),
    Favorites("favorites");

    fun firstPage(user: String) = "/$path/$user/"
}

data class GalleryPage(val items: List<SubmissionPreview>, val nextPath: String?)

data class JournalSummary(
    val id: Long,
    val title: String,
    val rating: Rating,
    /** Unix seconds. */
    val postedAt: Long?,
    val bodyHtml: String,
    val commentCount: Int?,
) {
    val url: String get() = "https://www.furaffinity.net/journal/$id/"
}

data class JournalPage(val items: List<JournalSummary>, val nextPath: String?)
