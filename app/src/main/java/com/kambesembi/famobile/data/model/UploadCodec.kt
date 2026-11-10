package com.kambesembi.famobile.data.model

import org.json.JSONArray
import org.json.JSONObject

/** What the uploads keep on the phone: the form, the queue, and what the next form starts with. */
data class UploadStore(
    val draft: UploadDraft = UploadDraft(),
    val jobs: List<UploadJob> = emptyList(),
    /** The choices of the last upload. */
    val preset: UploadDetails = UploadDetails(),
    /** Keywords used before, most recent first. */
    val recentKeywords: List<String> = emptyList(),
    val lists: UploadLists = UploadLists(),
    /** The account's gallery folders, as last seen. */
    val folders: List<FilterOption> = emptyList(),
    /** When FA last took an upload (ms since 1970): FA turns away the next one for a while. */
    val lastUploadAt: Long = 0,
    /** The FA account (URL name, lower case) the form and the choices belong to; null until one was seen. */
    val account: String? = null,
    /** The half-filled forms of other accounts on this phone, kept for when they log in again. */
    val parked: Map<String, UploadDraft> = emptyMap(),
) {
    /** What [account] gets to see: its own uploads, not another account's. */
    val shown: UploadStore get() = copy(jobs = jobs.filter { it.account == null || it.account == account })
}

/** [UploadStore] as JSON, for a file in the app's storage. Unknown or broken parts fall back to defaults. */
object UploadCodec {

    fun encode(store: UploadStore): String = JSONObject().apply {
        put("draft", draft(store.draft))
        put("jobs", JSONArray(store.jobs.map(::job)))
        put("preset", details(store.preset))
        put("keywords", JSONArray(store.recentKeywords))
        put("lists", lists(store.lists))
        put("folders", options(store.folders))
        put("lastUpload", store.lastUploadAt)
        store.account?.let { put("account", it) }
        if (store.parked.isNotEmpty()) put("parked", JSONObject().apply { store.parked.forEach { (name, d) -> put(name, draft(d)) } })
    }.toString()

    fun decode(text: String?): UploadStore {
        val json = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return UploadStore()
        return UploadStore(
            draft = json.optJSONObject("draft")?.let(::draft) ?: UploadDraft(),
            jobs = json.optJSONArray("jobs").objects().mapNotNull { runCatching { job(it) }.getOrNull() },
            preset = json.optJSONObject("preset")?.let(::details) ?: UploadDetails(),
            recentKeywords = json.optJSONArray("keywords").strings(),
            lists = json.optJSONObject("lists")?.let(::lists) ?: UploadLists(),
            folders = options(json.optJSONArray("folders")),
            lastUploadAt = json.optLong("lastUpload"),
            account = json.optStringOrNull("account"),
            parked = json.optJSONObject("parked")?.let { o ->
                o.keys().asSequence().mapNotNull { name -> o.optJSONObject(name)?.let { name to draft(it) } }.toMap()
            }.orEmpty(),
        )
    }

    private fun draft(d: UploadDraft) = JSONObject().apply {
        put("items", JSONArray(d.items.map(::item)))
        put("details", details(d.details))
    }

    private fun draft(o: JSONObject) = UploadDraft(
        items = o.optJSONArray("items").objects().mapNotNull { runCatching { item(it) }.getOrNull() },
        details = o.optJSONObject("details")?.let(::details) ?: UploadDetails(),
    )

    private fun job(j: UploadJob) = JSONObject().apply {
        put("id", j.id)
        put("item", item(j.item))
        put("details", details(j.details))
        put("status", j.status.name)
        j.submissionId?.let { put("submission", it) }
        j.message?.let { put("message", it) }
        put("needsCheck", j.needsCheck)
        put("attempts", j.attempts)
        put("created", j.created)
        j.account?.let { put("account", it) }
    }

    private fun job(o: JSONObject) = UploadJob(
        id = o.getString("id"),
        item = item(o.getJSONObject("item")),
        details = o.optJSONObject("details")?.let(::details) ?: UploadDetails(),
        status = enumOr(o.optString("status"), UploadStatus.Failed),
        submissionId = if (o.has("submission")) o.getLong("submission") else null,
        message = o.optStringOrNull("message"),
        needsCheck = o.optBoolean("needsCheck"),
        attempts = o.optInt("attempts"),
        created = o.optLong("created"),
        account = o.optStringOrNull("account"),
    )

