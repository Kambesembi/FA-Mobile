package com.kambesembi.famobile.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.model.SavedSubmission
import com.kambesembi.famobile.data.model.SubmissionPreview
import com.kambesembi.famobile.data.model.SubmissionResult
import com.kambesembi.famobile.ui.inbox.SubmissionCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Faving and saving straight from a grid, without opening the submission. The app provides one at
 * the top; its messages ("Added to favorites") show in the app-wide snackbar.
 */
class QuickActions(
    /** Submissions loaded so far: what is already faved shows a heart. */
    val loaded: StateFlow<Map<Long, SubmissionResult>>,
    /** The account's newest favorites on FA, for hearts on what wasn't opened in the app. */
    val favorited: StateFlow<Set<Long>>,
    /** How many fav actions are under way, for a progress bar. */
    val working: StateFlow<Int>,
    val saved: Flow<List<SavedSubmission>>,
    /** Loads a submission's page in the background, to learn whether it is faved. */
    val check: (Long) -> Unit,
    /** Favs these submissions (loading each page for FA's link first); already faved ones stay. */
    val fav: (List<SubmissionPreview>) -> Unit,
    val unfav: (SubmissionPreview) -> Unit,
    val toggleSaved: (SavedSubmission) -> Unit,
)

val LocalQuickActions = staticCompositionLocalOf<QuickActions?> { null }

/** What the app knows about a submission's place in the favorites. */
enum class FavKnowledge {
    /** Its page wasn't loaded yet. */
    Unknown,
    Faved,
    NotFaved,
    /** FA offers no fav button (your own submission, or it couldn't be shown). */
    Unavailable,
}

@Composable
fun rememberFavState(id: Long): FavKnowledge {
    val quick = LocalQuickActions.current ?: return FavKnowledge.Unavailable
    val loaded by quick.loaded.collectAsStateWithLifecycle()
    val favorited by quick.favorited.collectAsStateWithLifecycle()
    return when (val result = loaded[id]) {
        null -> if (id in favorited) FavKnowledge.Faved else FavKnowledge.Unknown
        is SubmissionResult.Unavailable -> FavKnowledge.Unavailable
        is SubmissionResult.Loaded -> when (result.submission.fav?.faved) {
            true -> FavKnowledge.Faved
            false -> FavKnowledge.NotFaved
            null -> FavKnowledge.Unavailable
        }
    }
}

/** Whether the app already knows this submission is in the favorites. */
@Composable
fun rememberFaved(id: Long): Boolean = rememberFavState(id) == FavKnowledge.Faved

/** A grid thumbnail: tap opens it, long-press offers +Fav (or -Fav) and Save. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickCard(preview: SubmissionPreview, thumbSize: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val quick = LocalQuickActions.current
    var menu by remember { mutableStateOf(false) }
    val favState = rememberFavState(preview.id)
    Box(modifier) {
        SubmissionCard(
            preview = preview,
            thumbSize = thumbSize,
            selected = false,
            faved = favState == FavKnowledge.Faved,
            modifier = Modifier.combinedClickable(
                onClick = onOpen,
                onLongClick = {
                    if (quick != null) {
                        menu = true
                        // The page tells whether it is faved; the menu follows as soon as it's in.
                        if (favState == FavKnowledge.Unknown) quick.check(preview.id)
                    }
                },
            ),
        )
        if (quick != null) {
            val savedList by quick.saved.collectAsStateWithLifecycle(emptyList())
            val saved = savedList.any { it.id == preview.id }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                when (favState) {
                    FavKnowledge.Unknown -> DropdownMenuItem(
                        text = { Text(stringResource(R.string.quick_fav)) },
                        leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
                        enabled = false,
                        onClick = {},
                    )
                    FavKnowledge.Faved -> DropdownMenuItem(
                        text = { Text(stringResource(R.string.quick_unfav)) },
                        leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                        onClick = {
                            menu = false
                            quick.unfav(preview)
                        },
                    )
                    FavKnowledge.NotFaved -> DropdownMenuItem(
                        text = { Text(stringResource(R.string.quick_fav)) },
                        leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
                        onClick = {
                            menu = false
                            quick.fav(listOf(preview))
                        },
                    )
                    FavKnowledge.Unavailable -> Unit
                }
                DropdownMenuItem(
                    text = { Text(stringResource(if (saved) R.string.quick_unsave else R.string.quick_save)) },
                    leadingIcon = { Icon(if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = null) },
                    onClick = {
                        menu = false
                        quick.toggleSaved(SavedSubmission(preview.id, preview.title, preview.thumbnailTemplate, preview.rating))
                    },
                )
            }
        }
    }
}
