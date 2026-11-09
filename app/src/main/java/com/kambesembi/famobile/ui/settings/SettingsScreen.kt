package com.kambesembi.famobile.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.AppLanguage
import com.kambesembi.famobile.data.AppSettings
import com.kambesembi.famobile.data.CheckInterval
import com.kambesembi.famobile.data.ThemeMode
import com.kambesembi.famobile.data.notify.CheckWorker
import com.kambesembi.famobile.data.notify.Notifier
import com.kambesembi.famobile.data.parse.LoggedInUser
import com.kambesembi.famobile.data.update.UpdateState
import com.kambesembi.famobile.data.update.Updates
import com.kambesembi.famobile.ui.components.Avatar
import com.kambesembi.famobile.ui.components.Bubble
import com.kambesembi.famobile.ui.components.TopBarHeight
import com.kambesembi.famobile.ui.components.readableSide
import com.kambesembi.famobile.ui.shell.LogoutDialog
import com.kambesembi.famobile.ui.util.openExternally
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    user: LoggedInUser,
    settings: AppSettings,
    updates: Updates,
    version: String,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onFaSettings: () -> Unit = {},
    onChooseFaSettings: () -> Unit = {},
) {
    val theme by settings.theme.collectAsStateWithLifecycle(ThemeMode.System)
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }
    val interval by settings.checkInterval.collectAsStateWithLifecycle(CheckInterval.Min30)
    val columns by settings.gridColumns.collectAsStateWithLifecycle(AppSettings.DEFAULT_COLUMNS)
    val context = LocalContext.current
    // Read again on every return from Android's settings.
    var canNotify by remember { mutableStateOf(Notifier.canPost(context)) }
    LifecycleResumeEffect(Unit) {
        canNotify = Notifier.canPost(context)
        onPauseOrDispose { }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                expandedHeight = TopBarHeight,
                title = { Text(stringResource(R.string.menu_settings), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = readableSide(12.dp), vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(R.string.settings_appearance) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.padding(top = 8.dp))
                val modes = ThemeMode.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = theme == mode,
                            onClick = { scope.launch { settings.setTheme(mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(stringResource(mode.label)) }
                    }
                }
                Spacer(Modifier.padding(top = 16.dp))
                Text(stringResource(R.string.settings_columns), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.padding(top = 8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    (1..5).forEach { n ->
                        SegmentedButton(
                            selected = columns == n,
                            onClick = { scope.launch { settings.setGridColumns(n) } },
                            shape = SegmentedButtonDefaults.itemShape(n - 1, 5),
                            icon = {},
                        ) { Text("$n") }
                    }
                }
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    stringResource(R.string.settings_columns_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(top = 16.dp))
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.padding(top = 8.dp))
                val language = remember { AppLanguage.current(context) }
                val languages = AppLanguage.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    languages.forEachIndexed { i, option ->
                        SegmentedButton(
                            selected = language == option,
                            onClick = { (context as? Activity)?.let { AppLanguage.set(it, option) } },
                            shape = SegmentedButtonDefaults.itemShape(i, languages.size),
                            icon = {},
                        ) {
                            Text(option.endonym ?: stringResource(R.string.settings_language_system), maxLines = 1)
                        }
                    }
                }
            }

            Section(R.string.settings_content) {
                val sfw by settings.sfw.collectAsStateWithLifecycle(false)
                Row(
                    Modifier.fillMaxWidth().toggleable(sfw) { on -> scope.launch { settings.setSfw(on) } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_sfw), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_sfw_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = sfw, onCheckedChange = null)
                }
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    stringResource(R.string.settings_blocklist_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section(R.string.settings_notifications) {
                Text(stringResource(R.string.settings_check), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.padding(top = 8.dp))
                val intervals = CheckInterval.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    intervals.forEachIndexed { i, option ->
                        SegmentedButton(
                            selected = interval == option,
                            onClick = {
                                scope.launch {
                                    settings.setCheckInterval(option)
                                    CheckWorker.schedule(context, option, replace = true)
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, intervals.size),
                            icon = {},
                        ) { Text(stringResource(option.label), maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    }
                }
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    stringResource(R.string.settings_check_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (interval != CheckInterval.Off && !canNotify) {
                    Spacer(Modifier.padding(top = 10.dp))
                    Text(
                        stringResource(R.string.settings_notifications_blocked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.padding(top = 6.dp))
                TextButton(onClick = { context.openNotificationSettings() }) {
                    Text(stringResource(R.string.settings_notifications_open))
                }
            }

            Section(R.string.fa_settings) {
                val chosen by settings.faSettings.collectAsStateWithLifecycle(emptySet())
                Text(
                    stringResource(R.string.fa_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (chosen.isNotEmpty()) Button(onClick = onFaSettings) { Text(stringResource(R.string.fa_settings_open)) }
                    OutlinedButton(onClick = onChooseFaSettings) { Text(stringResource(R.string.fa_settings_choose)) }
                }
            }

            Section(R.string.settings_account) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(user.avatarUrl, user.displayName, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_logged_in_as, user.displayName),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "@" + user.username,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.padding(top = 12.dp))
                OutlinedButton(
                    onClick = { confirmLogout = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.menu_logout))
                }
            }

            Section(R.string.settings_updates) {
                val checks by settings.updateChecks.collectAsStateWithLifecycle(true)
                val update by updates.state.collectAsStateWithLifecycle()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_update_checks), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_update_checks_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = checks, onCheckedChange = { on ->
                        scope.launch {
                            settings.setUpdateChecks(on)
                            updates.schedule(on)
                        }
                    })
                }
                Spacer(Modifier.padding(top = 10.dp))
                Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.bodyMedium)
                val status = when (val u = update) {
                    UpdateState.Unknown -> null
                    UpdateState.Checking -> stringResource(R.string.update_checking)
                    UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                    UpdateState.NotPublic -> stringResource(R.string.update_not_public)
                    is UpdateState.Failed -> stringResource(R.string.update_failed, u.message)
                    is UpdateState.Available -> stringResource(R.string.update_available, u.release.version)
                }
                status?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (update is UpdateState.Available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    (update as? UpdateState.Available)?.release?.let { release ->
                        Button(onClick = { context.openExternally(release.apkUrl ?: release.pageUrl) }) {
                            Text(stringResource(R.string.update_download))
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { context.openExternally(release.pageUrl) }) { Text(stringResource(R.string.update_release_page)) }
                    } ?: TextButton(
                        onClick = { scope.launch { updates.check() } },
                        enabled = update != UpdateState.Checking,
                    ) { Text(stringResource(R.string.update_check_now)) }
                }
            }

            Section(R.string.settings_about) {
                Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirmLogout) LogoutDialog(onConfirm = onLogout, onDismiss = { confirmLogout = false })
}

private val CheckInterval.label: Int
    get() = when (this) {
        CheckInterval.Off -> R.string.interval_off
        CheckInterval.Min15 -> R.string.interval_15
        CheckInterval.Min30 -> R.string.interval_30
        CheckInterval.Hour1 -> R.string.interval_60
        CheckInterval.Hour3 -> R.string.interval_180
    }

private fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    runCatching { startActivity(intent) }
}

private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Dark -> R.string.theme_dark
        ThemeMode.Light -> R.string.theme_light
    }

@Composable
private fun Section(@StringRes title: Int, content: @Composable () -> Unit) {
    Column {
        Text(
            stringResource(title).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
        )
        Bubble(Modifier.fillMaxWidth()) { content() }
    }
}
