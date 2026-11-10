package com.kambesembi.famobile.data.upload

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kambesembi.famobile.FaApp
import com.kambesembi.famobile.data.UploadRepository
import com.kambesembi.famobile.data.model.UploadJob
import com.kambesembi.famobile.data.model.UploadStatus

/**
 * Sends the queued uploads one after another, as a foreground job with a progress notification, so
 * it carries on with the app closed. Without a connection it waits and tries again.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private var lastShown = 0L

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(null)

    private fun foreground(job: UploadJob?): ForegroundInfo {
        val waiting = (applicationContext as FaApp).container.uploads.store.value.jobs
            .count { it.status == UploadStatus.Queued && it.id != job?.id }
        val notification = UploadNotifier.progress(applicationContext, job, waiting)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(UploadNotifier.PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(UploadNotifier.PROGRESS_ID, notification)
        }
    }

    /** Some phones refuse a foreground start from the background; the upload then simply runs without it. */
    private suspend fun show(job: UploadJob?) {
        try {
            setForeground(foreground(job))
        } catch (_: Exception) {
            val repo = (applicationContext as FaApp).container.uploads
            UploadNotifier.showProgress(applicationContext, job, repo.store.value.jobs.count { it.status == UploadStatus.Queued })
        }
    }

    override suspend fun doWork(): Result {
        val container = (applicationContext as FaApp).container
        val uploads = container.uploads
        val source = container.uploadSource()
        uploads.recoverInterrupted()
        show(null)
        try {
            while (true) {
                val job = uploads.nextQueued() ?: break
                val outcome = uploads.send(job, source) { progress ->
                    // A notification update a few times a second at most.
                    val now = SystemClock.elapsedRealtime()
                    if (progress.status != UploadStatus.Sending || now - lastShown > 400) {
                        lastShown = now
                        val waiting = uploads.store.value.jobs.count { it.status == UploadStatus.Queued && it.id != progress.id }
                        UploadNotifier.showProgress(applicationContext, progress, waiting)
                    }
                }
                val after = uploads.store.value.jobs.firstOrNull { it.id == job.id }
                when (outcome) {
                    UploadRepository.Outcome.Offline -> return Result.retry()
                    else -> after?.let { UploadNotifier.result(applicationContext, it) }
                }
            }
        } finally {
            UploadNotifier.hideProgress(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "uploads"

        /** Starts sending; one already running just takes the new ones too, then this one finds nothing. */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
