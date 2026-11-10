package com.kambesembi.famobile.data.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.model.UploadFile
import com.kambesembi.famobile.data.model.UploadKind
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Something about a file that FA won't take, said so the user knows what to do. */
class UploadProblem(message: String) : IOException(message)

/** A file as it will be sent. */
class ReadyFile(val file: File, val name: String, val mimeType: String)

/**
 * Files for uploads: copied in from other apps, and made to fit FA (JPEG, PNG or GIF up to the size
 * limit, without the phone's location) right before they are sent.
 */
object UploadFiles {

    /** FA's limit when its page doesn't say otherwise. */
    const val DEFAULT_MAX_BYTES = 10L * 1024 * 1024

    fun root(context: Context): File = File(context.filesDir, "uploads")

    /** Copies what another app shared or the picker returned, so it stays when that app is gone. */
    suspend fun import(context: Context, uri: Uri, dir: File): UploadFile? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name: String? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
        }
        val mime = resolver.getType(uri) ?: guessMime(name ?: uri.lastPathSegment.orEmpty())
        val fileName = safeName(name ?: uri.lastPathSegment ?: "upload", mime)
        dir.mkdirs()
        val target = unique(dir, fileName)
        val copied = runCatching {
            resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
        }.getOrNull()
        if (copied == null || target.length() == 0L) {
            target.delete()
            return@withContext null
        }
        describe(target, fileName, mime)
    }

    /** A file the app made itself (the demo's picture). */
    fun describe(file: File, name: String = file.name, mime: String = guessMime(name)): UploadFile {
        var width: Int? = null
        var height: Int? = null
        if (mime.startsWith("image/")) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth > 0) {
                val turned = rotation(file) % 180 != 0
                width = if (turned) bounds.outHeight else bounds.outWidth
                height = if (turned) bounds.outWidth else bounds.outHeight
            }
        }
        return UploadFile(file.path, name, mime, file.length(), width, height)
    }

    /**
     * The file as FA will take it. Artwork in another format, or over [maxBytes], is re-encoded (and
     * scaled down until it fits); photos lose their location when [stripLocation].
     */
    suspend fun prepare(context: Context, upload: UploadFile, kind: UploadKind, stripLocation: Boolean, maxBytes: Long): ReadyFile =
        withContext(Dispatchers.IO) {
            val file = File(upload.path)
            if (!file.exists()) throw UploadProblem(context.getString(R.string.upload_file_missing, upload.name))
            val ext = upload.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val image = upload.isImage || ext in UploadKind.Artwork.extensions
            if (kind != UploadKind.Artwork || !image) {
                if (file.length() > maxBytes) throw UploadProblem(context.getString(R.string.upload_too_big, upload.name, maxBytes / (1024 * 1024)))
                if (ext !in kind.extensions) throw UploadProblem(context.getString(R.string.upload_wrong_type, upload.name))
                return@withContext ReadyFile(file, upload.name, upload.mimeType)
            }
            val accepted = ext in UploadKind.Artwork.extensions
            if (ext == "gif") {
                // Animated GIFs can't be shrunk without losing the animation.
                if (file.length() > maxBytes) throw UploadProblem(context.getString(R.string.upload_gif_too_big, maxBytes / (1024 * 1024)))
                return@withContext ReadyFile(file, upload.name, "image/gif")
            }
            if (accepted && file.length() <= maxBytes) {
                // Couldn't be taken out: write the picture anew, which leaves all of the photo's data behind.
                if (stripLocation && !removeLocation(file)) return@withContext reencode(context, file, upload.name, maxBytes)
                return@withContext ReadyFile(file, upload.name, if (ext == "png") "image/png" else "image/jpeg")
            }
            reencode(context, file, upload.name, maxBytes)
        }

    /** Decodes the picture (turned upright) and writes it as JPEG, or PNG when it has transparency, small enough. */
    private fun reencode(context: Context, file: File, name: String, maxBytes: Long): ReadyFile = try {
        reencodeUnguarded(context, file, name, maxBytes)
    } catch (e: OutOfMemoryError) {
        // The upload fails with a reason; the app (and the rest of the queue) carries on.
        throw UploadProblem(context.getString(R.string.upload_too_big, name, maxBytes / (1024 * 1024)))
    }

    private fun reencodeUnguarded(context: Context, file: File, name: String, maxBytes: Long): ReadyFile {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) throw UploadProblem(context.getString(R.string.upload_unreadable, name))
        // Very large pictures are decoded smaller right away, so they fit in memory.
        var sample = 1
        while (bounds.outWidth.toLong() * bounds.outHeight / (sample * sample) > MAX_DECODE_PIXELS) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw UploadProblem(context.getString(R.string.upload_unreadable, name))
        var bitmap = upright(decoded, rotation(file))
        val png = bitmap.hasAlpha()
        val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val outName = name.substringBeforeLast('.') + if (png) ".png" else ".jpg"
        val out = unique(file.parentFile ?: file, "ready-$outName")
        var quality = 92
        while (true) {
            out.outputStream().use { bitmap.compress(format, quality, it) }
            if (out.length() <= maxBytes) break
            if (!png && quality > 82) {
                quality = 82
                continue
            }
            if (bitmap.width < 600) throw UploadProblem(context.getString(R.string.upload_too_big, name, maxBytes / (1024 * 1024)))
            val smaller = Bitmap.createScaledBitmap(bitmap, (bitmap.width * 0.85f).toInt(), (bitmap.height * 0.85f).toInt(), true)
            if (smaller !== bitmap) bitmap.recycle()
            bitmap = smaller
        }
        bitmap.recycle()
        return ReadyFile(out, outName, if (png) "image/png" else "image/jpeg")
    }

    private fun upright(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
        if (turned !== bitmap) bitmap.recycle()
        return turned
    }

    private fun rotation(file: File): Int = runCatching {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
    }.getOrDefault(0)

    /**
     * Takes the GPS tags out of the app's own copy; the rest of the photo's data stays. False when
     * the file's data couldn't be read or the tags are still there afterwards.
     */
    private fun removeLocation(file: File): Boolean = runCatching {
        fun present() = ExifInterface(file.path).let { exif -> GPS_TAGS.filter { exif.getAttribute(it) != null } }
        val tags = present()
        if (tags.isEmpty()) return@runCatching true
        ExifInterface(file.path).apply {
            tags.forEach { setAttribute(it, null) }
            saveAttributes()
        }
        present().isEmpty()
    }.getOrDefault(false)

    /**
     * After the upload: only a small picture stays in [dir] for the uploads list, the files go.
     * Returns the picture, or null when there's nothing to show.
     */
    suspend fun keepPreview(dir: File, picture: UploadFile?): File? = withContext(Dispatchers.IO) {
        val out = File(dir, "preview.jpg")
        val made = picture?.let { p ->
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(p.path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= PREVIEW_PX && bounds.outHeight / (sample * 2) >= PREVIEW_PX) sample *= 2
                val bitmap = BitmapFactory.decodeFile(p.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching false
                out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                bitmap.recycle()
                true
            }.getOrDefault(false)
        } == true
        dir.listFiles()?.filter { it != out }?.forEach { it.delete() }
        if (made) out else null
    }

    private const val PREVIEW_PX = 256

    /** A picture for demo mode: no files on the CI emulator to pick from. */
    suspend fun demoPicture(dir: File): UploadFile = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, 900f, 0xFF2B1B4D.toInt(), 0xFFF0A35E.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1200f, 900f, paint)
        paint.shader = null
        paint.color = 0xFFFFE2A8.toInt()
        canvas.drawCircle(820f, 560f, 150f, paint)
        paint.color = 0xFF1A1230.toInt()
        canvas.drawRect(0f, 700f, 1200f, 900f, paint)
        val file = unique(dir, "evening_sketch.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        describe(file, file.name, "image/png")
    }

    fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
    }

    /** A plain file name with the right extension (shared files sometimes come without one). */
    private fun safeName(name: String, mime: String): String {
        val clean = name.substringAfterLast('/').replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().ifEmpty { "upload" }
        if (clean.contains('.')) return clean
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: return clean
        return "$clean.$ext"
    }

    private fun unique(dir: File, name: String): File {
        var file = File(dir, name)
        var n = 2
        while (file.exists()) {
            file = File(dir, name.substringBeforeLast('.') + "-$n" + name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" })
            n++
        }
        return file
    }

    private const val MAX_DECODE_PIXELS = 16_000_000L

    private val GPS_TAGS = listOf(
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_DEST_LATITUDE, ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE, ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
        ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_TRACK, ExifInterface.TAG_GPS_TRACK_REF,
    )
}
