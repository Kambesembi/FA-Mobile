package com.kambesembi.famobile.data.model

import java.text.Normalizer
import java.util.Locale

/** What FA calls the kinds of submission (`submission_type` on /submit/), and the files each takes. */
enum class UploadKind(val value: String, val extensions: Set<String>) {
    Artwork("submission", setOf("jpg", "jpeg", "png", "gif")),
    Story("story", setOf("txt", "doc", "docx", "odt", "rtf", "pdf")),
    Poetry("poetry", setOf("txt", "doc", "docx", "odt", "rtf", "pdf")),
    Music("music", setOf("mp3", "wav", "mid", "midi")),
    ;

    val isWriting: Boolean get() = this == Story || this == Poetry

    companion object {
        /** The kind a file most likely is, from its type and name; null when FA takes it as none. */
        fun of(mimeType: String, fileName: String): UploadKind? {
            val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return when {
                // Any picture: the app converts what FA doesn't take.
                mimeType.startsWith("image/") -> Artwork
                mimeType.startsWith("audio/") || ext in Music.extensions -> Music
                ext in Story.extensions || mimeType == "application/pdf" || mimeType.startsWith("text/") ||
                    mimeType.contains("msword") || mimeType.contains("wordprocessingml") ||
                    mimeType.contains("opendocument.text") || mimeType.contains("rtf") -> Story
                ext in Artwork.extensions -> Artwork
                else -> null
            }
        }
    }
}

