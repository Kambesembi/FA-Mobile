package com.kambesembi.famobile.ui.upload

import android.Manifest
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.UploadRepository
import com.kambesembi.famobile.data.model.FilterOption
import com.kambesembi.famobile.data.model.Keywords
import com.kambesembi.famobile.data.model.Rating
import com.kambesembi.famobile.data.model.UploadChoice
import com.kambesembi.famobile.data.model.UploadDetails
import com.kambesembi.famobile.data.model.UploadItem
import com.kambesembi.famobile.data.model.UploadKind
import com.kambesembi.famobile.data.model.UploadNames
import com.kambesembi.famobile.data.notify.Notifier
import com.kambesembi.famobile.data.parse.UploadParser
import com.kambesembi.famobile.ui.components.Bubble
import com.kambesembi.famobile.ui.components.TopBarHeight
import com.kambesembi.famobile.ui.components.readableSide
import com.kambesembi.famobile.ui.viewer.CollapsibleSection
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the upload form needs from the app. */
class UploadActions(
    val repo: UploadRepository,
    /** The app's scope: copying files in and sending go on when the screen is left. */
    val scope: CoroutineScope,
    val username: String?,
    /** Demo mode never asks for permissions (CI's emulator would show the dialog over everything). */
    val demo: Boolean,
    val back: () -> Unit,
    val openUploads: () -> Unit,
    /** The files are queued: leave the form and say so. */
    val submitted: (count: Int, title: String) -> Unit,
)

private const val MAX_PICK = 20

/** The document types FA takes for stories, poetry and music, for the file picker. */
private val documentTypes = arrayOf(
    "image/*", "audio/*", "text/plain", "application/pdf", "application/rtf", "text/rtf", "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.oasis.opendocument.text",
)

