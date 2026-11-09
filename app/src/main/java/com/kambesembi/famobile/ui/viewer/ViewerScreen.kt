package com.kambesembi.famobile.ui.viewer

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import coil.request.ImageRequest
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.SubmissionRepository
import com.kambesembi.famobile.data.model.Comment
import com.kambesembi.famobile.data.model.Rating
import com.kambesembi.famobile.data.model.SavedSubmission
import com.kambesembi.famobile.data.model.Submission
import com.kambesembi.famobile.data.model.SubmissionKind
import com.kambesembi.famobile.data.model.SubmissionResult
import com.kambesembi.famobile.data.parse.ComicParser
import com.kambesembi.famobile.data.parse.FaLink
import com.kambesembi.famobile.ui.components.Avatar
import com.kambesembi.famobile.ui.components.Bubble
import com.kambesembi.famobile.ui.components.FurryLoader
import com.kambesembi.famobile.ui.components.HtmlText
import com.kambesembi.famobile.ui.components.LocalOpenOutside
import com.kambesembi.famobile.ui.components.MediaWidth
import com.kambesembi.famobile.ui.components.QuickCard
import com.kambesembi.famobile.ui.components.ReplySheet
import com.kambesembi.famobile.ui.components.TopBarHeight
import com.kambesembi.famobile.ui.components.readableSide
import com.kambesembi.famobile.ui.components.visible
import com.kambesembi.famobile.ui.inbox.ratingColor
import com.kambesembi.famobile.ui.util.downloadFile
import com.kambesembi.famobile.ui.util.openExternally
import com.kambesembi.famobile.ui.util.shareLink
import java.io.IOException
import java.text.NumberFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** What the viewer needs from the rest of the app. */
class ViewerActions(
    val repo: SubmissionRepository,
    /** App scope: a fav or removal keeps going when the screen is left. */
    val scope: CoroutineScope,
    val userAgent: String,
    val openSubmissions: (ids: List<Long>, start: Long) -> Unit,
    /** Opens a user page in the app. */
    val openUser: (name: String) -> Unit,
    /** Opens a journal in the app. */
    val openJournal: (id: Long) -> Unit,
    /** Saved submissions, for the flag. */
    val saved: Flow<List<SavedSubmission>>,
    val toggleSaved: (SavedSubmission) -> Unit,
    val back: () -> Unit,
    /** Opens the comic reader at this submission; null hides "Read as comic". */
    val openComic: ((Long) -> Unit)? = null,
)

