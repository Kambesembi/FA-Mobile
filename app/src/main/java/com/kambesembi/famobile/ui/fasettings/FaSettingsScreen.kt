package com.kambesembi.famobile.ui.fasettings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.FaSettingEntry
import com.kambesembi.famobile.data.FaSettingsRepository
import com.kambesembi.famobile.data.FaUrls
import com.kambesembi.famobile.data.model.FaSetting
import com.kambesembi.famobile.data.model.FaSettingEditor
import com.kambesembi.famobile.data.model.FaSettingValue
import com.kambesembi.famobile.data.model.FaSettingsPage
import com.kambesembi.famobile.ui.components.Bubble
import com.kambesembi.famobile.ui.components.FurryLoader
import com.kambesembi.famobile.ui.components.ReadableWidth
import com.kambesembi.famobile.ui.components.TopBarHeight
import com.kambesembi.famobile.ui.util.openExternally
import java.io.IOException
import kotlinx.coroutines.launch

@StringRes
private fun FaSettingsPage.title(): Int = when (this) {
    FaSettingsPage.Account -> R.string.fa_settings_page_account
    FaSettingsPage.Site -> R.string.fa_settings_page_site
    FaSettingsPage.User -> R.string.fa_settings_page_user
    FaSettingsPage.Profile -> R.string.fa_settings_page_profile
    FaSettingsPage.Contacts -> R.string.fa_settings_page_contacts
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Bar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        expandedHeight = TopBarHeight,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Choosing what the FA settings page shows. [onConfirm] gets the chosen ids. */
@Composable
fun FaSettingsPicker(chosen: Set<String>, onConfirm: (Set<String>) -> Unit, onBack: () -> Unit) {
    var picked by rememberSaveable(chosen) { mutableStateOf(chosen.toList()) }
    val set = picked.toSet()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { Bar(stringResource(R.string.fa_settings_choose), onBack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { onConfirm(set) },
                    enabled = set.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(stringResource(R.string.fa_settings_confirm, set.size)) }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text(
                    stringResource(R.string.fa_settings_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = ReadableWidth).fillMaxWidth(),
                )
            }
            for ((page, settings) in FaSetting.all.groupBy { it.page }) {
                item(key = page.name) {
                    Column(Modifier.widthIn(max = ReadableWidth).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(page.title()).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                            )
                            val all = settings.all { it.id in set }
                            TextButton(onClick = {
                                picked = if (all) picked - settings.map { it.id }.toSet() else (picked + settings.map { it.id }).distinct()
                            }) { Text(stringResource(if (all) R.string.fa_settings_none else R.string.fa_settings_all)) }
                        }
                        Bubble(Modifier.fillMaxWidth()) {
                            settings.forEachIndexed { i, setting ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                val on = setting.id in set
                                Row(
                                    Modifier.fillMaxWidth().clickable { picked = if (on) picked - setting.id else picked + setting.id },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(vertical = 10.dp))
                                    Spacer(Modifier.size(12.dp))
                                    Text(setting.label, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The user's own page of FA settings: only what they chose, read fresh from FA, changed with a tap. */
@Composable
fun FaSettingsView(repository: FaSettingsRepository, chosen: Set<String>, onChoose: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var reload by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<List<Pair<FaSettingsPage, List<FaSettingEntry>>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<FaSettingEntry?>(null) }
    var saving by remember { mutableStateOf<String?>(null) }
    val saved = stringResource(R.string.fa_settings_saved)
    LaunchedEffect(chosen, reload) {
        result = null
        error = null
        try {
            result = repository.load(chosen)
        } catch (e: IOException) {
            error = e.message ?: e.javaClass.simpleName
        }
    }
    fun save(entry: FaSettingEntry, edit: FaSettingEditor) {
        editing = null
        saving = entry.setting.id
        scope.launch {
            try {
                val after = repository.save(entry.setting, edit)
                result = result?.map { (page, entries) -> page to entries.map { if (it.setting.id == after.setting.id) after else it } }
                snackbar.showSnackbar(saved)
            } catch (e: IOException) {
                snackbar.showSnackbar(e.message ?: e.javaClass.simpleName)
            } finally {
                saving = null
            }
        }
    }
    editing?.let { entry ->
        entry.editor?.let { editor -> EditDialog(entry.setting.label, editor, onDismiss = { editing = null }, onSave = { save(entry, it) }) }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Bar(stringResource(R.string.fa_settings), onBack) {
                IconButton(onClick = { reload++ }) { Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.fa_settings_reload)) }
                IconButton(onClick = onChoose) { Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.fa_settings_choose)) }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val pages = result
            when {
                error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
                }
                pages == null -> FurryLoader()
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(pages, key = { it.first.name }) { (page, entries) ->
                        Column(Modifier.widthIn(max = ReadableWidth).fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(page.title()).uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                )
                                TextButton(onClick = { context.openExternally(FaUrls.absolute(page.path)) }) {
                                    Text(stringResource(R.string.fa_settings_change_on_fa))
                                }
                            }
                            Bubble(Modifier.fillMaxWidth()) {
                                entries.forEachIndexed { i, entry ->
                                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Entry(
                                        entry,
                                        busy = saving == entry.setting.id,
                                        enabled = saving == null,
                                        onEdit = { editing = entry },
                                        onToggle = { on -> save(entry, FaSettingEditor.Toggle(on)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Entry(entry: FaSettingEntry, busy: Boolean, enabled: Boolean, onEdit: () -> Unit, onToggle: (Boolean) -> Unit) {
    val editor = entry.editor
    val toggle = editor as? FaSettingEditor.Toggle
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && editor != null) { if (toggle != null) onToggle(!toggle.on) else onEdit() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.setting.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(2.dp))
            if (toggle == null) Value(entry.value)
        }
        Spacer(Modifier.size(8.dp))
        when {
            busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            toggle != null -> Switch(checked = toggle.on, onCheckedChange = null, enabled = enabled)
            editor != null -> Icon(Icons.Outlined.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Value(value: FaSettingValue) {
    val body = MaterialTheme.typography.bodyLarge
    when (value) {
        is FaSettingValue.Text -> Text(value.text, style = if (value.long) MaterialTheme.typography.bodyMedium else body)
        is FaSettingValue.Flag -> Text(stringResource(if (value.on) R.string.fa_settings_yes else R.string.fa_settings_no), style = body)
        is FaSettingValue.Items -> Text(value.items.joinToString(if (value.items.any { it.contains(':') }) "\n" else " · "), style = body)
        FaSettingValue.Empty -> Text(
            stringResource(R.string.fa_settings_not_set),
            style = body,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

/** Changing one entry: a list to pick from, boxes to tick, or text. */
@Composable
private fun EditDialog(label: String, editor: FaSettingEditor, onDismiss: () -> Unit, onSave: (FaSettingEditor) -> Unit) {
    var choice by remember { mutableStateOf((editor as? FaSettingEditor.Choice)?.selected) }
    var ticked by remember { mutableStateOf((editor as? FaSettingEditor.Multi)?.ticked.orEmpty()) }
    var text by remember { mutableStateOf((editor as? FaSettingEditor.Free)?.text.orEmpty()) }
    var tags by remember { mutableStateOf((editor as? FaSettingEditor.Tags)?.tags.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            when (editor) {
                is FaSettingEditor.Choice -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(editor.options) { (value, name) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { choice = value },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choice == value, onClick = null)
                            Spacer(Modifier.size(12.dp))
                            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                }
                is FaSettingEditor.Multi -> Column {
                    for ((name, title) in editor.options) {
                        val on = name in ticked
                        Row(
                            Modifier.fillMaxWidth().clickable { ticked = if (on) ticked - name else ticked + name },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(vertical = 10.dp))
                            Spacer(Modifier.size(12.dp))
                            Text(title, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                is FaSettingEditor.Free -> OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = !editor.multiline,
                    minLines = if (editor.multiline) 6 else 1,
                    maxLines = if (editor.multiline) 14 else 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                is FaSettingEditor.Tags -> TagsEditor(tags) { tags = it }
                is FaSettingEditor.Toggle -> Unit
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    when (editor) {
                        is FaSettingEditor.Choice -> editor.copy(selected = choice)
                        is FaSettingEditor.Multi -> editor.copy(ticked = ticked)
                        is FaSettingEditor.Free -> editor.copy(text = text)
                        is FaSettingEditor.Tags -> editor.copy(tags = tags)
                        is FaSettingEditor.Toggle -> editor
                    },
                )
            }) { Text(stringResource(R.string.fa_settings_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.fa_settings_cancel)) } },
    )
}

/** The tags as chips (a tap removes one) and a field to add more; spaces or commas split several. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsEditor(tags: List<String>, onChange: (List<String>) -> Unit) {
    var adding by remember { mutableStateOf("") }
    fun add() {
        val new = adding.split(Regex("""[\s,]+""")).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val known = tags.map { it.lowercase() }.toSet()
        onChange(tags + new.filter { it !in known }.distinct())
        adding = ""
    }
    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = adding,
            onValueChange = { adding = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.fa_settings_tag_add)) },
            trailingIcon = {
                IconButton(onClick = { add() }, enabled = adding.isNotBlank()) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.fa_settings_tag_add))
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (tag in tags) {
                InputChip(
                    selected = false,
                    onClick = { onChange(tags - tag) },
                    label = { Text(tag) },
                    trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
}