/**
 * FA's upload as one screen: the files, a rating, then whatever else is wanted. Everything but the
 * titles is shared by all files; the form keeps itself while you're away and starts with the last
 * upload's choices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(actions: UploadActions) {
    val repo = actions.repo
    val store by repo.store.collectAsStateWithLifecycle()
    val prep by repo.prep.collectAsStateWithLifecycle()
    val draft = store.draft
    val details = draft.details
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var adding by remember { mutableStateOf(false) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var coverFor by rememberSaveable { mutableStateOf<String?>(null) }
    val activeUploads = store.jobs.count { it.active }

    LaunchedEffect(Unit) {
        repo.startDraft()
        if (!prep.loaded) repo.prepare(actions.username)
    }

    fun setDetails(change: (UploadDetails) -> UploadDetails) = repo.editDraft { it.copy(details = change(it.details)) }
    fun setItem(path: String, change: (UploadItem) -> UploadItem) =
        repo.editDraft { d -> d.copy(items = d.items.map { if (it.file.path == path) change(it) else it }) }

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        adding = true
        actions.scope.launch {
            val added = repo.addFiles(uris)
            adding = false
            val skipped = uris.size - added
            if (skipped > 0) {
                uiScope.launch { snackbar.showSnackbar(context.resources.getQuantityString(R.plurals.upload_skipped, skipped, skipped)) }
            }
        }
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { add(it) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { add(it) }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val path = coverFor
        coverFor = null
        if (path != null && uri != null) actions.scope.launch { repo.setThumbnail(path, uri) }
    }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val images = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val files = { pickFiles.launch(documentTypes) }

    val missingRating = draft.missingRating
    // Only the files this form sends; the others are checked in their own form.
    val sending = draft.sending
    val missingTitle = sending.any { it.title.isBlank() }
    val missingDescription = details.description.isBlank()
    // Per file: its own genders count among its keywords.
    val fewKeywords = details.keywords.size < Keywords.MIN_COUNT || sending.any { details.forItem(it).keywords.size < Keywords.MIN_COUNT }
    val shared = draft.items.size > 1

    fun upload() {
        if (missingRating || missingTitle || missingDescription || fewKeywords) {
            showErrors = true
            // Up to the first thing that still needs filling in (the list: files, "add more", the
            // note for several files, rating, a problem banner, description, keywords).
            val rating = draft.items.size + 1 + (if (shared) 1 else 0)
            val description = rating + 1 + (if (prep.error != null) 1 else 0)
            val target = when {
                missingTitle -> 0
                missingRating -> rating
                missingDescription -> description
                else -> description + 1
            }
            uiScope.launch { listState.animateScrollToItem(target) }
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !actions.demo && !Notifier.permissionGranted(context)) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        val title = sending.first().title
        val count = repo.submit()
        val next = repo.store.value.draft.items.firstOrNull()
        when {
            count == 0 -> Unit
            // A file with its own settings is next: its form, right here.
            next != null -> {
                showErrors = false
                uiScope.launch {
                    listState.scrollToItem(0)
                    val name = next.title.ifBlank { next.file.name }
                    snackbar.showSnackbar(context.resources.getQuantityString(R.plurals.upload_next_form, count, count, name))
                }
            }
            else -> actions.submitted(count, title)
        }
    }

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
                title = { Text(stringResource(R.string.upload_title), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = actions.openUploads) {
                        BadgedBox(badge = { if (activeUploads > 0) Badge { Text("$activeUploads") } }) {
                            Icon(Icons.AutoMirrored.Outlined.ViewList, contentDescription = stringResource(R.string.uploads_title))
                        }
                    }
                    if (!draft.isEmpty) {
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.upload_discard)) },
                                onClick = {
                                    menu = false
                                    confirmDiscard = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (draft.items.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                    Box(Modifier.navigationBarsPadding().padding(horizontal = 6.dp, vertical = 8.dp)) {
                        Button(
                            onClick = ::upload,
                            enabled = !adding,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                when {
                                    draft.waiting.isNotEmpty() -> stringResource(R.string.upload_send_then, sending.size, draft.waiting.size)
                                    sending.size == 1 -> stringResource(R.string.upload_send)
                                    else -> stringResource(R.string.upload_send_many, sending.size)
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
            contentPadding = PaddingValues(horizontal = readableSide(6.dp), vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (draft.items.isEmpty()) {
                item(key = "pick") { PickFiles(adding, images, files) }
            } else {
                itemsIndexed(draft.items, key = { _, item -> item.file.path }) { _, item ->
                    ItemCard(
                        item = item,
                        single = draft.items.size == 1,
                        maxBytes = prep.maxBytes,
                        showErrors = showErrors,
                        onTitle = { title -> setItem(item.file.path) { it.copy(title = title) } },
                        onKind = { kind -> setItem(item.file.path) { it.copy(kind = kind) } },
                        onCover = {
                            coverFor = item.file.path
                            pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onRemoveCover = { actions.scope.launch { repo.setThumbnail(item.file.path, null) } },
                        onRemove = { repo.removeItem(item.file.path) },
                        shared = details.takeIf { draft.items.size > 1 },
                        onOwn = { change -> setItem(item.file.path, change) },
                        later = item in draft.waiting,
                    )
                }
                item(key = "add") { AddMore(adding, images, files) }
                if (shared) item(key = "shared") { SharedNote(draft.items.size) }
            }
            item(key = "rating") {
                RatingPicker(details.rating, showErrors && details.rating == null && draft.items.any { it.rating == null }, shared) { rating ->
                    setDetails { it.copy(rating = rating) }
                }
            }
            prep.error?.let { error ->
                item(key = "error") {
                    Problem(error, loading = prep.loading) { actions.scope.launch { repo.prepare(actions.username) } }
                }
            }
            item(key = "description") {
                DescriptionField(details.description, showErrors && missingDescription) { text -> setDetails { it.copy(description = text) } }
            }
            item(key = "keywords") {
                KeywordsField(details.keywords, store.recentKeywords, showErrors && fewKeywords) { words -> setDetails { it.copy(keywords = words) } }
            }
            item(key = "category") {
                val none = stringResource(R.string.upload_fa_default)
                CollapsibleSection(
                    stringResource(R.string.upload_section_category),
                    key = "upload-category",
                    summary = listOfNotNull(details.category, details.theme, details.species)
                        .joinToString(" · ") { it.label }.ifEmpty { none },
                ) {
                    val lists = store.lists
                    if (lists.isEmpty) {
                        if (prep.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.upload_lists_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChoiceField(stringResource(R.string.upload_category), lists.categories, details.category) { c -> setDetails { it.copy(category = c) } }
                            ChoiceField(stringResource(R.string.upload_theme), lists.themes, details.theme) { c -> setDetails { it.copy(theme = c) } }
                            ChoiceField(stringResource(R.string.upload_species), lists.species, details.species) { c -> setDetails { it.copy(species = c) } }
                        }
                    }
                }
            }
            if (store.folders.isNotEmpty() || details.folders.isNotEmpty()) {
                item(key = "folders") {
                    FoldersSection(store.folders, details.folders) { folders -> setDetails { it.copy(folders = folders) } }
                }
            }
            item(key = "options") {
                val summary = listOfNotNull(
                    stringResource(R.string.upload_scraps).takeIf { details.scraps },
                    stringResource(R.string.upload_lock_comments).takeIf { details.lockComments },
                    stringResource(R.string.upload_strip_location_short).takeIf { details.stripLocation },
                ).joinToString(" · ")
                CollapsibleSection(stringResource(R.string.upload_section_options), key = "upload-options", summary = summary) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SwitchRow(stringResource(R.string.upload_scraps), stringResource(R.string.upload_scraps_hint), details.scraps) { on -> setDetails { it.copy(scraps = on) } }
                        SwitchRow(stringResource(R.string.upload_lock_comments), null, details.lockComments) { on -> setDetails { it.copy(lockComments = on) } }
                        SwitchRow(stringResource(R.string.upload_strip_location), stringResource(R.string.upload_strip_location_hint), details.stripLocation) { on ->
                            setDetails { it.copy(stripLocation = on) }
                        }
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.upload_discard)) },
            text = { Text(stringResource(R.string.upload_discard_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    showErrors = false
                    repo.discardDraft()
                }) { Text(stringResource(R.string.upload_discard_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PickFiles(adding: Boolean, images: () -> Unit, files: () -> Unit) {
    Bubble(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(stringResource(R.string.upload_pick_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.upload_pick_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (adding) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = images, enabled = !adding) {
                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.upload_pick_images))
                }
                OutlinedButton(onClick = files, enabled = !adding) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.upload_pick_files))
                }
            }
        }
    }
}

@Composable
private fun AddMore(adding: Boolean, images: () -> Unit, files: () -> Unit) {
    Column {
        if (adding) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = images, enabled = !adding) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.upload_add_images))
            }
            TextButton(onClick = files, enabled = !adding) { Text(stringResource(R.string.upload_add_files)) }
        }
    }
}

/**
 * Whether a file goes with this form's description, keywords and the rest, or gets a form of its own
 * afterwards. With every switch off, the first file still goes with this form.
 */
