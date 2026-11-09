package com.kambesembi.famobile.ui.notes

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.NotesState
import com.kambesembi.famobile.data.model.NoteFolder
import com.kambesembi.famobile.data.model.NoteSummary
import com.kambesembi.famobile.ui.components.Avatar
import com.kambesembi.famobile.ui.components.Bubble
import com.kambesembi.famobile.ui.components.readableSide

/** The notes list: folder chips on top, newest first, more while scrolling. */
@Composable
fun NotesScreen(
    state: NotesState,
    demo: Boolean,
    onFolder: (NoteFolder) -> Unit,
    onOpen: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    val loadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState, state.notes.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (state.loaded && last >= listState.layoutInfo.totalItemsCount - 6) loadMore() }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = readableSide(12.dp), end = readableSide(12.dp), top = 4.dp, bottom = 88.dp), // room for the compose button
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "folders") { Folders(state.folder, onFolder) }
        if (demo) {
            item(key = "demo") {
                Text(
                    stringResource(R.string.demo_mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.small)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        when {
            !state.loaded && state.error != null -> item(key = "error") {
                Bubble(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.error_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.size(6.dp))
                    Text(state.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
            !state.loaded -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            state.notes.isEmpty() -> item(key = "empty") {
                Bubble(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.notes_empty_folder), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            else -> {
                items(state.notes, key = { it.id }) { note -> NoteRow(note, state.folder == NoteFolder.Sent) { onOpen(note.id) } }
                item(key = "footer") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        when {
                            state.loadingMore -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                            state.error != null && state.nextPath != null -> Button(onClick = onLoadMore) { Text(stringResource(R.string.retry)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Folders(current: NoteFolder, onFolder: (NoteFolder) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NoteFolder.entries.forEach { folder ->
            FilterChip(
                selected = folder == current,
                onClick = { onFolder(folder) },
                label = { Text(stringResource(folder.label)) },
            )
        }
    }
}

@Composable
private fun NoteRow(note: NoteSummary, sent: Boolean, onClick: () -> Unit) {
    val who = note.correspondent
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(who?.avatarUrl, who?.displayName ?: "?", 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                note.subject,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (note.unread) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val time = note.sentAt?.let {
                DateUtils.getRelativeTimeSpanString(it * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
            }
            val name = who?.displayName?.let { if (sent) stringResource(R.string.notes_to, it) else it }
            Text(
                listOfNotNull(name, time).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (note.unread) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

val NoteFolder.label: Int
    get() = when (this) {
        NoteFolder.Inbox -> R.string.folder_inbox
        NoteFolder.Unread -> R.string.folder_unread
        NoteFolder.Sent -> R.string.folder_sent
        NoteFolder.HighPriority -> R.string.folder_high
        NoteFolder.MediumPriority -> R.string.folder_medium
        NoteFolder.LowPriority -> R.string.folder_low
        NoteFolder.Archive -> R.string.folder_archive
        NoteFolder.Trash -> R.string.folder_trash
    }
