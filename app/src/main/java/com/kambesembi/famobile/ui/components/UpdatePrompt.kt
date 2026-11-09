package com.kambesembi.famobile.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kambesembi.famobile.BuildConfig
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.update.InstallState
import com.kambesembi.famobile.data.update.Installer
import com.kambesembi.famobile.data.update.Updates
import com.kambesembi.famobile.ui.util.openExternally

/**
 * "Update to 0.4.2?" while a new version is offered. On yes the APK downloads, with its progress,
 * and Android's own installer takes over (asking the first time to allow installs from this app).
 */
@Composable
fun UpdatePrompt(updates: Updates, installer: Installer) {
    val release by updates.offer.collectAsStateWithLifecycle()
    val state by installer.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val offered = release ?: return

    // Back from Android's "install unknown apps" screen with the OK: go on.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, offered) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && installer.state.value == InstallState.NeedsPermission && installer.canInstall()) {
                installer.start(offered)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Android's "update this app?" screen.
    LaunchedEffect(state) {
        val confirm = (state as? InstallState.Confirm)?.intent ?: return@LaunchedEffect
        installer.confirmShown()
        try {
            context.startActivity(confirm)
        } catch (e: ActivityNotFoundException) {
            installer.reset()
        }
    }

    val later = {
        installer.cancel()
        updates.decline()
    }
    val busy = state is InstallState.Downloading || state is InstallState.Installing || state is InstallState.Confirm
    AlertDialog(
        onDismissRequest = { if (!busy) later() },
        icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
        title = { Text(stringResource(R.string.update_prompt_title, offered.version)) },
        text = {
            Column {
                when (val s = state) {
                    InstallState.Idle -> Text(stringResource(R.string.update_prompt_body, BuildConfig.VERSION_NAME))
                    is InstallState.Downloading -> {
                        Text(stringResource(R.string.update_downloading))
                        Spacer(Modifier.height(12.dp))
                        val fraction = s.fraction
                        if (fraction == null) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    InstallState.Installing, is InstallState.Confirm -> {
                        Text(stringResource(R.string.update_installing))
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    InstallState.NeedsPermission -> Text(stringResource(R.string.update_permission))
                    is InstallState.Failed -> {
                        Text(stringResource(R.string.update_install_failed, s.message), color = MaterialTheme.colorScheme.error)
                        if (installer.hasDownload()) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.update_system_hint), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                InstallState.Idle -> Button(onClick = { installer.start(offered) }) { Text(stringResource(R.string.update_now)) }
                InstallState.NeedsPermission -> Button(onClick = {
                    val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    runCatching { context.startActivity(settings) }
                }) { Text(stringResource(R.string.update_allow)) }
                is InstallState.Failed -> Button(onClick = { installer.start(offered) }) { Text(stringResource(R.string.update_retry)) }
                else -> Unit
            }
        },
        dismissButton = {
            when (state) {
                InstallState.Idle -> TextButton(onClick = { context.openExternally(offered.pageUrl) }) {
                    Text(stringResource(R.string.update_release_page))
                }
                is InstallState.Downloading -> TextButton(onClick = { installer.cancel() }) { Text(stringResource(R.string.update_cancel)) }
                is InstallState.Failed -> if (installer.hasDownload()) {
                    TextButton(onClick = { installer.openInSystemInstaller(context) }) { Text(stringResource(R.string.update_system)) }
                }
                else -> Unit
            }
            if (!busy) TextButton(onClick = later) { Text(stringResource(R.string.update_later)) }
        },
    )
}