@Composable
private fun SeparateSwitch(item: UploadItem, later: Boolean, onOwn: ((UploadItem) -> UploadItem) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.upload_share_details), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(
                    when {
                        later -> R.string.upload_share_details_later
                        item.separate -> R.string.upload_share_details_first
                        else -> R.string.upload_share_details_on
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (later) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = !item.separate, onCheckedChange = { on -> onOwn { it.copy(separate = !on) } })
    }
}

/**
 * One of several files: its rating and genders, starting as the form's. Changing them here makes them
 * this file's own; "Same as all" goes back to the form's.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnSettings(item: UploadItem, shared: UploadDetails, showErrors: Boolean, onOwn: ((UploadItem) -> UploadItem) -> Unit) {
    val rating = item.rating ?: shared.rating
    val genders = item.genders ?: shared.genders
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (item.ownSettings) R.string.upload_own_settings else R.string.upload_same_settings),
                style = MaterialTheme.typography.labelMedium,
                color = if (item.ownSettings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (item.ownSettings) {
                TextButton(onClick = { onOwn { it.copy(rating = null, genders = null) } }) { Text(stringResource(R.string.upload_same_as_all)) }
            }
        }
        val labels = listOf(
            Rating.General to R.string.rating_general,
            Rating.Mature to R.string.rating_mature,
            Rating.Adult to R.string.rating_adult,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(40.dp)) {
            labels.forEachIndexed { i, (r, label) ->
                SegmentedButton(
                    selected = rating == r,
                    onClick = { onOwn { it.copy(rating = r) } },
                    shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                    icon = {},
                ) { Text(stringResource(label), style = MaterialTheme.typography.labelMedium) }
            }
        }
        if (showErrors && rating == null) {
            Text(
                stringResource(R.string.upload_rating_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            Keywords.GENDERS.forEach { gender ->
                val on = genders.any { it.equals(gender, ignoreCase = true) }
                FilterChip(
                    selected = on,
                    onClick = { onOwn { it.copy(genders = if (on) genders.filterNot { g -> g.equals(gender, ignoreCase = true) } else genders + gender) } },
                    label = { Text(genderLabel(gender), style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
    }
}

private fun genderLabel(gender: String) = gender.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** One file: a preview, its title, and for stories and songs the kind and a cover. */
@Composable
private fun ItemCard(
    item: UploadItem,
    single: Boolean,
    maxBytes: Long,
    showErrors: Boolean,
    onTitle: (String) -> Unit,
    onKind: (UploadKind) -> Unit,
    onCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onRemove: () -> Unit,
    /** The form's details when there are several files: then each can have its own rating and genders. */
    shared: UploadDetails? = null,
    onOwn: ((UploadItem) -> UploadItem) -> Unit = {},
    /** Waits for a form of its own after this one. */
    later: Boolean = false,
) {
    val file = item.file
    Bubble(Modifier.fillMaxWidth()) {
        val ratio = if (file.width != null && file.height != null && file.height > 0) file.width.toFloat() / file.height else null
        if (single && file.isImage && ratio != null) {
            // One picture: shown large, as it will look.
            Box(Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = File(file.path),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .aspectRatio(ratio.coerceIn(0.4f, 3f), matchHeightConstraintsFirst = true)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .align(Alignment.Center),
                )
                RemoveButton(onRemove, Modifier.align(Alignment.TopEnd))
            }
            Spacer(Modifier.height(12.dp))
            TitleField(item.title, showErrors, info(file, maxBytes), onTitle)
        } else {
            Row(verticalAlignment = Alignment.Top) {
                Preview(item, Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { TitleField(item.title, showErrors, info(file, maxBytes), onTitle) }
                RemoveButton(onRemove)
            }
            if (shared != null) {
                SeparateSwitch(item, later, onOwn)
                if (!later) OwnSettings(item, shared, showErrors, onOwn)
            }
        }
        if (item.kind.isWriting) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                FilterChip(item.kind == UploadKind.Story, onClick = { onKind(UploadKind.Story) }, label = { Text(stringResource(R.string.upload_kind_story)) })
                FilterChip(item.kind == UploadKind.Poetry, onClick = { onKind(UploadKind.Poetry) }, label = { Text(stringResource(R.string.upload_kind_poetry)) })
            }
        }
        if (item.kind != UploadKind.Artwork) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                val cover = item.thumbnail
                if (cover != null) {
                    AsyncImage(
                        model = File(cover.path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onCover) { Text(stringResource(R.string.upload_cover_change)) }
                    TextButton(onClick = onRemoveCover) { Text(stringResource(R.string.upload_cover_remove)) }
                } else {
                    TextButton(onClick = onCover) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.upload_cover_add))
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleField(title: String, showErrors: Boolean, info: String, onTitle: (String) -> Unit) {
    val missing = showErrors && title.isBlank()
    OutlinedTextField(
        value = title,
        onValueChange = { onTitle(it.take(UploadNames.MAX_TITLE)) },
        label = { Text(stringResource(R.string.upload_field_title)) },
        singleLine = true,
        isError = missing,
        supportingText = { Text(if (missing) stringResource(R.string.upload_title_missing) else info, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RemoveButton(onRemove: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onRemove, modifier = modifier) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.upload_remove_file), modifier = Modifier.padding(4.dp).size(18.dp))
        }
    }
}

