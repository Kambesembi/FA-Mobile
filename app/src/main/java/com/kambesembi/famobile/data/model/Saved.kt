package com.kambesembi.famobile.data.model

/** An artist saved for later (the flag on a profile). */
data class SavedArtist(val name: String, val displayName: String, val avatarUrl: String?)

/** A submission saved for later (the flag in the viewer), with what its card needs. */
data class SavedSubmission(val id: Long, val title: String, val thumbnailTemplate: String?, val rating: Rating) {
    fun preview(): SubmissionPreview =
        SubmissionPreview(id, title, null, thumbnailTemplate, aspectRatio = null, rating = rating, kind = SubmissionKind.Image)
}

/**
 * Saved items as plain text for the settings store: one line each, fields separated by tabs. Tabs and
 * line breaks can't occur in FA names, ids or thumbnail URLs; titles have them replaced by spaces.
 */
object SavedCodec {

    fun encodeArtists(artists: List<SavedArtist>): String =
        artists.joinToString("\n") { listOf(it.name, clean(it.displayName), it.avatarUrl.orEmpty()).joinToString("\t") }

    fun decodeArtists(text: String?): List<SavedArtist> =
        lines(text).mapNotNull { fields ->
            val name = fields.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SavedArtist(name, fields.getOrNull(1)?.ifBlank { null } ?: name, fields.getOrNull(2)?.ifBlank { null })
        }

    fun encodeSubmissions(submissions: List<SavedSubmission>): String =
        submissions.joinToString("\n") {
            listOf(it.id.toString(), clean(it.title), it.thumbnailTemplate.orEmpty(), it.rating.name).joinToString("\t")
        }

    fun decodeSubmissions(text: String?): List<SavedSubmission> =
        lines(text).mapNotNull { fields ->
            val id = fields.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            SavedSubmission(
                id = id,
                title = fields.getOrNull(1).orEmpty(),
                thumbnailTemplate = fields.getOrNull(2)?.ifBlank { null },
                rating = fields.getOrNull(3)?.let { runCatching { Rating.valueOf(it) }.getOrNull() } ?: Rating.General,
            )
        }

    private fun lines(text: String?): List<List<String>> =
        text.orEmpty().split('\n').filter { it.isNotBlank() }.map { it.split('\t') }

    private fun clean(value: String) = value.replace('\t', ' ').replace('\n', ' ')
}