    private fun item(i: UploadItem) = JSONObject().apply {
        put("file", file(i.file))
        put("title", i.title)
        put("kind", i.kind.name)
        i.thumbnail?.let { put("thumbnail", file(it)) }
        i.rating?.let { put("rating", it.name) }
        i.genders?.let { put("genders", JSONArray(it)) }
        if (i.separate) put("separate", true)
    }

    private fun item(o: JSONObject) = UploadItem(
        file = file(o.getJSONObject("file")),
        title = o.optString("title"),
        kind = enumOr(o.optString("kind"), UploadKind.Artwork),
        thumbnail = o.optJSONObject("thumbnail")?.let(::file),
        rating = o.optStringOrNull("rating")?.let { runCatching { Rating.valueOf(it) }.getOrNull() },
        genders = o.optJSONArray("genders")?.strings(),
        separate = o.optBoolean("separate"),
    )

    private fun file(f: UploadFile) = JSONObject().apply {
        put("path", f.path)
        put("name", f.name)
        put("mime", f.mimeType)
        put("size", f.size)
        f.width?.let { put("width", it) }
        f.height?.let { put("height", it) }
    }

    private fun file(o: JSONObject) = UploadFile(
        path = o.getString("path"),
        name = o.optString("name"),
        mimeType = o.optString("mime", "application/octet-stream"),
        size = o.optLong("size"),
        width = if (o.has("width")) o.getInt("width") else null,
        height = if (o.has("height")) o.getInt("height") else null,
    )

    private fun details(d: UploadDetails) = JSONObject().apply {
        d.rating?.let { put("rating", it.name) }
        put("description", d.description)
        put("keywords", JSONArray(d.keywords))
        d.category?.let { put("category", choice(it)) }
        d.theme?.let { put("theme", choice(it)) }
        d.species?.let { put("species", choice(it)) }
        put("folders", JSONArray(d.folders.map(::choice)))
        put("scraps", d.scraps)
        put("lockComments", d.lockComments)
        put("stripLocation", d.stripLocation)
    }

    private fun details(o: JSONObject) = UploadDetails(
        rating = o.optStringOrNull("rating")?.let { runCatching { Rating.valueOf(it) }.getOrNull() },
        description = o.optString("description"),
        keywords = o.optJSONArray("keywords").strings(),
        category = o.optJSONObject("category")?.let(::choice),
        theme = o.optJSONObject("theme")?.let(::choice),
        species = o.optJSONObject("species")?.let(::choice),
        folders = o.optJSONArray("folders").objects().map(::choice),
        scraps = o.optBoolean("scraps"),
        lockComments = o.optBoolean("lockComments"),
        stripLocation = o.optBoolean("stripLocation", true),
    )

    private fun choice(c: UploadChoice) = JSONObject().put("value", c.value).put("label", c.label)

    private fun choice(o: JSONObject) = UploadChoice(o.optString("value"), o.optString("label"))

    private fun lists(l: UploadLists) = JSONObject().apply {
        put("categories", options(l.categories))
        put("themes", options(l.themes))
        put("species", options(l.species))
        put("exact", l.exact)
    }

    private fun lists(o: JSONObject) = UploadLists(
        categories = options(o.optJSONArray("categories")),
        themes = options(o.optJSONArray("themes")),
        species = options(o.optJSONArray("species")),
        exact = o.optBoolean("exact"),
    )

    private fun options(list: List<FilterOption>) = JSONArray(
        list.map { o -> JSONObject().put("value", o.value).put("label", o.label).apply { o.group?.let { put("group", it) } } },
    )

    private fun options(array: JSONArray?): List<FilterOption> =
        array.objects().map { FilterOption(it.optString("value"), it.optString("label"), it.optStringOrNull("group")) }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).ifEmpty { null } }

    private fun JSONObject.optStringOrNull(name: String): String? = if (has(name) && !isNull(name)) optString(name) else null

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        runCatching { enumValueOf<E>(name) }.getOrDefault(fallback)
}
