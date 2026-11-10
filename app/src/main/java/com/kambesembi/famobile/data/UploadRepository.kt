package com.kambesembi.famobile.data

import android.content.Context
import android.net.Uri
import com.kambesembi.famobile.R
import com.kambesembi.famobile.data.model.Keywords
import com.kambesembi.famobile.data.model.UploadCodec
import com.kambesembi.famobile.data.model.UploadDetails
import com.kambesembi.famobile.data.model.UploadDraft
import com.kambesembi.famobile.data.model.UploadItem
import com.kambesembi.famobile.data.model.UploadJob
import com.kambesembi.famobile.data.model.UploadKind
import com.kambesembi.famobile.data.model.UploadLists
import com.kambesembi.famobile.data.model.UploadNames
import com.kambesembi.famobile.data.model.UploadStatus
import com.kambesembi.famobile.data.model.UploadStore
import com.kambesembi.famobile.data.net.ChallengeRequired
import com.kambesembi.famobile.data.net.FormFile
import com.kambesembi.famobile.data.net.PageSource
import com.kambesembi.famobile.data.parse.ExploreParser
import com.kambesembi.famobile.data.parse.UploadParser
import com.kambesembi.famobile.data.upload.UploadFiles
import com.kambesembi.famobile.data.upload.UploadProblem
import com.kambesembi.famobile.data.upload.UploadWorker
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Document

/** What the upload screen learned from FA when it opened. */
data class UploadPrep(
    val loading: Boolean = false,
    /** Couldn't reach FA's upload page: why. Uploading still works; the queue tries again. */
    val error: String? = null,
    val maxBytes: Long = UploadFiles.DEFAULT_MAX_BYTES,
    val kinds: Set<UploadKind> = UploadKind.entries.toSet(),
    val loaded: Boolean = false,
)

/** A step of the upload that FA (or the file) said no to. */
private class UploadRefused(message: String) : IOException(message)

/** FA's flood protection turned this step away; it may be tried again after [seconds]. */
private class FloodWait(val seconds: Int, message: String) : IOException(message)

/**
 * Uploads: the form being filled in, the queue the background work sends one by one, and what the
 * next form starts with. Everything is kept in one small file, so a closed app loses nothing.
 */