/**
 * Swipe through [ids] (the inbox, or "more from this artist"), starting at [startId]. In the inbox,
 * "Done" removes the submission and the next one slides in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(ids: List<Long>, startId: Long, actions: ViewerActions) {
    val pager = rememberPagerState(initialPage = ids.indexOf(startId).coerceAtLeast(0)) { ids.size }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ids.isEmpty()) { if (ids.isEmpty()) actions.back() }

    // Load the neighbour ahead of time, so swiping on feels instant (one page, as a reader would):
    // its page and FA's preview image, not the full file, to spare slow connections.
    val context = LocalContext.current
    LaunchedEffect(pager.settledPage, ids) {
        ids.getOrNull(pager.settledPage + 1)?.let { next ->
            actions.scope.launch {
                val result = runCatching { actions.repo.load(next) }.getOrNull()
                val preview = (result as? SubmissionResult.Loaded)?.submission?.previewUrl ?: return@launch
                context.imageLoader.enqueue(ImageRequest.Builder(context).data(preview).build())
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                expandedHeight = TopBarHeight,
                title = {
                    if (ids.size > 1) {
                        Text(
                            stringResource(R.string.viewer_position, pager.currentPage + 1, ids.size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pager,
            key = { ids.getOrElse(it) { -1L } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) { page ->
            val id = ids.getOrNull(page) ?: return@HorizontalPager
            SubmissionPage(id, actions, snackbar)
        }
    }
}

@Composable
private fun SubmissionPage(id: Long, actions: ViewerActions, snackbar: SnackbarHostState) {
    val loaded by actions.repo.loaded.collectAsStateWithLifecycle()
    val result = loaded[id]
    var error by remember(id) { mutableStateOf<String?>(null) }
    var attempt by remember(id) { mutableIntStateOf(0) }

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

    when {
        result is SubmissionResult.Loaded -> SubmissionContent(result.submission, actions, snackbar)
        result is SubmissionResult.Unavailable -> Notice(result.message, "https://www.furaffinity.net/view/$id/", null)
        error != null -> Notice(error!!, null) { attempt++ }
        else -> FurryLoader()
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SubmissionContent(submission: Submission, actions: ViewerActions, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    var zoom by rememberSaveable { mutableStateOf(false) }
    var faving by remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()
    // The comment form: open for a new comment (null) or a reply to that comment.
    var composing by remember { mutableStateOf(false) }
    var replyTo by remember { mutableStateOf<Comment?>(null) }
    val posted = stringResource(R.string.reply_posted)
    val networkError = stringResource(R.string.error_network)
    // Part of a series (the artist's Prev/Next links, or "Page 9" in the title): it can be read all at once.
    val series = remember(submission.id) { ComicParser.isSeries(submission.title, submission.descriptionHtml) }
    val moreFromArtist = submission.moreFromArtist.visible()

    val openOutside = LocalOpenOutside.current ?: context::openExternally
    val onLink: (String) -> Unit = { url ->
        when (val link = FaLink.of(url)) {
            is FaLink.Submission -> actions.openSubmissions(listOf(link.id), link.id)
            is FaLink.User -> actions.openUser(link.name)
            is FaLink.Journal -> actions.openJournal(link.id)
            is FaLink.External -> openOutside(link.url)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = readableSide(6.dp, MediaWidth), end = readableSide(6.dp, MediaWidth), bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "media") { Media(submission, onZoom = { zoom = true }) }

        val openComic = actions.openComic
        if (openComic != null && series) {
            item(key = "comic") {
                FilledTonalButton(onClick = { openComic(submission.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.comic_read))
                }
            }
        }

        item(key = "title") { TitleBlock(submission) { onLink("https://www.furaffinity.net/user/${submission.artist.name}/") } }

        item(key = "actions") {
            Bubble(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    // Thumb reach: what is used most sits on the right.
                    ActionButton(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.action_browser)) {
                        context.openExternally(submission.url)
                    }
                    ActionButton(Icons.Outlined.Share, stringResource(R.string.action_share)) {
                        context.shareLink(submission.title, submission.url)
                    }
                    val savedList by actions.saved.collectAsStateWithLifecycle(emptyList())
                    val saved = savedList.any { it.id == submission.id }
                    ActionButton(
                        icon = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        label = stringResource(if (saved) R.string.action_saved else R.string.action_save),
                        highlighted = saved,
                    ) {
                        actions.toggleSaved(
                            SavedSubmission(
                                id = submission.id,
                                title = submission.title,
                                thumbnailTemplate = submission.previewUrl?.replace(Regex("""@\d+-"""), "@{size}-"),
                                rating = submission.rating,
                            ),
                        )
                    }
                    submission.downloadUrl?.let { url ->
                        ActionButton(Icons.Outlined.Download, stringResource(R.string.action_download)) {
                            context.downloadFile(url, actions.userAgent)
                        }
                    }
                    submission.fav?.let { fav ->
                        ActionButton(
                            icon = if (fav.faved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            label = stringResource(if (fav.faved) R.string.action_faved else R.string.action_fav),
                            highlighted = fav.faved,
                            busy = faving,
                        ) {
                            if (!faving) {
                                faving = true
                                actions.scope.launch {
                                    try {
                                        actions.repo.toggleFav(submission)
                                    } catch (e: IOException) {
                                        uiScope.launch { snackbar.showSnackbar(e.message ?: networkError) }
                                    } finally {
                                        faving = false
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (submission.descriptionHtml.isNotBlank()) {
            item(key = "description") {
                Section(stringResource(R.string.section_description)) {
                    HtmlText(submission.descriptionHtml, onLink = onLink, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item(key = "details") {
            CollapsibleSection(
                stringResource(R.string.section_details),
                key = "view-details-${submission.id}",
                summary = listOfNotNull(
                    stringResource(ratingLabel(submission.rating)),
                    submission.info.firstOrNull { it.first.equals("Resolution", ignoreCase = true) }?.second,
                    submission.views?.let { stringResource(R.string.stat_views, NumberFormat.getIntegerInstance().format(it)) },
                ).joinToString(" · "),
            ) {
                Stats(submission)
                for ((label, value) in submission.info) {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(110.dp),
                        )
                        Text(value, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (submission.keywords.isNotEmpty()) {
                    Spacer(Modifier.size(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (keyword in submission.keywords) {
                            Text(
                                keyword,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }

        if (moreFromArtist.isNotEmpty()) {
            item(key = "more") {
                Column {
                    SectionTitle(stringResource(R.string.section_more_from, submission.artist.displayName))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(moreFromArtist, key = { it.id }) { preview ->
                            QuickCard(
                                preview,
                                thumbSize = 300,
                                onOpen = { actions.openSubmissions(moreFromArtist.map { it.id }, preview.id) },
                                modifier = Modifier.width(140.dp),
                            )
                        }
                    }
                }
            }
        }

        item(key = "comments-title") {
            Column {
                CommentsTitle(
                    stringResource(R.string.section_comments) + (submission.commentCount?.let { " ($it)" } ?: ""),
                    onWrite = submission.commentForm?.let { { replyTo = null; composing = true } },
                )
                if (submission.comments.isEmpty()) {
                    Text(
                        stringResource(R.string.no_comments),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        items(submission.comments, key = { "c${it.id}" }) { comment ->
            CommentItem(
                comment,
                onLink,
                onReply = if (submission.commentForm?.canReply == true && !comment.hidden) {
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
            maxLength = submission.commentForm?.maxLength,
            draftKey = "view-${submission.id}-${target?.id ?: 0}",
            send = { text -> actions.repo.comment(submission, text, target?.id) },
            onPosted = {
                composing = false
                uiScope.launch { snackbar.showSnackbar(posted) }
            },
            onDismiss = { composing = false },
        )
    }

    if (zoom && submission.imageUrl != null) {
        ZoomImage(submission.imageUrl, submission.previewUrl, submission.title) { zoom = false }
    }
}

/** The image, or for stories, music and flash their cover plus a button that opens the file. */
@Composable
private fun Media(submission: Submission, onZoom: () -> Unit) {
    val context = LocalContext.current
    val tallest = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
    val shape = MaterialTheme.shapes.large
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        val image = if (submission.kind == SubmissionKind.Image) submission.imageUrl else submission.previewUrl ?: submission.imageUrl
        val ratio = submission.aspectRatio?.takeIf { submission.kind == SubmissionKind.Image }
        if (image != null && ratio != null) {
            // The size is known from FA's details: make room for it right away, so nothing below moves,
            // and let the picture sharpen in place (thumbnail, preview, full image).
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                val width = minOf(maxWidth, tallest * ratio)
                ProgressiveImage(
                    fullUrl = image,
                    previewUrl = submission.previewUrl,
                    contentDescription = submission.title,
                    sized = true,
                    modifier = Modifier
                        .size(width, width / ratio)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { onZoom() },
                )
            }
        } else if (image != null) {
            ProgressiveImage(
                fullUrl = image,
                previewUrl = submission.previewUrl,
                contentDescription = submission.title,
                sized = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = tallest)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable(enabled = submission.kind == SubmissionKind.Image) { onZoom() },
            )
        }
        val file = submission.downloadUrl
        if (submission.kind != SubmissionKind.Image && file != null) {
            Spacer(Modifier.size(12.dp))
            val (icon, label) = when (submission.kind) {
                SubmissionKind.Text -> Icons.AutoMirrored.Outlined.Article to R.string.action_read_story
                SubmissionKind.Audio -> Icons.Outlined.PlayArrow to R.string.action_play
                else -> Icons.Outlined.InsertDriveFile to R.string.action_open_file
            }
            Button(onClick = { context.openExternally(file) }) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(label))
            }
        }
    }
}

