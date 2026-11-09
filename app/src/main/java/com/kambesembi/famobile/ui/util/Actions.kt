package com.kambesembi.famobile.ui.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.widget.Toast
import com.kambesembi.famobile.R

/** Opens a link in the phone's browser (or whichever app handles it). */
fun Context.openExternally(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show() }
}

fun Context.shareLink(title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, if (title.isBlank()) url else "$title\n$url")
    startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * Saves an FA file with Android's download manager (progress and "done" in the notification shade).
 * Images go to Pictures/FA Mobile, everything else to Download/FA Mobile.
 */
fun Context.downloadFile(url: String, userAgent: String) {
    val uri = Uri.parse(url)
    val name = uri.lastPathSegment?.substringAfter('.')?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "submission"
    val isImage = name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp")
    val request = DownloadManager.Request(uri)
        .setTitle(name)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .addRequestHeader("User-Agent", userAgent)
    CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
    if (Build.VERSION.SDK_INT >= 29) {
        val folder = if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
        request.setDestinationInExternalPublicDir(folder, "FA Mobile/$name")
    } else {
        // Before Android 10, public folders need a storage permission; the app's own folder doesn't.
        request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, name)
    }
    runCatching { (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request) }
        .onSuccess { Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show() }
        .onFailure { Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show() }
}
