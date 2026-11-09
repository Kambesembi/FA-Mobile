package com.kambesembi.famobile.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.kambesembi.famobile.BuildConfig
import com.kambesembi.famobile.FaApp
import com.kambesembi.famobile.R
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface InstallState {
    data object Idle : InstallState

    /** [fraction] is null while the size isn't known. */
    data class Downloading(val fraction: Float?) : InstallState

    /** Handed to Android, which shows its own "update this app?" screen. */
    data object Installing : InstallState

    /** Android first needs the OK for this app to install apps. */
    data object NeedsPermission : InstallState

    /** Android's confirmation screen, to be shown by the app. */
    data class Confirm(val intent: Intent) : InstallState

    data class Failed(val message: String) : InstallState
}

/**
 * Downloads a release's APK from GitHub and installs it over this app with Android's package
 * installer. Android checks that it is signed with the same key, and asks the user to confirm.
 */
class Installer(private val context: Context, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()
    private var job: Job? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val folder get() = File(context.cacheDir, "update")

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun start(release: Release) {
        if (job?.isActive == true) return
        job = scope.launch { install(release) }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = InstallState.Idle
    }

    fun reset() {
        if (job?.isActive != true) _state.value = InstallState.Idle
    }

    /** The confirmation screen is up; what the user chose comes back to [InstallReceiver]. */
    fun confirmShown() {
        _state.value = InstallState.Installing
    }

    /** A download left from an earlier update. */
    fun cleanUp() {
        if (job?.isActive != true) runCatching { folder.deleteRecursively() }
    }

    private suspend fun install(release: Release) {
        val url = release.apkUrl
        if (url == null) {
            _state.value = InstallState.Failed(context.getString(R.string.update_no_apk))
            return
        }
        if (!canInstall()) {
            _state.value = InstallState.NeedsPermission
            return
        }
        _state.value = InstallState.Downloading(null)
        try {
            val file = download(url)
            check(file)
            _state.value = InstallState.Installing
            commit(file)
        } catch (e: CancellationException) {
            _state.value = InstallState.Idle
            throw e
        } catch (e: Exception) {
            _state.value = InstallState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun download(url: String): File = withContext(Dispatchers.IO) {
        val dir = folder.apply {
            deleteRecursively()
            mkdirs()
        }
        val file = File(dir, "update.apk")
        val request = Request.Builder().url(url).header("User-Agent", "FA-Mobile-updater").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("HTTP ${response.code}")
            val total = body.contentLength()
            body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var shown = -1
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != shown) {
                                shown = percent
                                _state.value = InstallState.Downloading(done.toFloat() / total)
                            }
                        }
                    }
                }
            }
            if (total > 0 && file.length() != total) throw IOException(context.getString(R.string.update_bad_file))
        }
        file
    }

    /** It is this app, and newer than the running one. The signature is Android's to check. */
    private fun check(file: File) {
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
        if (info == null || info.packageName != context.packageName ||
            PackageInfoCompat.getLongVersionCode(info) <= BuildConfig.VERSION_CODE
        ) throw IOException(context.getString(R.string.update_bad_file))
    }

    private suspend fun commit(file: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            // Once the app installed itself, later updates may go through without asking (Android 12+).
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { output ->
                file.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            // Mutable: Android puts the result into it.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val result = PendingIntent.getBroadcast(context, id, Intent(context, InstallReceiver::class.java), flags)
            session.commit(result.intentSender)
        }
    }

    internal fun onResult(status: Int, message: String?, confirm: Intent?) {
        _state.value = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                confirm?.let { InstallState.Confirm(it) } ?: InstallState.Failed(message ?: "status $status")
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> InstallState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                InstallState.Failed(context.getString(R.string.update_conflict))
            else -> InstallState.Failed(message ?: "status $status")
        }
    }
}

/** What Android's package installer says about an update. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        @Suppress("DEPRECATION")
        val confirm = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
        (context.applicationContext as FaApp).container.installer
            .onResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE), confirm)
    }
}
