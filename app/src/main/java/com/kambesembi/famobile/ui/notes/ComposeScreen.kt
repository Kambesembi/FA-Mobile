package com.kambesembi.famobile.ui.notes

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.model.NoteDraft
import com.kambesembi.famobile.ui.components.TopBarHeight
import com.kambesembi.famobile.ui.components.readableSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A new note or a reply. [send] returns null when the note went out, otherwise what went wrong. The
 * toolbar wraps the selection in FA's BBCode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    draft: NoteDraft,
    /** App scope: a note being sent keeps going if the screen is left. */
    scope: CoroutineScope,
    send: suspend (to: String, subject: String, message: String) -> String?,
    onDone: () -> Unit,
) {
    var to by rememberSaveable { mutableStateOf(draft.to) }
    var subject by rememberSaveable { mutableStateOf(draft.subject) }
    var message by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(draft.message, TextRange(0))) }
    var sending by rememberSaveable { mutableStateOf(false) }
    val snackbar = androidx.compose.runtime.remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    val context = LocalContext.current
    val sentText = stringResource(R.string.note_sent)
    val canSend = !sending && to.isNotBlank() && subject.isNotBlank() && message.text.isNotBlank()

    fun wrap(open: String, close: String) {
        val sel = message.selection
        val text = message.text
        val start = minOf(sel.start, sel.end)
        val end = maxOf(sel.start, sel.end)
        val wrapped = text.substring(0, start) + open + text.substring(start, end) + close + text.substring(end)
        message = TextFieldValue(wrapped, if (start == end) TextRange(start + open.length) else TextRange(start, end + open.length + close.length))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                expandedHeight = TopBarHeight,
                title = { Text(stringResource(if (draft.to.isEmpty()) R.string.note_new else R.string.note_reply), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
                },
                actions = {
                    if (sending) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(
                            enabled = canSend,
                            onClick = {
                                sending = true
                                scope.launch {
                                    val error = send(to, subject, message.text)
                                    sending = false
                                    uiScope.launch {
                                        if (error == null) {
                                            Toast.makeText(context, sentText, Toast.LENGTH_SHORT).show()
                                            onDone()
                                        } else {
                                            snackbar.showSnackbar(error)
                                        }
                                    }
                                }
                            },
                        ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.note_send)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = readableSide(12.dp)),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text(stringResource(R.string.note_to_field)) },
                singleLine = true,
                enabled = !sending,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text(stringResource(R.string.note_subject)) },
                singleLine = true,
                enabled = !sending,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Tool(Icons.Filled.FormatBold, R.string.bbcode_bold) { wrap("[b]", "[/b]") }
                Tool(Icons.Filled.FormatItalic, R.string.bbcode_italic) { wrap("[i]", "[/i]") }
                Tool(Icons.Filled.FormatUnderlined, R.string.bbcode_underline) { wrap("[u]", "[/u]") }
                Tool(Icons.Filled.FormatQuote, R.string.bbcode_quote) { wrap("[quote]", "[/quote]") }
                Tool(Icons.Filled.Link, R.string.bbcode_link) { wrap("[url=]", "[/url]") }
            }
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text(stringResource(R.string.note_message)) },
                enabled = !sending,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp),
            )
        }
    }
}

@Composable
private fun Tool(icon: ImageVector, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = stringResource(label)) }
}
