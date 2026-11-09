package com.kambesembi.famobile.ui.notes

import android.text.format.DateUtils
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.NoteResult
import com.kambesembi.famobile.data.NotesRepository
import com.kambesembi.famobile.data.model.Note
import com.kambesembi.famobile.data.model.NoteMove
import com.kambesembi.famobile.data.model.UserRef
import com.kambesembi.famobile.ui.components.Avatar
import com.kambesembi.famobile.ui.components.Bubble
import com.kambesembi.famobile.ui.components.FurryLoader
import com.kambesembi.famobile.ui.components.HtmlText
import com.kambesembi.famobile.ui.components.TopBarHeight
import com.kambesembi.famobile.ui.components.readableSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One note: who, when, the message, and reply / unread / archive / trash. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    id: Long,
    repo: NotesRepository,
    /** App scope, so a move finishes after the screen is left. */
    scope: CoroutineScope,
    onLink: (String) -> Unit,
    onReply: (Note) -> Unit,
    onBack: () -> Unit,
) {
    val opened by repo.opened.collectAsStateWithLifecycle()
    val result = opened[id]
    val snackbar = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.note_action_failed)

    LaunchedEffect(id) { repo.open(id) }

    fun move(to: NoteMove) {
        if (busy) return
        busy = true
        scope.launch {
            val ok = repo.move(setOf(id), to)
            busy = false
            if (ok) uiScope.launch { onBack() } else uiScope.launch { snackbar.showSnackbar(failed) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                expandedHeight = TopBarHeight,
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (result is NoteResult.Loaded) {
                        IconButton(onClick = { move(NoteMove.Unread) }, enabled = !busy) {
                            Icon(Icons.Outlined.MarkEmailUnread, contentDescription = stringResource(R.string.note_mark_unread))
                        }
                        IconButton(onClick = { move(NoteMove.Archive) }, enabled = !busy) {
                            Icon(Icons.Outlined.Archive, contentDescription = stringResource(R.string.note_archive))
                        }
                        IconButton(onClick = { move(NoteMove.Trash) }, enabled = !busy) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.note_trash))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            val note = (result as? NoteResult.Loaded)?.note
            if (note?.reply != null) {
                ExtendedFloatingActionButton(
                    onClick = { onReply(note) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                    text = { Text(stringResource(R.string.note_reply)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (result) {
                null -> FurryLoader()
                is NoteResult.Failed -> Bubble(Modifier.align(Alignment.Center).padding(24.dp).fillMaxWidth()) {
                    Text(result.message, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = { uiScope.launch { repo.open(id, force = true) } }) { Text(stringResource(R.string.retry)) }
                }
                is NoteResult.Loaded -> NoteContent(result.note, onLink)
            }
        }
    }
}

@Composable
private fun NoteContent(note: Note, onLink: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = readableSide(12.dp), end = readableSide(12.dp), bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                note.subject,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        item {
            Bubble(Modifier.fillMaxWidth()) {
                Person(R.string.note_from, note.from, note.sentAt)
                Spacer(Modifier.size(8.dp))
                Person(R.string.note_to, note.to, null)
            }
        }
        item {
            Bubble(Modifier.fillMaxWidth()) {
                HtmlText(note.bodyHtml, onLink = onLink, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun Person(label: Int, user: UserRef?, sentAt: Long?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        Avatar(user?.avatarUrl, user?.displayName ?: "?", 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(user?.displayName ?: "—", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            val detail = listOfNotNull(
                user?.name?.let { "@$it" },
                sentAt?.let { DateUtils.getRelativeTimeSpanString(it * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() },
            ).joinToString("  ·  ")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
