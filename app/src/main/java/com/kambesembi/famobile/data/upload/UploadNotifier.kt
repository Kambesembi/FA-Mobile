package com.kambesembi.famobile.data.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kambesembi.famobile.MainActivity
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.FaUrls
import com.kambesembi.famobile.data.model.UploadJob
import com.kambesembi.famobile.data.model.UploadStatus
import com.kambesembi.famobile.data.notify.Notifier

/** The upload's progress while it runs, and a note when it is done or failed. */
object UploadNotifier {

    const val PROGRESS_ID = 7001
    private const val PROGRESS_CHANNEL = "uploads"
    private const val RESULT_CHANNEL = "upload-results"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(PROGRESS_CHANNEL, context.getString(R.string.channel_uploads), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(RESULT_CHANNEL, context.getString(R.string.channel_upload_results), NotificationManager.IMPORTANCE_DEFAULT),
            ),
        )
    }

    private fun openUploads(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        PROGRESS_ID,
        Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_UPLOADS),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** The ongoing one: which file, how far, and how many wait behind it. */
    fun progress(context: Context, job: UploadJob?, waiting: Int): Notification {
        createChannels(context)
        val builder = NotificationCompat.Builder(context, PROGRESS_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setColor(0xFFF0C27A.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openUploads(context))
        val title = job?.item?.title?.ifBlank { job.item.file.name }
        when {
            job == null -> builder.setContentTitle(context.getString(R.string.upload_notify_starting)).setProgress(0, 0, true)
            job.status == UploadStatus.Finishing ->
                builder.setContentTitle(context.getString(R.string.upload_notify_finishing, title)).setProgress(0, 0, true)
            job.status == UploadStatus.Queued ->
                builder.setContentTitle(context.getString(R.string.upload_notify_waiting, title)).setProgress(0, 0, true)
                    .setContentText(job.message)
            else -> builder
                .setContentTitle(context.getString(R.string.upload_notify_sending, title))
                .setProgress(100, (job.progress * 100).toInt(), false)
                .setContentText("${(job.progress * 100).toInt()} %")
        }
        if (waiting > 0) builder.setSubText(context.resources.getQuantityString(R.plurals.upload_notify_more, waiting, waiting))
        return builder.build()
    }

    fun showProgress(context: Context, job: UploadJob?, waiting: Int) {
        if (!Notifier.canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(PROGRESS_ID, progress(context, job, waiting))
        } catch (_: SecurityException) {
        }
    }

    fun hideProgress(context: Context) {
        NotificationManagerCompat.from(context).cancel(PROGRESS_ID)
    }

    /** Done: tapping it opens the new submission. Failed: tapping it opens the uploads list. */
    fun result(context: Context, job: UploadJob) {
        if (!Notifier.canPost(context)) return
        createChannels(context)
        val title = job.item.title.ifBlank { job.item.file.name }
        val builder = NotificationCompat.Builder(context, RESULT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_paw)
            .setColor(0xFFF0C27A.toInt())
            .setAutoCancel(true)
        val id = job.submissionId
        if (job.status == UploadStatus.Done && id != null) {
            val open = Intent(Intent.ACTION_VIEW, Uri.parse(FaUrls.absolute("/view/$id/")), context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            builder.setContentTitle(context.getString(R.string.upload_notify_done, title))
                .setContentText(context.getString(R.string.upload_notify_done_text))
                .setContentIntent(PendingIntent.getActivity(context, job.id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else {
            builder.setContentTitle(context.getString(R.string.upload_notify_failed, title))
                .setContentText(job.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(job.message))
                .setContentIntent(openUploads(context))
        }
        try {
            NotificationManagerCompat.from(context).notify(RESULT_BASE + (job.id.hashCode() and 0xFFFF), builder.build())
        } catch (_: SecurityException) {
        }
    }

    /** Ids at and above this belong to uploads; the activity notifications stay below. */
    const val RESULT_BASE = 7100
}
