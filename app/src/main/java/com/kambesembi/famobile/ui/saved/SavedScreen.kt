package com.kambesembi.famobile.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.model.SavedArtist
import com.kambesembi.famobile.data.model.SavedSubmission
import com.kambesembi.famobile.ui.components.Avatar
import com.kambesembi.famobile.ui.components.Bubble
import com.kambesembi.famobile.ui.components.LocalContentFilter
import com.kambesembi.famobile.ui.components.TopBarHeight
import com.kambesembi.famobile.ui.components.gridThumbSize
import com.kambesembi.famobile.ui.components.rememberFaved
import com.kambesembi.famobile.ui.components.submissionGridCells
import com.kambesembi.famobile.ui.inbox.SubmissionCard

/** What the Saved page shows and what it can do. */
class SavedActions(
    val openSubmissions: (ids: List<Long>, start: Long) -> Unit,
    val openUser: (name: String) -> Unit,
    val removeSubmission: (SavedSubmission) -> Unit,
    val removeArtist: (SavedArtist) -> Unit,
    val back: () -> Unit,
)

/** Everything flagged for later, kept on this phone: submissions and artists, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(submissions: List<SavedSubmission>, artists: List<SavedArtist>, actions: SavedActions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    expandedHeight = TopBarHeight,
                    title = { Text(stringResource(R.string.saved_title)) },
                    navigationIcon = {
                        IconButton(onClick = actions.back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text(stringResource(R.string.saved_tab_submissions) + count(submissions.size)) },
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text(stringResource(R.string.saved_tab_artists) + count(artists.size)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (tab == 0) Submissions(submissions, actions) else Artists(artists, actions)
        }
    }
}

private fun count(n: Int) = if (n > 0) " ($n)" else ""

@Composable
private fun Submissions(saved: List<SavedSubmission>, actions: SavedActions) {
    if (saved.isEmpty()) {
        Empty(stringResource(R.string.saved_empty_submissions))
        return
    }
    val thumbSize = gridThumbSize()
    val filter = LocalContentFilter.current
    val shown = saved.filterNot { filter.hides(it.rating) }
    LazyVerticalStaggeredGrid(
        columns = submissionGridCells(),
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalItemSpacing = 5.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(shown, key = { it.id }) { item ->
            Box {
                SubmissionCard(
                    preview = item.preview(),
                    thumbSize = thumbSize,
                    selected = false,
                    faved = rememberFaved(item.id),
                    modifier = Modifier.clickable { actions.openSubmissions(shown.map { it.id }, item.id) },
                )
                IconButton(
                    onClick = { actions.removeSubmission(item) },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.saved_remove),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                            .padding(4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Artists(saved: List<SavedArtist>, actions: SavedActions) {
    if (saved.isEmpty()) {
        Empty(stringResource(R.string.saved_empty_artists))
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(saved, key = { it.name }) { artist ->
            Bubble(Modifier.fillMaxWidth(), onClick = { actions.openUser(artist.name) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(artist.avatarUrl, artist.displayName, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            artist.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "@" + artist.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { actions.removeArtist(artist) }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.saved_remove))
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