@Composable
private fun TitleBlock(submission: Submission, onArtist: () -> Unit) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(submission.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onArtist).padding(vertical = 4.dp),
        ) {
            Avatar(submission.artist.avatarUrl, submission.artist.displayName, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(submission.artist.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull("@" + submission.artist.name, submission.postedAt?.let(::relativeTime)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Stats(submission: Submission) {
    val format = remember { NumberFormat.getIntegerInstance() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RatingLabel(submission.rating)
        submission.views?.let { Text(stringResource(R.string.stat_views, format.format(it)), style = MaterialTheme.typography.bodySmall) }
        submission.favoriteCount?.let { Text(stringResource(R.string.stat_favorites, format.format(it)), style = MaterialTheme.typography.bodySmall) }
    }
}

/** The rating's name, as FA writes it. */
internal fun ratingLabel(rating: Rating): Int = when (rating) {
    Rating.General -> R.string.rating_general
    Rating.Mature -> R.string.rating_mature
    Rating.Adult -> R.string.rating_adult
}

@Composable
internal fun RatingLabel(rating: Rating) {
    val label = ratingLabel(rating)
    Text(
        stringResource(label),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = ratingColor(rating),
        modifier = Modifier
            .border(1.5.dp, ratingColor(rating), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
internal fun CommentItem(comment: Comment, onLink: (String) -> Unit, onReply: (() -> Unit)? = null) {
    val indent = (comment.depth.coerceAtMost(6) * 14).dp
    Bubble(Modifier.fillMaxWidth().padding(start = indent)) {
        if (comment.hidden) {
            Text(
                stringResource(R.string.comment_hidden),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Bubble
        }
        val author = comment.author
        // The author (opens their page) and, on the same line, Reply: so a comment is no taller for it.
        Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).then(
                if (author == null) Modifier else Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onLink("https://www.furaffinity.net/user/${author.name}/") },
            ),
        ) {
            Avatar(comment.author?.avatarUrl, comment.author?.displayName ?: "?", 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        comment.author?.displayName.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (comment.byOriginalPoster) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.op_marker),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraSmall)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                comment.postedAt?.let {
                    Text(relativeTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (onReply != null) {
            TextButton(onClick = onReply, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_reply), style = MaterialTheme.typography.labelLarge)
            }
        }
        }
        Spacer(Modifier.size(8.dp))
        HtmlText(comment.html, onLink = onLink, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A comments heading, with a button to write one when FA offers the form. */
@Composable
internal fun CommentsTitle(title: String, onWrite: (() -> Unit)?, writeLabel: Int = R.string.action_write_comment) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { SectionTitle(title) }
        if (onWrite != null) {
            TextButton(onClick = onWrite) {
                Icon(Icons.Outlined.ModeComment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(writeLabel))
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    highlighted: Boolean = false,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(icon, contentDescription = null, tint = color)
        }
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        SectionTitle(title)
        Bubble(Modifier.fillMaxWidth()) { content() }
    }
}

/**
 * A section that starts folded to one line, its title and a short [summary]; a tap opens it. Whether
 * it is open is kept per [key] while the screen lives.
 */
@Composable
internal fun CollapsibleSection(title: String, key: String, summary: String? = null, content: @Composable () -> Unit) {
    var open by rememberSaveable(key) { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Bubble(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(if (open) R.string.section_collapse else R.string.section_expand)) { open = !open },
        ) {
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (!open && !summary.isNullOrBlank()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(turn),
            )
        }
        if (open) {
            Spacer(Modifier.size(10.dp))
            content()
        }
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
    )
}

@Composable
internal fun Notice(message: String, browserUrl: String?, onRetry: (() -> Unit)?) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Bubble(Modifier.fillMaxWidth()) {
            Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Start)
            Spacer(Modifier.size(12.dp))
            if (onRetry != null) Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            if (browserUrl != null) Button(onClick = { context.openExternally(browserUrl) }) { Text(stringResource(R.string.open_in_browser)) }
        }
    }
}

internal fun relativeTime(unixSeconds: Long): String =
    DateUtils.getRelativeTimeSpanString(unixSeconds * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
