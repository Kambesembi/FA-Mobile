package com.kambesembi.famobile.data.model

enum class Rating { General, Mature, Adult;

    companion object {
        /** From FA's classes ("r-mature", "c-contentRating--adult") or the label ("Mature"). */
        fun from(value: String?): Rating = when {
            value == null -> General
            value.contains("adult", ignoreCase = true) -> Adult
            value.contains("mature", ignoreCase = true) -> Mature
            else -> General
        }
    }
}

enum class SubmissionKind { Image, Text, Audio, Flash, Other;

    companion object {
        /** From a figure's "t-image" class or the page's "page-content-type-image" class. */
        fun from(value: String?): SubmissionKind = when {
            value == null -> Other
            value.endsWith("image") -> Image
            value.endsWith("text") -> Text
            value.endsWith("audio") || value.endsWith("music") -> Audio
            value.endsWith("flash") -> Flash
            else -> Other
        }
    }
}

data class UserRef(
    /** URL name, as in /user/<name>/. */
    val name: String,
    val displayName: String,
    val avatarUrl: String? = null,
)

/** A thumbnail in a grid: the inbox, a gallery, "more from this artist". */
data class SubmissionPreview(
    val id: Long,
    val title: String,
    val artist: UserRef?,
    /** Thumbnail URL with "{size}" in place of FA's size (150…800). */
    val thumbnailTemplate: String?,
    /** Width / height, when FA says. */
    val aspectRatio: Float?,
    val rating: Rating,
    val kind: SubmissionKind,
    /** FA's data-tags (meta tags and keywords), for the account's blocklist; null when FA gave none. */
    val tags: List<String>? = null,
) {
    fun thumbnail(size: Int): String? = thumbnailTemplate?.replace("{size}", size.toString())
}

/** One entry in a grid that groups by day ("Today, 4 November 2026"). */
data class DatedPreview(val preview: SubmissionPreview, val day: String?)

data class InboxPage(
    val items: List<DatedPreview>,
    /** Path of the next (older) page, or null on the last one. */
    val nextPath: String?,
)

data class Comment(
    val id: Long,
    /** 0 for top-level comments, 1 for replies to them, … */
    val depth: Int,
    val author: UserRef?,
    /** Unix seconds. */
    val postedAt: Long?,
    val html: String,
    val byOriginalPoster: Boolean,
    /** Deleted or hidden: FA shows only a placeholder. */
    val hidden: Boolean,
)

data class FavState(val faved: Boolean, /** /fav/<id>/?key=… or /unfav/<id>/?key=… */ val path: String)

data class Submission(
    val id: Long,
    val title: String,
    val artist: UserRef,
    /** Unix seconds. */
    val postedAt: Long?,
    val kind: SubmissionKind,
    val rating: Rating,
    /** The full-size file shown on the page (for stories and music: their cover). */
    val imageUrl: String?,
    /** FA's 600px preview, shown while the full image loads. */
    val previewUrl: String?,
    /** The file itself (image, .docx, .mp3, …). */
    val downloadUrl: String?,
    val descriptionHtml: String,
    val views: Int?,
    val commentCount: Int?,
    val favoriteCount: Int?,
    /** Category, Theme, Species, Resolution, File Size, … as FA lists them. */
    val info: List<Pair<String, String>>,
    val keywords: List<String>,
    /** Null when logged out. */
    val fav: FavState?,
    val comments: List<Comment>,
    val moreFromArtist: List<SubmissionPreview>,
    /** FA's comment form; null when logged out or when comments are off. */
    val commentForm: ReplyForm? = null,
) {
    val url: String get() = "https://www.furaffinity.net/view/$id/"

    /** Width / height of the file, from FA's "Resolution" line ("1999 x 1843"), so the viewer can make room first. */
    val aspectRatio: Float?
        get() {
            val value = info.firstOrNull { it.first.equals("Resolution", ignoreCase = true) }?.second ?: return null
            val (w, h) = resolution.find(value)?.destructured ?: return null
            val width = w.toFloat()
            val height = h.toFloat()
            return if (width > 0f && height > 0f) width / height else null
        }

    private companion object {
        val resolution = Regex("""(\d+)\s*[x×]\s*(\d+)""")
    }
}

/** What a submission page gave us: the submission, or FA's reason for not showing it. */
sealed interface SubmissionResult {
    data class Loaded(val submission: Submission) : SubmissionResult
    data class Unavailable(val message: String) : SubmissionResult
}
