package com.kambesembi.famobile.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kambesembi.famobile.R
import com.kambesembi.famobile.ui.util.openExternally

/** Asks before a link leaves FA; the app's root provides it. Null outside the app's screens (previews). */
val LocalOpenOutside = compositionLocalOf<((String) -> Unit)?> { null }

/** "Leave FA?" with the link's site in large type and the whole link below it. */
@Composable
fun OutsideLinkDialog(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val host = runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.")
    val copied = stringResource(R.string.link_copied)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
        title = { Text(stringResource(R.string.outside_link_title)) },
        text = {
            Column {
                Text(stringResource(R.string.outside_link_body))
                Spacer(Modifier.height(12.dp))
                if (!host.isNullOrBlank()) Text(host, style = MaterialTheme.typography.titleMedium)
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                context.openExternally(url)
            }) { Text(stringResource(R.string.outside_link_open)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(url))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) { Text(stringResource(R.string.outside_link_copy)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
