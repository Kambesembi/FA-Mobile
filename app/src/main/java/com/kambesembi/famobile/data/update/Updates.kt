package com.kambesembi.famobile.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kambesembi.famobile.BuildConfig
import com.kambesembi.famobile.FaApp
import com.kambesembi.famobile.MainActivity
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.AppSettings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * The app's update check: what the settings show, one notification per new version, and the
 * "update now?" question while the app is open.
 */
class Updates(private val context: Context, private val settings: AppSettings) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Unknown)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _offer = MutableStateFlow<Release?>(null)

    /** The release the app asks to update to right now; null when it doesn't ask. */
    val offer: StateFlow<Release?> = _offer.asStateFlow()
    private var declined: String? = null

    /** Asks GitHub now; a version not announced before gets a notification unless [notify] is off. */
    suspend fun check(notify: Boolean = true): UpdateState {
        _state.value = UpdateState.Checking
        val result = UpdateCheck.check(BuildConfig.VERSION_NAME)
        _state.value = result
        if (result is UpdateState.Available && settings.notifiedUpdate() != result.release.version) {
            if (notify) notify(context, result.release)
            settings.setNotifiedUpdate(result.release.version)
        }
        return result
    }

    /** With the app open: looks for a new version and asks about it, once per version unless [force]. */
    suspend fun checkAndOffer(force: Boolean = false) {
        val result = check(notify = false)
        if (result is UpdateState.Available && (force || result.release.version != declined)) _offer.value = result.release
    }

    /** Asks about [release] now (the update button in the settings). */
    fun offer(release: Release) {
        _offer.value = release
    }

    /** "Later": not asked again about this version until the app starts anew. */
    fun decline() {
        declined = _offer.value?.version
        _offer.value = null
    }

    /** The check twice a day in the background, or none when turned off. */
    fun schedule(enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build(),
            )
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        private const val WORK = "update-check"
        private const val CHANNEL = "updates"
        private const val NOTIFICATION_ID = 7200

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        /** "Version 0.1.31 is available": tapping it opens the app, which asks to update. */
        fun notify(context: Context, release: Release) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            createChannel(context)
            val open = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_UPDATE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val tap = PendingIntent.getActivity(context, NOTIFICATION_ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_paw)
                .setColor(0xFFF0C27A.toInt())
                .setContentTitle(context.getString(R.string.update_notify_title, release.version))
                .setContentText(context.getString(R.string.update_notify_text))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
        }
    }
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as FaApp).container
        if (!container.settings.updateChecks.first()) return Result.success()
        container.updates.check()
        return Result.success()
    }
}