/** A picture's thumbnail, or the kind's icon (a story's or song's cover when it has one). */
@Composable
internal fun Preview(item: UploadItem, modifier: Modifier) {
    val picture = item.file.takeIf { it.isImage } ?: item.thumbnail
    if (picture != null && File(picture.path).exists()) {
        AsyncImage(
            model = File(picture.path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLow),
        )
    } else {
        Box(modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(kindIcon(item.kind), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

internal fun kindIcon(kind: UploadKind): ImageVector = when (kind) {
    UploadKind.Artwork -> Icons.Outlined.AddPhotoAlternate
    UploadKind.Story, UploadKind.Poetry -> Icons.Outlined.Description
    UploadKind.Music -> Icons.Outlined.AudioFile
}

/** "1600 × 1200 · 2.4 MB", and what the app will do to make it fit. */
@Composable
private fun info(file: com.kambesembi.famobile.data.model.UploadFile, maxBytes: Long): String {
    val context = LocalContext.current
    val ext = file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val parts = mutableListOf<String>()
    if (file.width != null && file.height != null) parts += "${file.width} × ${file.height}"
    parts += Formatter.formatShortFileSize(context, file.size)
    if (file.isImage) {
        when {
            ext !in UploadKind.Artwork.extensions -> parts += stringResource(R.string.upload_will_convert)
            file.size > maxBytes && ext != "gif" -> parts += stringResource(R.string.upload_will_shrink, maxBytes / (1024 * 1024))
            file.size > maxBytes -> parts += stringResource(R.string.upload_gif_over, maxBytes / (1024 * 1024))
        }
    } else if (file.size > maxBytes) {
        parts += stringResource(R.string.upload_file_over, maxBytes / (1024 * 1024))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatingPicker(rating: Rating?, missing: Boolean, forAll: Boolean, onRating: (Rating) -> Unit) {
    Bubble(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(if (forAll) R.string.upload_rating_all else R.string.upload_rating))
        val labels = listOf(
            Rating.General to R.string.rating_general,
            Rating.Mature to R.string.rating_mature,
            Rating.Adult to R.string.rating_adult,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, (r, label) ->
                SegmentedButton(
                    selected = rating == r,
                    onClick = { onRating(r) },
                    shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                ) { Text(stringResource(label)) }
            }
        }
        if (missing) {
            Text(
                stringResource(R.string.upload_rating_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun Problem(message: String, loading: Boolean, onRetry: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.upload_prep_failed), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = onRetry, enabled = !loading) { Text(stringResource(R.string.retry)) }
        }
    }
}

/** Several files: each is its own submission on FA, and what follows goes with all of them. */
@Composable
private fun SharedNote(count: Int) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                pluralStringResource(R.plurals.upload_shared_note, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** The description, with buttons that wrap the selection in FA's BBCode. */
@Composable
private fun DescriptionField(text: String, missing: Boolean, onText: (String) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    // Changed from outside (an upload put back into the form): follow it.
    LaunchedEffect(text) {
        if (text != value.text) value = TextFieldValue(text, TextRange(text.length))
    }
    fun wrap(open: String, close: String) {
        val start = value.selection.min
        val end = value.selection.max
        val wrapped = value.text.substring(0, start) + open + value.text.substring(start, end) + close + value.text.substring(end)
        value = TextFieldValue(wrapped, if (start == end) TextRange(start + open.length) else TextRange(start, end + open.length + close.length))
        onText(wrapped)
    }
    Bubble(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionLabel(stringResource(R.string.upload_description)) }
            Tool(Icons.Filled.FormatBold, R.string.bbcode_bold) { wrap("[b]", "[/b]") }
            Tool(Icons.Filled.FormatItalic, R.string.bbcode_italic) { wrap("[i]", "[/i]") }
            Tool(Icons.Filled.FormatUnderlined, R.string.bbcode_underline) { wrap("[u]", "[/u]") }
            Tool(Icons.Filled.FormatQuote, R.string.bbcode_quote) { wrap("[quote]", "[/quote]") }
            Tool(Icons.Filled.Link, R.string.bbcode_link) { wrap("[url=]", "[/url]") }
        }
        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it
                if (it.text != text) onText(it.text)
            },
            placeholder = { Text(stringResource(R.string.upload_description_hint)) },
            isError = missing,
            supportingText = if (missing) ({ Text(stringResource(R.string.upload_description_missing)) }) else null,
            minLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Tool(icon: ImageVector, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = stringResource(label), modifier = Modifier.size(20.dp))
    }
}

/** Keywords as chips: a space or comma finishes one; keywords used before are a tap away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordsField(keywords: List<String>, recent: List<String>, missing: Boolean, onChange: (List<String>) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    fun commit(raw: String) {
        val words = Keywords.split(raw)
        if (words.isNotEmpty()) onChange(Keywords.merge(keywords, words))
    }
    Bubble(Modifier.fillMaxWidth()) {
        SectionLabel(
            if (keywords.isEmpty()) stringResource(R.string.upload_keywords) else pluralStringResource(R.plurals.upload_keywords_count, keywords.size, keywords.size),
        )
        if (keywords.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                keywords.forEach { word ->
                    InputChip(
                        selected = false,
                        onClick = { onChange(keywords - word) },
                        label = { Text(word) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.upload_keyword_remove, word), modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { typed ->
                val cut = typed.indexOfLast { it.isWhitespace() || it == ',' }
                if (cut >= 0) {
                    commit(typed.substring(0, cut))
                    text = typed.substring(cut + 1)
                } else {
                    text = typed
                }
            },
            placeholder = { Text(stringResource(R.string.upload_keywords_hint)) },
            isError = missing,
            supportingText = {
                val used = Keywords.join(keywords).length
                Row {
                    Text(
                        if (missing) pluralStringResource(R.plurals.upload_keywords_missing, Keywords.MIN_COUNT, Keywords.MIN_COUNT)
                        else stringResource(R.string.upload_keywords_rules),
                        modifier = Modifier.weight(1f),
                    )
                    if (used > Keywords.MAX_CHARS * 3 / 4) Text("$used / ${Keywords.MAX_CHARS}")
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                commit(text)
                text = ""
            }),
            modifier = Modifier.fillMaxWidth(),
        )
        // FA's gender boxes only add these keywords.
        Text(
            stringResource(R.string.upload_genders),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Keywords.GENDERS.forEach { gender ->
                val on = keywords.any { it.equals(gender, ignoreCase = true) }
                FilterChip(
                    selected = on,
                    onClick = {
                        onChange(if (on) keywords.filterNot { it.equals(gender, ignoreCase = true) } else Keywords.merge(keywords, listOf(gender)))
                    },
                    label = { Text(genderLabel(gender)) },
                )
            }
        }
        val typed = text.trim()
        val suggestions = recent
            .filter { r -> r !in Keywords.GENDERS && keywords.none { it.equals(r, ignoreCase = true) } && (typed.isEmpty() || r.startsWith(typed, ignoreCase = true)) }
            .take(14)
        if (suggestions.isNotEmpty()) {
            Text(
                stringResource(R.string.upload_keywords_recent),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { word ->
                    SuggestionChip(
                        onClick = {
                            onChange(Keywords.merge(keywords, listOf(word)))
                            text = ""
                        },
                        label = { Text(word) },
                    )
                }
            }
        }
    }
}

/** One of FA's lists; the first entry leaves it to FA. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceField(label: String, options: List<FilterOption>, choice: UploadChoice?, onChoice: (UploadChoice?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = choice?.label ?: stringResource(R.string.upload_fa_default),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val options = options.filterNot(UploadParser::isNoChoice)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.upload_fa_default)) },
                onClick = {
                    onChoice(null)
                    open = false
                },
            )
            var group: String? = null
            options.forEach { option ->
                val g = option.group
                if (g != group) {
                    group = g
                    if (g != null) {
                        Text(
                            g,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onChoice(UploadChoice(option.value, option.label))
                        open = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoldersSection(folders: List<FilterOption>, chosen: List<UploadChoice>, onChange: (List<UploadChoice>) -> Unit) {
    val none = stringResource(R.string.upload_folders_none)
    CollapsibleSection(
        stringResource(R.string.upload_section_folders),
        key = "upload-folders",
        summary = chosen.joinToString(" · ") { it.label }.ifEmpty { none },
    ) {
        // Chosen before but gone from the list (renamed, deleted): still shown, so it can be unticked.
        val all = folders.map { UploadChoice(it.value, it.label) } + chosen.filter { c -> folders.none { it.value == c.value } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            all.forEach { folder ->
                val on = chosen.any { it.value == folder.value }
                FilterChip(
                    selected = on,
                    onClick = { onChange(if (on) chosen.filter { it.value != folder.value } else chosen + folder) },
                    label = { Text(folder.label) },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
