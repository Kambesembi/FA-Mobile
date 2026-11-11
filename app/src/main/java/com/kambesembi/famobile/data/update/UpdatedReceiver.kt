package com.kambesembi.famobile.data.update

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kambesembi.famobile.BuildConfig
import com.kambesembi.famobile.MainActivity
import com.kambesembi.famobile.R

/**
 * Android closes an app to install its update, and tells the new version once it is in. The app comes
 * back by itself where Android lets it (up to Android 9); later versions don't let an app open
 * itself from the background, so there a notification says the update is done and opens it on a tap.
 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT < 29) {
            runCatching { context.startActivity(open) }
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        Updates.createChannel(context)
        val tap = PendingIntent.getActivity(context, NOTIFICATION_ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, Updates.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_paw)
            .setColor(0xFFF0C27A.toInt())
            .setContentTitle(context.getString(R.string.updated_notify_title, BuildConfig.VERSION_NAME))
            .setContentText(context.getString(R.string.updated_notify_text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        const val NOTIFICATION_ID = 7201

        /** The app is open: the "updated" note has done its job. */
        fun clear(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
