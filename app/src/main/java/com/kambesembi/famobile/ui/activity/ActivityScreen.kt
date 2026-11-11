package com.kambesembi.famobile.ui.activity

import androidx.compose.foundation.lazy.rememberLazyListState
import com.kambesembi.famobile.ui.components.ScrollTopOnReload
import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.ActivityState
import com.kambesembi.famobile.data.model.ActivityItem
import com.kambesembi.famobile.data.model.ActivityKind
import com.kambesembi.famobile.data.model.ActivitySection
import com.kambesembi.famobile.ui.components.Avatar
import com.kambesembi.famobile.ui.components.Bubble
import com.kambesembi.famobile.ui.components.FurryLoader
import com.kambesembi.famobile.ui.components.readableSide

/**
 * Watches, comments, favorites, journals and shouts, one bubble per kind. Tap opens what it's about,
 * swipe removes a notification, "Clear" removes a whole kind.
 */
@Composable
fun ActivityScreen(
    state: ActivityState,
    demo: Boolean,
    onOpen: (ActivityItem) -> Unit,
    onOpenUser: (String) -> Unit = {},
    onRemove: (ActivityItem) -> Unit,
    onClear: (ActivitySection) -> Unit,
    onRetry: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf<ActivitySection?>(null) }

    when {
        !state.loaded && state.error != null -> {
            Message(stringResource(R.string.error_title), state.error, onRetry)
            return
        }
        !state.loaded -> {
            FurryLoader()
            return
        }
        state.sections.isEmpty() -> {
            Message(stringResource(R.string.activity_empty), stringResource(R.string.activity_empty_hint), null)
            return
        }
    }

    val listState = rememberLazyListState()
    ScrollTopOnReload(state.refreshing) { listState.scrollToItem(0) }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = readableSide(12.dp), vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
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
        for (section in state.sections) {
            item(key = section.sectionId) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            sectionTitle(section).uppercase() + "  ·  " + section.items.size,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { confirmClear = section }, enabled = !state.removing) {
                            Text(stringResource(R.string.clear))
                        }
                    }
                    Bubble(Modifier.fillMaxWidth()) {
                        section.items.forEachIndexed { i, item ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            // Keyed, so a swiped-away row's state doesn't carry over to the next one.
                            androidx.compose.runtime.key(item.key) {
                                SwipeRow(onRemove = { onRemove(item) }) {
                                    ItemRow(item, onClick = { onOpen(item) }, onUser = onOpenUser)
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "hint") {
            Text(
                stringResource(R.string.activity_swipe_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    confirmClear?.let { section ->
        AlertDialog(
            onDismissRequest = { confirmClear = null },
            title = { Text(stringResource(R.string.activity_clear_title, sectionTitle(section))) },
            text = { Text(stringResource(R.string.activity_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = null
                    onClear(section)
                }) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SwipeRow(onRemove: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onRemove()
            value != SwipeToDismissBoxValue.Settled
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            // Soft red while dragging; full red, a bigger bin and the word once letting go removes it.
            val reached = state.targetValue != SwipeToDismissBoxValue.Settled
            val colors = MaterialTheme.colorScheme
            val background by animateColorAsState(if (reached) colors.error else colors.errorContainer, label = "swipe")
            val foreground = if (reached) colors.onError else colors.onErrorContainer
            val scale by animateFloatAsState(if (reached) 1.2f else 0.9f, label = "swipe-icon")
            val fromStart = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(background)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (fromStart) Arrangement.Start else Arrangement.End,
            ) {
                val label = @Composable {
                    Text(stringResource(R.string.saved_remove), color = foreground, style = MaterialTheme.typography.labelLarge)
                }
                if (!fromStart) {
                    label()
                    Spacer(Modifier.width(10.dp))
                }
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = foreground, modifier = Modifier.scale(scale))
                if (fromStart) {
                    Spacer(Modifier.width(10.dp))
                    label()
                }
            }
        },
    ) { content() }
}

@Composable
private fun ItemRow(item: ActivityItem, onClick: () -> Unit, onUser: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val actor = item.actor
        if (actor != null) {
            Box(Modifier.clip(CircleShape).clickable { onUser(actor.name) }) { Avatar(actor.avatarUrl, actor.displayName, 40.dp) }
        } else {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(kindIcon(item.kind), contentDescription = null) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(sentence(item, onUser = { item.actor?.let { onUser(it.name) } }, onSubject = onClick), style = MaterialTheme.typography.bodyMedium)
            item.postedAt?.let {
                Text(
                    DateUtils.getRelativeTimeSpanString(it * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(kindIcon(item.kind), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

/**
 * "artist commented on Title", with the names in bold: a tap on the name opens the user, on the title
 * what it is about. Unknown kinds show FA's own line.
 */
@Composable
private fun sentence(item: ActivityItem, onUser: () -> Unit, onSubject: () -> Unit) = buildAnnotatedString {
    val actor = item.actor?.displayName
    val title = item.title
    val template = when (item.kind) {
        ActivityKind.Watches -> R.string.activity_watch
        ActivityKind.SubmissionComments -> R.string.activity_submission_comment
        ActivityKind.JournalComments -> R.string.activity_journal_comment
        ActivityKind.Shouts -> R.string.activity_shout
        ActivityKind.Favorites -> R.string.activity_favorite
        ActivityKind.Journals -> R.string.activity_journal
        ActivityKind.Other -> null
    }
    if (template == null || actor == null) {
        append(item.text)
        return@buildAnnotatedString
    }
    // Templates use %1$s for the actor and %2$s for the title.
    val text = stringResource(template, "\u0001", "\u0002")
    for (part in Regex("(\u0001|\u0002)|[^\u0001\u0002]+").findAll(text)) {
        when (part.value) {
            "\u0001" -> withLink(LinkAnnotation.Clickable("user") { onUser() }) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(actor) }
            }
            "\u0002" -> withLink(LinkAnnotation.Clickable("subject") { onSubject() }) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(title ?: "…") }
            }
            else -> append(part.value)
        }
    }
}

@Composable
private fun sectionTitle(section: ActivitySection): String = when (section.kind) {
    ActivityKind.Watches -> stringResource(R.string.activity_section_watches)
    ActivityKind.SubmissionComments -> stringResource(R.string.activity_section_submission_comments)
    ActivityKind.JournalComments -> stringResource(R.string.activity_section_journal_comments)
    ActivityKind.Shouts -> stringResource(R.string.activity_section_shouts)
    ActivityKind.Favorites -> stringResource(R.string.activity_section_favorites)
    ActivityKind.Journals -> stringResource(R.string.activity_section_journals)
    ActivityKind.Other -> section.title
}

private fun kindIcon(kind: ActivityKind): ImageVector = when (kind) {
    ActivityKind.Watches -> Icons.Outlined.PersonAdd
    ActivityKind.SubmissionComments, ActivityKind.JournalComments -> Icons.Outlined.ChatBubbleOutline
    ActivityKind.Shouts -> Icons.Outlined.Campaign
    ActivityKind.Favorites -> Icons.Outlined.FavoriteBorder
    ActivityKind.Journals -> Icons.AutoMirrored.Outlined.Article
    ActivityKind.Other -> Icons.Outlined.NotificationsNone
}

@Composable
private fun Message(title: String, body: String?, onRetry: (() -> Unit)?) {
    // A list, even for one bubble: pull-to-refresh needs something to drag.
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillParentMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                MessageBubble(title, body, onRetry)
            }
        }
    }
}

@Composable
private fun MessageBubble(title: String, body: String?, onRetry: (() -> Unit)?) {
    run {
        Bubble(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (body != null) {
                Spacer(Modifier.size(6.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onRetry != null) {
                Spacer(Modifier.size(12.dp))
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}
