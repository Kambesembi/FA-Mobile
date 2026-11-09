package com.kambesembi.famobile.data.model

/**
 * What the account blocks on FA (Settings → Tag Blocklist, and blocked users), as every logged-in page
 * carries it on `<body>`. Thumbnails carry `data-tags`: four meta tags (u_artist, c_category, t_theme,
 * s_species) and the keywords. Matching follows FA's own censor.js.
 */
data class Blocklist(
    val tags: Set<String> = emptySet(),
    /** Blocked users as FA's u_ meta tags ("u_some_name"). */
    val users: Set<String> = emptySet(),
    /** Also hide submissions without any keyword. */
    val hideTagless: Boolean = false,
) {
    val isEmpty: Boolean get() = tags.isEmpty() && users.isEmpty() && !hideTagless

    /** Whether a thumbnail with these [thumbTags] is blocked; unknown tags (null) never are. */
    fun blocks(thumbTags: List<String>?): Boolean {
        if (thumbTags == null || isEmpty) return false
        if (thumbTags.any { it in tags || it in users }) return true
        return hideTagless && thumbTags.size <= META_TAGS
    }

    companion object {
        val None = Blocklist()
        private const val META_TAGS = 4

        /** From the body's data-tag-blocklist, data-user-blocklist and data-tag-blocklist-hide-tagless. */
        fun of(tagList: String, userList: String, hideTagless: String): Blocklist = Blocklist(
            tags = words(tagList).toSet(),
            users = words(userList).map(::userTag).toSet(),
            hideTagless = hideTagless.trim().toIntOrNull()?.let { it != 0 } ?: false,
        )

        private fun words(list: String) = list.lowercase().split(Regex("""\s+""")).filter { it.isNotEmpty() }

        /** A username as FA's u_ tag: anything but letters, digits, _ and - becomes _, no doubled or edge _. */
        fun userTag(name: String): String =
            "u_" + name.replace(Regex("""[^\w-]"""), "_").replace(Regex("_{2,}"), "_").trim('_', ' ')
    }
}
