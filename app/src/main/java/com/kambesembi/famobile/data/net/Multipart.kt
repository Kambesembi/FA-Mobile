package com.kambesembi.famobile.data.net

import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/** A file field of a multipart form. A null [file] is sent the way a browser sends an empty file input. */
class FormFile(val field: String, val file: File?, val fileName: String = file?.name.orEmpty(), val mimeType: String = "application/octet-stream")

/** How far an upload is: [sent] bytes of [total]. */
fun interface SendProgress {
    fun sent(sent: Long, total: Long)
}

/** The form as `multipart/form-data`, fields first, in the order given, like a browser. */
internal fun multipartBody(form: List<Pair<String, String>>, files: List<FormFile>): MultipartBody =
    MultipartBody.Builder().setType(MultipartBody.FORM).apply {
        form.forEach { (name, value) -> addFormDataPart(name, value) }
        files.forEach { f ->
            val body = f.file?.asRequestBody(f.mimeType.toMediaType()) ?: ByteArray(0).toRequestBody("application/octet-stream".toMediaType())
            addFormDataPart(f.field, if (f.file == null) "" else f.fileName, body)
        }
    }.build()

/** Reports every chunk that goes out. */
internal class CountingBody(private val body: RequestBody, private val progress: SendProgress) : RequestBody() {
    override fun contentType() = body.contentType()

    override fun contentLength() = body.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        val counting = object : ForwardingSink(sink) {
            var sent = 0L
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                sent += byteCount
                progress.sent(sent, total)
            }
        }.buffer()
        body.writeTo(counting)
        counting.flush()
    }
}