/** A file copied into the app, ready to be sent. [width] and [height] are known for pictures. */
data class UploadFile(
    val path: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val width: Int? = null,
    val height: Int? = null,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

/** One of FA's choices, kept with its label so it can be found again if FA renumbers its lists. */
data class UploadChoice(val value: String, val label: String)

/** One file to upload and what belongs to it alone. */
data class UploadItem(
    val file: UploadFile,
    val title: String,
    val kind: UploadKind,
    /** A cover for a story or a song (FA's "thumbnail"). */
    val thumbnail: UploadFile? = null,
    /** This file's own rating; null follows the form's. */
    val rating: Rating? = null,
    /** This file's own gender keywords; null follows the form's. */
    val genders: List<String>? = null,
    /** Not with this form: the file gets a form of its own once this one is sent. */
    val separate: Boolean = false,
) {
    val ownSettings: Boolean get() = rating != null || genders != null
}

/** Everything the files of one upload share. */
data class UploadDetails(
    val rating: Rating? = null,
    val description: String = "",
    val keywords: List<String> = emptyList(),
    val category: UploadChoice? = null,
    val theme: UploadChoice? = null,
    val species: UploadChoice? = null,
    val folders: List<UploadChoice> = emptyList(),
    val scraps: Boolean = false,
    val lockComments: Boolean = false,
    /** Remove GPS data from photos before they leave the phone. */
    val stripLocation: Boolean = true,
) {
    /** What the next upload starts with: the choices, not the words. */
    fun preset(): UploadDetails = copy(description = "", keywords = emptyList())

    /** The gender keywords among the keywords. */
    val genders: List<String> get() = keywords.filter { it.lowercase(Locale.ROOT) in Keywords.GENDERS }

    /** What [item] is sent with: these details, with the file's own rating and genders where it has them. */
    fun forItem(item: UploadItem): UploadDetails = copy(
        rating = item.rating ?: rating,
        keywords = item.genders?.let { own ->
            Keywords.merge(keywords.filterNot { it.lowercase(Locale.ROOT) in Keywords.GENDERS }, own)
        } ?: keywords,
    )
}

/** The form being filled in. */
data class UploadDraft(
    val items: List<UploadItem> = emptyList(),
    val details: UploadDetails = UploadDetails(),
) {
    val isEmpty: Boolean get() = items.isEmpty() && details.description.isBlank() && details.keywords.isEmpty()

    /** The files this form sends: those not set apart, or the first when all are. */
    val sending: List<UploadItem> get() = items.filterNot { it.separate }.ifEmpty { items.take(1) }

    /** The files that get a form of their own afterwards, in order. */
    val waiting: List<UploadItem> get() = sending.let { s -> items.filter { it !in s } }

    /** A file still without a rating: neither its own nor the form's. */
    val missingRating: Boolean get() = sending.any { (it.rating ?: details.rating) == null }

    /**
     * After sending: the next form, with the waiting files and the last choices (no words). Its first
     * file is the one it's for; the others still wait their turn.
     */
    fun next(preset: UploadDetails): UploadDraft {
        val rest = waiting.map { it.copy(rating = null, genders = null) }
        return UploadDraft(items = rest.mapIndexed { i, item -> if (i == 0) item.copy(separate = false) else item }, details = preset)
    }
}

enum class UploadStatus { Queued, Sending, Finishing, Done, Failed }

/** One file on its way to FA, or there already. */
data class UploadJob(
    val id: String,
    val item: UploadItem,
    val details: UploadDetails,
    val status: UploadStatus = UploadStatus.Queued,
    /** 0..1 while the file is sent. */
    val progress: Float = 0f,
    val submissionId: Long? = null,
    /** FA's reason, or what went wrong. */
    val message: String? = null,
    /** Stopped at a Cloudflare check: the app has to get through it before a retry. */
    val needsCheck: Boolean = false,
    /** Network failures so far; after a few the upload gives up until retried. */
    val attempts: Int = 0,
    val created: Long = 0,
    /** The FA account (URL name, lower case) it was queued under; null for uploads from before accounts were kept. */
    val account: String? = null,
) {
    val active: Boolean get() = status == UploadStatus.Queued || status == UploadStatus.Sending || status == UploadStatus.Finishing
}

/** FA's lists for the details: from the browse page, or from FA's own upload form once one was seen. */
data class UploadLists(
    val categories: List<FilterOption> = emptyList(),
    val themes: List<FilterOption> = emptyList(),
    val species: List<FilterOption> = emptyList(),
    /** True when they come from the upload form itself. */
    val exact: Boolean = false,
) {
    val isEmpty: Boolean get() = categories.isEmpty() && themes.isEmpty() && species.isEmpty()
}

/**
 * FA's keyword rules (from its upload form): a-z, 0-9 and _ only, separated by spaces, at least
 * [MIN_COUNT] of them, [MAX_CHARS] characters in all. Single letters are dropped.
 */
object Keywords {
    const val MIN_COUNT = 3
    const val MAX_CHARS = 500

    /** FA's gender boxes on the upload form: they only add these keywords. */
    val GENDERS = listOf("male", "female", "trans_male", "trans_female", "intersex", "non_binary")

    private val separators = Regex("""[\s,]+""")
    private val marks = Regex("""\p{M}+""")
    private val joiners = Regex("""[-.]+""")
    private val other = Regex("""[^a-z0-9_]""")
    private val underscores = Regex("""_+""")

    /** A word as FA takes it: "Café-Art" → "cafe_art"; null when nothing is left. */
    fun clean(word: String): String? {
        val plain = Normalizer.normalize(word.trim().trimStart('#'), Normalizer.Form.NFD).replace(marks, "")
        return plain.lowercase(Locale.ROOT)
            .replace(joiners, "_")
            .replace(other, "")
            .replace(underscores, "_")
            .trim('_')
            .takeIf { it.length > 1 }
    }

    /** What was typed or pasted, as keywords (commas and spaces both separate). */
    fun split(text: String): List<String> = text.split(separators).mapNotNull(::clean)

    /** [current] with [added] appended, without repeats, as many as fit in FA's field. */
    fun merge(current: List<String>, added: List<String>): List<String> {
        val seen = current.map { it.lowercase(Locale.ROOT) }.toMutableSet()
        var length = join(current).length
        val out = current.toMutableList()
        for (word in added) {
            if (!seen.add(word.lowercase(Locale.ROOT))) continue
            val grown = length + (if (out.isEmpty()) 0 else 1) + word.length
            if (grown > MAX_CHARS) break
            out += word
            length = grown
        }
        return out
    }

    /** As FA's field takes them. */
    fun join(words: List<String>): String = words.joinToString(" ")
}

object UploadNames {
    /** FA's longest title. */
    const val MAX_TITLE = 60

    private val noise = Regex("""(?i)^(img|dsc|pxl|screenshot|photo|image)[_-]?\d[\d_-]*$""")

    /** A title from a file name: "my_new-pic_final.png" → "My new pic final"; camera names give "". */
    fun titleFrom(fileName: String): String {
        val base = fileName.substringBeforeLast('.').trim()
        if (noise.matches(base)) return ""
        val words = base.replace(Regex("""[_\-.]+"""), " ").replace(Regex("""\s+"""), " ").trim()
        return words.replaceFirstChar { it.titlecase(Locale.getDefault()) }.take(MAX_TITLE).trim()
    }
}
