package com.kambesembi.famobile.ui.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.UploadRepository
import com.kambesembi.famobile.data.model.UploadJob
import com.kambesembi.famobile.data.model.UploadStatus
import com.kambesembi.famobile.ui.components.Bubble
import com.kambesembi.famobile.ui.components.TopBarHeight
import com.kambesembi.famobile.ui.components.readableSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class UploadsActions(
    val repo: UploadRepository,
    val scope: CoroutineScope,
    val back: () -> Unit,
    val newUpload: () -> Unit,
    val openSubmission: (Long) -> Unit,
    /** A failed upload went back into the form. */
    val edit: () -> Unit,
)

/** What's on its way to FA, what got there, and what didn't (with FA's reason). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadsScreen(actions: UploadsActions) {
    val store by actions.repo.store.collectAsStateWithLifecycle()
    val jobs = store.jobs.sortedByDescending { it.created }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                expandedHeight = TopBarHeight,
                navigationIcon = {
                    IconButton(onClick = actions.back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = { Text(stringResource(R.string.uploads_title), fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (jobs.any { it.status == UploadStatus.Done }) {
                        TextButton(onClick = actions.repo::clearFinished) { Text(stringResource(R.string.uploads_clear_done)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (jobs.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = actions.newUpload,
                    icon = { Icon(Icons.Outlined.CloudUpload, contentDescription = null) },
                    text = { Text(stringResource(R.string.uploads_new)) },
                )
            }
        },
    ) { padding ->
        if (jobs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.uploads_empty), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = actions.newUpload) {
                        Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.uploads_new))
                    }
                }
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = readableSide(16.dp), end = readableSide(16.dp), top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(jobs, key = { it.id }) { job ->
                JobCard(
                    job = job,
                    onOpen = { job.submissionId?.let(actions.openSubmission) },
                    onRetry = { actions.scope.launch { actions.repo.retry(job.id) } },
                    onEdit = {
                        actions.repo.edit(job.id)
                        actions.edit()
                    },
                    onRemove = { actions.repo.remove(job.id) },
                )
            }
        }
    }
}

@Composable
private fun JobCard(job: UploadJob, onOpen: () -> Unit, onRetry: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit) {
    Bubble(Modifier.fillMaxWidth(), onClick = if (job.status == UploadStatus.Done) onOpen else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Preview(job.item, Modifier.size(56.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    job.item.title.ifBlank { job.item.file.name },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val (text, color) = when (job.status) {
                    UploadStatus.Queued -> (job.message ?: stringResource(R.string.uploads_queued)) to MaterialTheme.colorScheme.onSurfaceVariant
                    UploadStatus.Sending -> stringResource(R.string.uploads_sending, (job.progress * 100).toInt()) to MaterialTheme.colorScheme.onSurfaceVariant
                    UploadStatus.Finishing -> stringResource(R.string.uploads_finishing) to MaterialTheme.colorScheme.onSurfaceVariant
                    UploadStatus.Done -> stringResource(R.string.uploads_done) to MaterialTheme.colorScheme.primary
                    UploadStatus.Failed -> (job.message ?: stringResource(R.string.uploads_failed)) to MaterialTheme.colorScheme.error
                }
                Text(text, style = MaterialTheme.typography.bodySmall, color = color)
            }
            when (job.status) {
                UploadStatus.Done -> Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                UploadStatus.Failed -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                else -> Unit
            }
        }
        when (job.status) {
            UploadStatus.Sending -> {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
            }
            UploadStatus.Finishing -> {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            UploadStatus.Queued -> Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.cancel)) }
            }
            UploadStatus.Failed -> Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.uploads_remove)) }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.uploads_edit)) }
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            UploadStatus.Done -> Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onOpen) { Text(stringResource(R.string.uploads_open)) }
            }
        }
    }
}
