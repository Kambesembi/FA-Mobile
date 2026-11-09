package com.kambesembi.famobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.kambesembi.famobile.data.model.Blocklist
import com.kambesembi.famobile.data.model.Rating
import com.kambesembi.famobile.data.model.SubmissionPreview

/** What the grids leave out: Mature and Adult in SFW mode, and what the account blocks on FA. */
data class ContentFilter(val sfw: Boolean = false, val blocklist: Blocklist = Blocklist.None) {
    fun hides(preview: SubmissionPreview): Boolean = hides(preview.rating) || blocklist.blocks(preview.tags)

    fun hides(rating: Rating): Boolean = sfw && rating != Rating.General
}

/** The app's root provides it. */
val LocalContentFilter = compositionLocalOf { ContentFilter() }

/** These submissions as the content filter lets them show. */
@Composable
fun List<SubmissionPreview>.visible(): List<SubmissionPreview> {
    val filter = LocalContentFilter.current
    return if (!filter.sfw && filter.blocklist.isEmpty) this else filterNot(filter::hides)
}
