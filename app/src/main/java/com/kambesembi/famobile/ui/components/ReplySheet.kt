package com.kambesembi.famobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.model.Comment
import com.kambesembi.famobile.data.model.PostOutcome
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jsoup.Jsoup

/**
 * Writing a comment, a reply or a shout. [quote] is the comment being answered. The sheet stays open
 * while posting and shows FA's reason if it didn't work; the text survives rotation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplySheet(
    title: String,
    hint: String,
    quote: Comment?,
    maxLength: Int?,
    draftKey: String,
    send: suspend (String) -> PostOutcome<*>,
    onPosted: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(draftKey) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sendingNow by rememberUpdatedState(sending)
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !sendingNow },
    )
    val focus = remember { FocusRequester() }
    val notShown = stringResource(R.string.reply_not_shown)
    val networkError = stringResource(R.string.error_network)

    LaunchedEffect(Unit) {
        delay(250) // once the sheet is in place, so the keyboard opens with it
        runCatching { focus.requestFocus() }
    }

    ModalBottomSheet(onDismissRequest = { if (!sending) onDismiss() }, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            quote?.let { Quote(it) }
            OutlinedTextField(
                value = text,
                onValueChange = { if (maxLength == null || it.length <= maxLength) text = it },
                placeholder = { Text(hint) },
                minLines = 4,
                maxLines = 10,
                enabled = !sending,
                supportingText = if (maxLength != null) {
                    { Text("${text.length} / $maxLength") }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, enabled = !sending) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = text.isNotBlank() && !sending,
                    onClick = {
                        scope.launch {
                            sending = true
                            error = null
                            try {
                                when (val outcome = send(text.trim())) {
                                    is PostOutcome.Posted<*> -> {
                                        text = ""
                                        sending = false
                                        sheet.hide()
                                        onPosted()
                                    }
                                    is PostOutcome.Failed -> error = outcome.message ?: notShown
                                }
                            } catch (e: IOException) {
                                error = e.message ?: networkError
                            } finally {
                                sending = false
                            }
                        }
                    },
                ) {
                    if (sending) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (sending) R.string.reply_posting else R.string.reply_post))
                }
            }
        }
    }
}

/** The comment being answered: who wrote it and how it starts. */
@Composable
private fun Quote(comment: Comment) {
    Bubble(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(comment.author?.avatarUrl, comment.author?.displayName ?: "?", 24.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                comment.author?.displayName.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.size(6.dp))
        // Long comments scroll inside the box, so the whole message can be read while answering.
        HtmlText(
            comment.html,
            onLink = {},
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState()),
        )
    }
}
