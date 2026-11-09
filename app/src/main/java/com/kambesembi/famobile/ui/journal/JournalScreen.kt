package com.kambesembi.famobile.ui.journal

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.JournalRepository
import com.kambesembi.famobile.data.model.Comment
import com.kambesembi.famobile.data.model.Journal
import com.kambesembi.famobile.data.model.JournalResult
import com.kambesembi.famobile.ui.components.Avatar
import com.kambesembi.famobile.ui.components.Bubble
import com.kambesembi.famobile.ui.components.FurryLoader
import com.kambesembi.famobile.ui.components.HtmlText
import com.kambesembi.famobile.ui.components.PullRefresh
import com.kambesembi.famobile.ui.components.ReplySheet
import com.kambesembi.famobile.ui.components.TopBarHeight
import com.kambesembi.famobile.ui.components.readableSide
import com.kambesembi.famobile.ui.util.openExternally
import com.kambesembi.famobile.ui.util.shareLink
import com.kambesembi.famobile.ui.viewer.CommentItem
import com.kambesembi.famobile.ui.viewer.CommentsTitle
import com.kambesembi.famobile.ui.viewer.Notice
import com.kambesembi.famobile.ui.viewer.RatingLabel
import com.kambesembi.famobile.ui.viewer.relativeTime
import java.io.IOException
import kotlinx.coroutines.launch

class JournalActions(
    val repo: JournalRepository,
    /** Any link in the text or comments: submissions, users and journals open in the app. */
    val openLink: (String) -> Unit,
    val openUser: (name: String) -> Unit,
    val back: () -> Unit,
)

/** A journal with its comments, and the form to comment or reply. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(id: Long, actions: JournalActions) {
    val loaded by actions.repo.loaded.collectAsStateWithLifecycle()
    val result = loaded[id]
    var error by remember(id) { mutableStateOf<String?>(null) }
    var attempt by remember(id) { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val url = "https://www.furaffinity.net/journal/$id/"

    LaunchedEffect(id, attempt) {
        if (actions.repo.loaded.value[id] == null) {
            error = null
            try {
                actions.repo.load(id)
            } catch (e: IOException) {
                error = e.message ?: "Network error"
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                expandedHeight = TopBarHeight,
                title = { Text(stringResource(R.string.journal_screen), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = actions.back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                            onClick = {
                                menu = false
                                context.shareLink((result as? JournalResult.Loaded)?.journal?.title ?: url, url)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.open_in_browser)) },
                            leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
                            onClick = {
                                menu = false
                                context.openExternally(url)
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                result is JournalResult.Loaded -> PullRefresh(
                    isRefreshing = refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            try {
                                actions.repo.load(id, force = true)
                            } catch (e: IOException) {
                                snackbar.showSnackbar(e.message ?: "Network error")
                            } finally {
                                refreshing = false
                            }
                        }
                    },
                ) {
                    JournalContent(result.journal, actions, snackbar)
                }
                result is JournalResult.Unavailable -> Notice(result.message, url, null)
                error != null -> Notice(error!!, null) { attempt++ }
                else -> FurryLoader()
            }
        }
    }
}

@Composable
private fun JournalContent(journal: Journal, actions: JournalActions, snackbar: SnackbarHostState) {
    var composing by remember { mutableStateOf(false) }
    var replyTo by remember { mutableStateOf<Comment?>(null) }
    val scope = rememberCoroutineScope()
    val posted = stringResource(R.string.reply_posted)
    val form = journal.commentForm

    LazyColumn(
        contentPadding = PaddingValues(start = readableSide(12.dp), end = readableSide(12.dp), top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "head") {
            Bubble(Modifier.fillMaxWidth()) {
                Text(journal.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = journal.author.name.isNotEmpty()) { actions.openUser(journal.author.name) },
                ) {
                    Avatar(journal.author.avatarUrl, journal.author.displayName, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            journal.author.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RatingLabel(journal.rating)
                            journal.postedAt?.let {
                                Text(relativeTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (journal.bodyHtml.isNotBlank()) {
                    Spacer(Modifier.size(14.dp))
                    HtmlText(journal.bodyHtml, onLink = actions.openLink, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item(key = "comments-title") {
            Column {
                CommentsTitle(
                    stringResource(R.string.section_comments) + " (${journal.comments.size})",
                    onWrite = form?.let { { replyTo = null; composing = true } },
                )
                if (journal.comments.isEmpty()) {
                    Text(
                        stringResource(R.string.no_comments),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        items(journal.comments, key = { "c${it.id}" }) { comment ->
            CommentItem(
                comment,
                actions.openLink,
                onReply = if (form?.canReply == true && !comment.hidden) {
                    { replyTo = comment; composing = true }
                } else {
                    null
                },
            )
        }
    }

    if (composing) {
        val target = replyTo
        ReplySheet(
            title = if (target == null) stringResource(R.string.reply_new_comment) else stringResource(R.string.reply_to, target.author?.displayName.orEmpty()),
            hint = stringResource(R.string.reply_hint),
            quote = target,
            maxLength = form?.maxLength,
            draftKey = "journal-${journal.id}-${target?.id ?: 0}",
            send = { text -> actions.repo.comment(journal, text, target?.id) },
            onPosted = {
                composing = false
                scope.launch { snackbar.showSnackbar(posted) }
            },
            onDismiss = { composing = false },
        )
    }
}