class UploadRepository(
    private val app: Context,
    private val fa: FaClient,
    private val scope: CoroutineScope,
    private val demo: Boolean,
) {
    private val root = UploadFiles.root(app)
    private val stateFile = File(root, "state.json")
    private val draftDir = File(root, "draft")

    private val _store = MutableStateFlow(recover(UploadCodec.decode(read())))
    val store: StateFlow<UploadStore> = _store.asStateFlow()

    private val _prep = MutableStateFlow(UploadPrep())
    val prep: StateFlow<UploadPrep> = _prep.asStateFlow()

    /** The latest state waits here to be written; only the newest one matters. */
    private val saves = Channel<UploadStore>(Channel.CONFLATED)

    init {
        scope.launch(Dispatchers.IO) {
            for (store in saves) runCatching {
                root.mkdirs()
                val tmp = File(root, "state.json.tmp")
                tmp.writeText(UploadCodec.encode(store))
                if (!tmp.renameTo(stateFile)) {
                    stateFile.delete()
                    tmp.renameTo(stateFile)
                }
            }
        }
    }

    private fun read(): String? = runCatching { stateFile.takeIf { it.exists() }?.readText() }.getOrNull()

    /** After the app was killed mid-upload: start the send again, but never guess whether FA saved it. */
    private fun recover(store: UploadStore): UploadStore = store.copy(jobs = store.jobs.map(::interrupted))

    /** A job whose send was cut off: the file goes again; with the details already on their way, FA may have it. */
    private fun interrupted(job: UploadJob): UploadJob = when (job.status) {
        UploadStatus.Sending -> job.copy(status = UploadStatus.Queued, progress = 0f)
        UploadStatus.Finishing -> job.copy(status = UploadStatus.Failed, message = app.getString(R.string.upload_interrupted))
        else -> job
    }

    /**
     * Called by the background work before it sends anything: only it sends, so whatever still says
     * "sending" was cut off (Android stopped the work when the connection went) and never said so.
     */
    fun recoverInterrupted() = update(change = ::recover)

    private fun update(persist: Boolean = true, change: (UploadStore) -> UploadStore) {
        val next = _store.updateAndGet(change)
        if (persist) saves.trySend(next)
    }

    /** Called when the app starts: anything left in the queue goes on. */
    fun resume() {
        if (_store.value.jobs.any { it.status == UploadStatus.Queued }) UploadWorker.enqueue(app)
    }

    // ---- The form

    fun editDraft(change: (UploadDraft) -> UploadDraft) = update { it.copy(draft = change(it.draft)) }

    /** A fresh form starts with the last upload's choices. */
    fun startDraft() = update { s ->
        if (s.draft.isEmpty && s.draft.details == UploadDetails()) {
            s.copy(draft = UploadDraft(details = s.preset))
        } else {
            s
        }
    }

    /** Copies picked or shared files in; returns how many FA can take (the rest are skipped). */
    suspend fun addFiles(uris: List<Uri>): Int {
        var added = 0
        for (uri in uris) {
            val file = UploadFiles.import(app, uri, draftDir) ?: continue
            val kind = UploadKind.of(file.mimeType, file.name)
            if (kind == null) {
                File(file.path).delete()
                continue
            }
            added++
            update { s -> s.copy(draft = s.draft.copy(items = s.draft.items + UploadItem(file, UploadNames.titleFrom(file.name), kind))) }
        }
        return added
    }

    /** A cover for a story or a song. */
    suspend fun setThumbnail(itemPath: String, uri: Uri?) {
        val thumb = uri?.let { UploadFiles.import(app, it, draftDir) }?.takeIf { it.isImage }
        update { s ->
            s.copy(draft = s.draft.copy(items = s.draft.items.map { item ->
                if (item.file.path != itemPath) return@map item
                item.thumbnail?.let { File(it.path).delete() }
                item.copy(thumbnail = thumb)
            }))
        }
    }

    suspend fun addDemoPicture() {
        val file = UploadFiles.demoPicture(draftDir)
        update { s ->
            s.copy(draft = s.draft.copy(items = s.draft.items + UploadItem(file, UploadNames.titleFrom(file.name), UploadKind.Artwork)))
        }
    }

    fun removeItem(path: String) {
        val item = _store.value.draft.items.firstOrNull { it.file.path == path } ?: return
        update { s -> s.copy(draft = s.draft.copy(items = s.draft.items.filter { it.file.path != path })) }
        scope.launch(Dispatchers.IO) {
            File(item.file.path).delete()
            item.thumbnail?.let { File(it.path).delete() }
        }
    }

    /** Throws the form away, files and all, and forgets the last choices: the next form starts at FA's defaults. */
    fun discardDraft() {
        val items = _store.value.draft.items
        update { s -> s.copy(draft = UploadDraft(), preset = UploadDetails()) }
        scope.launch(Dispatchers.IO) {
            items.forEach { item ->
                File(item.file.path).delete()
                item.thumbnail?.let { File(it.path).delete() }
            }
        }
    }

    /**
     * Reads FA's upload page (through the app, so a Cloudflare check can show), and the lists and
     * folders for the form when they aren't known yet.
     */
    suspend fun prepare(username: String?) {
        if (_prep.value.loading) return
        _prep.update { it.copy(loading = true, error = null) }
        try {
            val page = fa.page(SUBMIT_PATH)
            val submit = UploadParser.submitPage(page.document)
            _prep.update {
                it.copy(
                    maxBytes = submit?.maxFileSize ?: UploadFiles.DEFAULT_MAX_BYTES,
                    kinds = submit?.kinds ?: UploadKind.entries.toSet(),
                    error = if (submit == null) UploadParser.problem(page.document) ?: app.getString(R.string.upload_no_form) else null,
                )
            }
            if (_store.value.lists.isEmpty) {
                val options = ExploreParser.browse(fa.page("/browse/").document).options
                val lists = UploadLists(
                    categories = options.categories.filterNot(UploadParser::isNoChoice),
                    themes = options.types.filterNot(UploadParser::isNoChoice),
                    species = options.species.filterNot(UploadParser::isNoChoice),
                )
                if (!lists.isEmpty) update { s -> if (s.lists.exact) s else s.copy(lists = lists) }
            }
            if (username != null) {
                val folders = UploadParser.galleryFolders(fa.page("/gallery/$username/").document)
                update { it.copy(folders = folders) }
            }
        } catch (e: IOException) {
            _prep.update { it.copy(error = e.message ?: app.getString(R.string.error_network)) }
        } finally {
            _prep.update { it.copy(loading = false, loaded = true) }
        }
    }

    /** Puts the form's files in the queue and starts sending; the next form starts with these choices. */
    fun submit(): Int {
        val draft = _store.value.draft
        if (draft.items.isEmpty()) return 0
        val now = System.currentTimeMillis()
        // Files set apart stay behind for forms of their own.
        val jobs = draft.sending.mapIndexed { i, item ->
            val id = UUID.randomUUID().toString()
            UploadJob(id, moveTo(File(root, id), item), draft.details.forItem(item), created = now + i)
        }
        val done = _store.value.jobs.filter { it.status == UploadStatus.Done }.sortedBy { it.created }
        val dropped = done.dropLast(MAX_DONE).map { it.id }.toSet()
        scope.launch(Dispatchers.IO) { dropped.forEach { File(root, it).deleteRecursively() } }
        update { s ->
            s.copy(
                draft = draft.next(draft.details.preset()),
                jobs = (s.jobs.filter { it.id !in dropped } + jobs).sortedBy { it.created },
                preset = draft.details.preset(),
                recentKeywords = Keywords.merge(draft.details.keywords, s.recentKeywords).take(MAX_KEYWORDS),
            )
        }
        UploadWorker.enqueue(app)
        return jobs.size
    }

    /** The files move out of the form's folder, so a new form can start right away. */
    private fun moveTo(dir: File, item: UploadItem): UploadItem {
        dir.mkdirs()
        fun move(path: String): String {
            val from = File(path)
            val to = File(dir, from.name)
            return if (from.renameTo(to)) to.path else path
        }
        return item.copy(
            file = item.file.copy(path = move(item.file.path)),
            thumbnail = item.thumbnail?.let { it.copy(path = move(it.path)) },
        )
    }

    /** Again, after a failure. A Cloudflare check is done in the app first. */
    suspend fun retry(id: String) {
        val job = _store.value.jobs.firstOrNull { it.id == id } ?: return
        if (job.needsCheck && !demo) {
            try {
                fa.page(SUBMIT_PATH)
            } catch (e: IOException) {
                setJob(id) { it.copy(message = e.message ?: app.getString(R.string.error_network)) }
                return
            }
        }
        setJob(id) { it.copy(status = UploadStatus.Queued, message = null, needsCheck = false, attempts = 0, progress = 0f) }
        UploadWorker.enqueue(app)
    }

    /** Back into the form, to change what FA didn't like. */
    fun edit(id: String) {
        val job = _store.value.jobs.firstOrNull { it.id == id && !it.active } ?: return
        update { s ->
            s.copy(
                draft = UploadDraft(items = s.draft.items + job.item, details = if (s.draft.items.isEmpty()) job.details else s.draft.details),
                jobs = s.jobs.filter { it.id != id },
            )
        }
    }

    /** Forgets an upload that isn't on its way; its files go too. */
    fun remove(id: String) {
        val job = _store.value.jobs.firstOrNull { it.id == id } ?: return
        if (job.status == UploadStatus.Sending || job.status == UploadStatus.Finishing) return
        update { s -> s.copy(jobs = s.jobs.filter { it.id != id }) }
        scope.launch(Dispatchers.IO) { File(root, id).deleteRecursively() }
    }

    fun clearFinished() {
        val done = _store.value.jobs.filter { it.status == UploadStatus.Done }.map { it.id }.toSet()
        update { s -> s.copy(jobs = s.jobs.filter { it.id !in done }) }
        scope.launch(Dispatchers.IO) { done.forEach { File(root, it).deleteRecursively() } }
    }

    private fun setJob(id: String, persist: Boolean = true, change: (UploadJob) -> UploadJob) =
        update(persist) { s -> s.copy(jobs = s.jobs.map { if (it.id == id) change(it) else it }) }

    // ---- The queue (the background work calls these)

    fun nextQueued(): UploadJob? = _store.value.jobs.firstOrNull { it.status == UploadStatus.Queued }

    enum class Outcome { Done, Failed, Offline }

    /**
     * Sends one upload the way FA's pages do: the file form (fresh key), the file with progress, then
     * the details form FA answers with, filled in. FA sending us to the new submission means done.
     */
    suspend fun send(job: UploadJob, source: PageSource, onProgress: (UploadJob) -> Unit): Outcome {
        val id = job.id
        // FA turns away an upload that comes too soon after the one before: wait the gap out first.
        val since = System.currentTimeMillis() - _store.value.lastUploadAt
        if (since in 0 until UPLOAD_GAP_MS) {
            if (!pause(id, ((UPLOAD_GAP_MS - since + 999) / 1000).toInt(), onProgress)) return Outcome.Failed
        }
        var floodWaits = 0
        while (true) {
            try {
                return sendOnce(job, source, onProgress)
            } catch (e: FloodWait) {
                // Turned away before FA kept anything: wait as long as FA says, then start over.
                if (++floodWaits > MAX_FLOOD_WAITS) return fail(id, e.message.orEmpty())
                if (!pause(id, e.seconds + 2, onProgress)) return Outcome.Failed
            }
        }
    }

    /** Waits [seconds] with a countdown on the upload; false when the upload was removed meanwhile. */
    private suspend fun pause(id: String, seconds: Int, onProgress: (UploadJob) -> Unit): Boolean {
        for (left in seconds downTo 1) {
            if (current(id) == null) return false
            setJob(id, persist = false) { it.copy(status = UploadStatus.Queued, progress = 0f, message = app.getString(R.string.upload_flood_wait, left)) }
            current(id)?.let(onProgress)
            delay(1000)
        }
        return current(id) != null
    }

    /** FA's message on [doc] as the reason to stop, or a [FloodWait] when it's FA's flood protection. */
    private fun refused(doc: Document, fallback: Int): IOException {
        val message = UploadParser.problem(doc)
        UploadParser.floodSeconds(message)?.let { return FloodWait(it, message.orEmpty()) }
        return UploadRefused(message ?: app.getString(fallback))
    }

    private suspend fun sendOnce(job: UploadJob, source: PageSource, onProgress: (UploadJob) -> Unit): Outcome {
        val id = job.id
        var finishing = false
        setJob(id) { it.copy(status = UploadStatus.Sending, progress = 0f, message = null, needsCheck = false) }
        current(id)?.let(onProgress)
        return try {
            val submitPage = source.fetch(SUBMIT_PATH)
            val submit = UploadParser.submitPage(submitPage.document)
            if (submit == null) {
                if (submitPage.header.present && submitPage.header.user == null) throw UploadRefused(app.getString(R.string.upload_logged_out))
                throw refused(submitPage.document, R.string.upload_no_form)
            }
            val kind = job.item.kind
            if (kind !in submit.kinds) throw UploadRefused(app.getString(R.string.upload_kind_unavailable))
            val max = submit.maxFileSize ?: UploadFiles.DEFAULT_MAX_BYTES
            val ready = UploadFiles.prepare(app, job.item.file, kind, job.details.stripLocation, max)
            val thumb = job.item.thumbnail?.let { UploadFiles.prepare(app, it, UploadKind.Artwork, true, max) }
            val files = listOf(
                FormFile("submission", ready.file, ready.name, ready.mimeType),
                FormFile("thumbnail", thumb?.file, thumb?.name.orEmpty(), thumb?.mimeType ?: "application/octet-stream"),
            )
            var shown = -1
            val answer = source.postMultipart(submit.form.action, submit.fields(kind), files) { sent, total ->
                if (total <= 0) return@postMultipart
                val percent = (sent * 100 / total).toInt()
                if (percent != shown) {
                    shown = percent
                    setJob(id, persist = false) { it.copy(progress = percent / 100f) }
                    current(id)?.let(onProgress)
                }
            }
            val finalize = UploadParser.finalizePage(answer.document)
            if (finalize == null) {
                // Some uploads may go straight to the submission.
                UploadParser.submissionId(answer.url)?.let { return done(id, it, onProgress) }
                throw refused(answer.document, R.string.upload_not_accepted)
            }
            // FA's own lists, from now on, for the form's choices.
            val lists = finalize.lists
            if (!lists.isEmpty) update { it.copy(lists = lists) }
            finalize.folders.takeIf { it.isNotEmpty() }?.let { folders -> update { it.copy(folders = folders) } }
            finishing = true
            setJob(id) { it.copy(status = UploadStatus.Finishing, progress = 1f) }
            current(id)?.let(onProgress)
            val fields = UploadParser.fill(finalize, job.item, job.details)
            var result = source.post(finalize.form.action, fields)
            var floodWaits = 0
            while (UploadParser.submissionId(result.url) == null) {
                // The details turned away by the flood protection: the same form again after the wait.
                val seconds = UploadParser.floodSeconds(UploadParser.problem(result.document)) ?: break
                if (++floodWaits > MAX_FLOOD_WAITS || !pause(id, seconds + 2, onProgress)) break
                setJob(id) { it.copy(status = UploadStatus.Finishing, progress = 1f, message = null) }
                current(id)?.let(onProgress)
                result = source.post(finalize.form.action, fields)
            }
            val submission = UploadParser.submissionId(result.url)
                ?: throw UploadRefused(UploadParser.problem(result.document) ?: app.getString(R.string.upload_not_confirmed))
            done(id, submission, onProgress)
        } catch (e: FloodWait) {
            throw e
        } catch (e: CancellationException) {
            // Android stopped the work (the connection went, or its time was up): the next run takes it up.
            setJob(id, change = ::interrupted)
            throw e
        } catch (e: ChallengeRequired) {
            fail(id, app.getString(R.string.upload_needs_check), needsCheck = true)
        } catch (e: UploadRefused) {
            fail(id, e.message.orEmpty())
        } catch (e: UploadProblem) {
            fail(id, e.message.orEmpty())
        } catch (e: IOException) {
            if (finishing) return fail(id, app.getString(R.string.upload_interrupted))
            // Nothing was saved on FA yet: try again when there's a connection, a few times.
            val attempts = job.attempts + 1
            if (attempts >= MAX_ATTEMPTS) return fail(id, app.getString(R.string.upload_network_failed, e.message ?: ""))
            setJob(id) { it.copy(status = UploadStatus.Queued, progress = 0f, attempts = attempts, message = app.getString(R.string.upload_waiting)) }
            current(id)?.let(onProgress)
            Outcome.Offline
        }
    }

    private fun current(id: String) = _store.value.jobs.firstOrNull { it.id == id }

    private suspend fun done(id: String, submission: Long, onProgress: (UploadJob) -> Unit): Outcome {
        // Only a small picture stays, for the list.
        val item = current(id)?.item
        val picture = item?.file?.takeIf { it.isImage } ?: item?.thumbnail
        val preview = UploadFiles.keepPreview(File(root, id), picture)
        setJob(id) { job ->
            val kept = preview?.let { UploadFiles.describe(it, job.item.file.name, "image/jpeg").copy(path = it.path) }
            val shown = when {
                kept == null -> job.item
                job.item.file.isImage -> job.item.copy(file = kept.copy(name = job.item.file.name))
                else -> job.item.copy(thumbnail = kept)
            }
            job.copy(status = UploadStatus.Done, submissionId = submission, progress = 1f, message = null, item = shown)
        }
        update { it.copy(lastUploadAt = System.currentTimeMillis()) }
        current(id)?.let(onProgress)
        return Outcome.Done
    }

    private fun fail(id: String, message: String, needsCheck: Boolean = false): Outcome {
        setJob(id) { it.copy(status = UploadStatus.Failed, message = message, needsCheck = needsCheck) }
        return Outcome.Failed
    }

    fun clear() {
        _prep.value = UploadPrep()
    }

    private companion object {
        const val SUBMIT_PATH = "/submit/"
        const val MAX_ATTEMPTS = 4
        const val MAX_DONE = 20
        const val MAX_KEYWORDS = 60
        const val MAX_FLOOD_WAITS = 3

        /** Between two uploads: FA's flood protection asked for 16 s after one; 30 s leaves room. */
        const val UPLOAD_GAP_MS = 30_000L
    }
}
