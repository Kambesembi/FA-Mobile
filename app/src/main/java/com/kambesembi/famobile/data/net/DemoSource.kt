package com.kambesembi.famobile.data.net

import android.content.res.AssetManager
import com.kambesembi.famobile.data.FaUrls
import com.kambesembi.famobile.data.parse.HeaderParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okio.buffer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities

/**
 * Saved pages from `fixtures/` (bundled into debug builds only), so the CI emulator can show every
 * screen without an account. It plays FA's part too: removing from the inbox and faving change
 * what the next page shows. [headerPage] is the page for anything without a better match
 * ("header-loggedout.html" shows the login screen).
 */
class DemoSource(
    private val assets: AssetManager,
    private val headerPage: String = "header-loggedin.html",
) : PageSource {

    private var loggedOut = headerPage == "header-loggedout.html"
    private val removed = mutableSetOf<Long>()
    private var nuked = false
    private val faved = mutableSetOf<Long>()
    private val removedActivity = mutableSetOf<String>()
    private val nukedActivity = mutableSetOf<String>()
    private var noteFolder = "inbox"
    private val movedNotes = mutableMapOf<Long, String>()
    private val readNotes = mutableSetOf<Long>()
    private val unreadNotes = mutableSetOf<Long>()
    private var sentNotes = 0
    private val watched = mutableSetOf<String>()
    /** Comments and shouts written in the demo, by page ("view:123", "journal:5", "user:artist2"). */
    private val written = mutableMapOf<String, MutableList<Written>>()
    private var nextWrittenId = 90_000_001L

    /** CI only: submission pages take long, to screenshot the loading animation. */
    @Volatile
    var slowViews = false

    private class Written(val id: Long, val replyTo: Long?, val text: String)

    /** Submissions uploaded in the demo, with their titles. */
    private val uploaded = mutableMapOf<Long, String>()
    private var nextUploadId = 66_990_001L

    override suspend fun fetch(path: String): FaPage = withContext(Dispatchers.IO) {
        delay(if (slowViews && path.startsWith("/view/")) 9_000 else 300) // so loading states are visible in screenshots
        val url = FaUrls.absolute(path)
        val favMatch = Regex("^/(un)?fav/(\\d+)/").find(path)
        when {
            loggedOut -> page("header-loggedout.html", url)
            favMatch != null -> {
                val id = favMatch.groupValues[2].toLong()
                if (favMatch.groupValues[1].isEmpty()) faved += id else faved -= id
                view(id)
            }
            Regex("^/controls/(settings|site-settings|user-settings|profile|contacts)/").containsMatchIn(path) -> page("controls-settings.html", url)
            path.startsWith("/msg/submissions") -> inbox(url)
            path.startsWith("/msg/others") -> activity(url)
            path.startsWith("/controls/switchbox/") -> {
                noteFolder = path.removePrefix("/controls/switchbox/").trim('/')
                notes(FaUrls.absolute("/msg/pms/"))
            }
            Regex("^/msg/pms/\\d+/(\\d+)/").containsMatchIn(path) -> {
                val id = Regex("^/msg/pms/\\d+/(\\d+)/").find(path)!!.groupValues[1].toLong()
                readNotes += id
                unreadNotes -= id
                note(id, url)
            }
            path.startsWith("/msg/pms") -> notes(url)
            path.startsWith("/submit") -> page("submit.html", url)
            path.startsWith("/view/") -> view(Regex("/view/(\\d+)").find(path)!!.groupValues[1].toLong())
            path.startsWith("/journal/") -> journal(Regex("/journal/(\\d+)").find(path)!!.groupValues[1].toLong())
            path.startsWith("/browse") -> results("browse.html", path)
            path.startsWith("/search") -> results("search.html", path)
            userPage.containsMatchIn(path) -> {
                val (kind, name) = userPage.find(path)!!.destructured
                when (kind) {
                    "watch" -> { watched += name; userPage(name, "user", path) }
                    "unwatch" -> { watched -= name; userPage(name, "user", path) }
                    else -> userPage(name, kind, path)
                }
            }
            else -> page(headerPage, url)
        }
    }

    /** The file goes nowhere, at a slow phone's pace, so the progress can be seen; FA's details form follows. */
    override suspend fun postMultipart(
        path: String,
        form: List<Pair<String, String>>,
        files: List<FormFile>,
        progress: SendProgress,
    ): FaPage = withContext(Dispatchers.IO) {
        val body = multipartBody(form, files)
        val total = body.contentLength().coerceAtLeast(1)
        val slow = object : okio.ForwardingSink(okio.blackholeSink()) {
            override fun write(source: okio.Buffer, byteCount: Long) {
                Thread.sleep((DEMO_UPLOAD_MS * byteCount / total).coerceAtLeast(1))
                super.write(source, byteCount)
            }
        }
        val sink = slow.buffer()
        CountingBody(body, progress).writeTo(sink)
        sink.flush()
        if (!path.startsWith("/submit/upload")) return@withContext page(headerPage, FaUrls.absolute(path))
        page("submit-finalize.html", FaUrls.absolute("/submit/finalize/"))
    }

    override suspend fun post(path: String, form: List<Pair<String, String>>): FaPage = withContext(Dispatchers.IO) {
        delay(300)
        // The details of an upload: a new submission, shown with its title.
        if (path.startsWith("/submit/finalize")) {
            delay(900)
            val id = nextUploadId++
            uploaded[id] = form.firstOrNull { it.first == "title" }?.second.orEmpty()
            return@withContext view(id).let { FaPage(FaUrls.absolute("/view/$id/?upload-successful"), it.document, it.header) }
        }
        // A comment, a reply or a shout: kept, and shown on that page from now on.
        val action = form.firstOrNull { it.first == "action" }?.second
        if (action == "reply" || action == "replyto" || action == "shout") {
            val text = form.firstOrNull { it.first == "reply" || it.first == "shout" }?.second.orEmpty()
            val replyTo = form.firstOrNull { it.first == "replyto" }?.second?.toLongOrNull()?.takeIf { action == "replyto" }
            val id = Regex("^/(?:view|journal)/(\\d+)").find(path)?.groupValues?.get(1)?.toLong()
            val user = Regex("^/user/([^/?#]+)").find(path)?.groupValues?.get(1)
            val key = when {
                path.startsWith("/view/") && id != null -> "view:$id"
                path.startsWith("/journal/") && id != null -> "journal:$id"
                user != null -> "user:$user"
                else -> null
            }
            if (key != null && text.isNotBlank()) written.getOrPut(key) { mutableListOf() } += Written(nextWrittenId++, replyTo, text)
            return@withContext when {
                path.startsWith("/view/") && id != null -> view(id)
                path.startsWith("/journal/") && id != null -> journal(id)
                user != null -> userPage(user, "user", "/user/$user/")
                else -> page(headerPage, FaUrls.absolute(path))
            }
        }
        if (path.startsWith("/msg/pms")) {
            val ids = form.filter { it.first == "items[]" }.mapNotNull { it.second.toLongOrNull() }
            when (val to = form.firstOrNull { it.first == "move_to" }?.second) {
                "unread" -> { unreadNotes += ids; readNotes -= ids.toSet() }
                null -> Unit
                else -> ids.forEach { movedNotes[it] = to }
            }
            return@withContext notes(FaUrls.absolute(path))
        }
        if (path.startsWith("/msg/send")) {
            sentNotes++
            return@withContext notes(FaUrls.absolute("/msg/pms/"))
        }
        if (path.startsWith("/msg/others")) {
            form.forEach { (name, value) ->
                if (name.endsWith("[]")) removedActivity += "$name=$value"
                if (name.startsWith("nuke-")) nukedActivity += name
            }
            return@withContext activity(FaUrls.absolute(path))
        }
        when (form.firstOrNull { it.first == "messagecenter-action" }?.second) {
            "remove_checked" -> removed += form.filter { it.first == "submissions[]" }.mapNotNull { it.second.toLongOrNull() }
            "nuke_notifications" -> nuked = true
        }
        inbox(FaUrls.absolute(path))
    }

    private fun inbox(url: String): FaPage {
        val doc = document("msg-submissions.html", url)
        doc.select("figure[id^=sid-]").forEach { figure ->
            val id = figure.id().removePrefix("sid-").toLongOrNull()
            if (nuked || (id != null && id in removed)) figure.remove()
        }
        // The saved inbox only counts its own submissions; show the demo account's other counts too.
        val left = doc.select("figure[id^=sid-]").size
        val counters = document(headerPage, url).selectFirst(".message-bar-desktop")
        counters?.select("a.notification-container[href*=/msg/submissions]")?.forEach {
            if (left == 0) it.remove() else it.text("${left}S").attr("title", "$left Submission Notifications")
        }
        if (counters != null) {
            doc.select(".message-bar-desktop").forEach { it.html(counters.html()) }
            doc.select(".mobile-notification-bar").forEach { it.html(counters.html()) }
        }
        return FaPage(url, doc, HeaderParser.parse(doc))
    }

    /** The saved notes list, in the chosen folder, with what was moved, read or marked unread. */
    private fun notes(url: String): FaPage {
        val doc = document("msg-pms.html", url)
        doc.select(".c-noteListItem").forEach { row ->
            val id = row.selectFirst("input[name=items[]]")?.attr("value")?.toLongOrNull() ?: return@forEach
            val link = row.selectFirst("a.notelink")
            if (id in readNotes) link?.removeClass("note-unread")?.removeClass("unread")?.addClass("note-read")?.addClass("read")
            if (id in unreadNotes) link?.removeClass("note-read")?.removeClass("read")?.addClass("note-unread")?.addClass("unread")
            val unread = link?.hasClass("unread") == true
            val keep = when (noteFolder) {
                "inbox" -> id !in movedNotes
                "unread" -> id !in movedNotes && unread
                "archive", "trash" -> movedNotes[id] == noteFolder
                else -> false // sent and the priority folders: nothing saved for them
            }
            if (!keep) row.remove()
        }
        if (noteFolder != "inbox") doc.select(".note-pagination").remove()
        return FaPage(url, doc, HeaderParser.parse(doc))
    }

    /** Every note shows the one saved note page, under its own id. */
    private fun note(id: Long, url: String): FaPage {
        val doc = Jsoup.parse(text("msg-pms-note.html").replace("130000005", id.toString()), url)
        return FaPage(url, doc, HeaderParser.parse(doc))
    }

    /** The saved activity page, without what was removed or cleared. */
    private fun activity(url: String): FaPage {
        val doc = document("msg-others.html", url)
        doc.select("section[id^=messages-]").forEach { section ->
            if (section.select("button[name]").any { it.attr("name") in nukedActivity }) {
                section.remove()
                return@forEach
            }
            section.select("li").forEach { li ->
                val box = li.selectFirst("input[type=checkbox]")
                if (box != null && "${box.attr("name")}=${box.attr("value")}" in removedActivity) li.remove()
            }
        }
        return FaPage(url, doc, HeaderParser.parse(doc))
    }

    /** Every submission shows the one saved submission page, under its own id and fav state. */
    private fun view(id: Long): FaPage {
        val url = FaUrls.absolute("/view/$id/")
        var html = text("view-image-loggedin.html").replace("10000610", id.toString())
        if (id in faved) html = html.replace(">+Fav<", ">-Fav<").replace("/fav/$id/", "/unfav/$id/")
        val doc = Jsoup.parse(html, url)
        uploaded[id]?.let { title -> doc.selectFirst(".submission-title h2")?.text(title) }
        demoComic(id, doc)
        addWritten(doc, "view:$id", "#comments-submission", newestFirst = false)
        return FaPage(url, doc, HeaderParser.parse(doc))
    }

    /** A short made-up comic, linked page to page the way artists do it, for the comic reader. */
    private fun demoComic(id: Long, doc: Document) {
        val n = (id - COMIC_FIRST + 1).toInt()
        if (n !in 1..COMIC_PAGES) return
        doc.selectFirst(".submission-title h2")?.text("Demo Comic - Page $n")
        val links = buildList {
            if (n > 1) add("""<a href="/view/$COMIC_FIRST/">&lt;&lt; First</a>""")
            if (n > 1) add("""<a href="/view/${id - 1}/">« Prev</a>""")
            if (n < COMIC_PAGES) add("""<a href="/view/${id + 1}/">Next »</a>""")
        }
        doc.selectFirst(".submission-description-text")?.html("Page $n of the demo comic.<br><br>" + links.joinToString(" | "))
    }

    /** Every journal shows the one saved journal page, under its own id. */
    private fun journal(id: Long): FaPage {
        val url = FaUrls.absolute("/journal/$id/")
        val doc = Jsoup.parse(text("journal.html").replace("11000003", id.toString()), url)
        addWritten(doc, "journal:$id", "#comments-journal", newestFirst = false)
        return FaPage(url, doc, HeaderParser.parse(doc))
    }

    /**
     * What was written in the demo, as FA would show it: by the demo account, a reply right under the
     * comment it answers and one step narrower; shouts newest first, comments at the end.
     */
    private fun addWritten(doc: Document, key: String, container: String, newestFirst: Boolean) {
        val box = doc.selectFirst(container) ?: return
        for (item in written[key].orEmpty()) {
            val target = item.replyTo?.let { id ->
                box.select(".comment_container").firstOrNull { it.selectFirst("a.comment_anchor")?.id() == "cid:$id" }
            }
            val width = target?.attr("style")
                ?.let { Regex("""width:\s*(\d+)%""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
                ?.let { it - 3 } ?: 100
            val anchor = if (newestFirst) "shout-${item.id}" else "cid:${item.id}"
            val text = Entities.escape(item.text).replace("\n", "<br>")
            val now = System.currentTimeMillis() / 1000
            val html = """<div class="comment_container" style="width:$width%"><a id="$anchor" class="comment_anchor"></a>
                <comment-container><div class="avatar"><a href="/user/testuser/"><img class="comment_useravatar" src="//a.furaffinity.net/1700000000/testuser.gif" alt="testuser"></a></div>
                <div class="comment-content"><comment-username><div class="c-usernameBlock">
                <a class="c-usernameBlock__displayName js-displayName-block" href="/user/testuser/"><span class="js-displayName">TestUser</span></a>
                <a class="c-usernameBlock__userName js-userName-block" href="/user/testuser/"><span>~testuser</span></a></div></comment-username>
                <comment-date><span class="popup_date" data-time="$now">just now</span></comment-date>
                <comment-user-text class="comment_text"><div class="user-submitted-links">$text</div></comment-user-text>
                </div></comment-container></div>"""
            when {
                target != null -> target.after(html)
                newestFirst -> box.prepend(html)
                else -> box.append(html)
            }
        }
    }

    /**
     * The saved user pages, for any user: artist2's pages with the name swapped in. A second page
     * of the gallery, favorites or journals shows other ids and is the last one.
     */
    private fun userPage(name: String, kind: String, path: String): FaPage {
        val url = FaUrls.absolute(path)
        val file = when (kind) {
            "gallery", "scraps" -> "user-gallery.html"
            "favorites" -> "user-favorites.html"
            "journals" -> "user-journals.html"
            else -> "user-page.html"
        }
        var html = text(file)
        val secondPage = path.substringBefore('?').trimEnd('/').substringAfter("/$name").isNotEmpty()
        if (secondPage) {
            // Shift the ids, so page two adds new items.
            html = html.replace(Regex("(sid-|/view/|t\\.furaffinity\\.net/|jid:|/journal/)(\\d+)")) { m ->
                m.groupValues[1] + (m.groupValues[2].toLong() - 1000)
            }
        }
        if (name != "artist2") html = html.replace("artist2", name).replace("Artist2", name.replaceFirstChar { it.uppercase() })
        val doc = Jsoup.parse(html, url)
        if (secondPage) {
            doc.select("form[method=get]").remove()
            doc.select("a.button").filter { it.text().trim() == "Older" }.forEach { it.remove() }
        }
        if (kind == "scraps") {
            doc.select("figure[id^=sid-]").drop(3).forEach { it.remove() }
            doc.select("form[method=get]").remove()
        }
        if (kind == "user") addWritten(doc, "user:$name", "#shouts", newestFirst = true)
        val watch = doc.selectFirst("a#watch-button")
        when {
            name == "testuser" -> watch?.remove()
            name in watched -> watch?.attr("href", "/unwatch/$name/?key=0000000000")?.text("-Watch")
        }
        return FaPage(url, doc, HeaderParser.parse(doc))
    }

    /**
     * Browse and search: the saved page, with other ids on later pages; page 3 is the last. A search
     * for "nothing" finds nothing.
     */
    private fun results(file: String, path: String): FaPage {
        val url = FaUrls.absolute(path)
        val page = Regex("""[?&]page=(\d+)""").find(path)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        var html = text(file)
        if (page > 1) {
            html = html.replace(Regex("""(sid-|/view/|t\.furaffinity\.net/)(\d+)""")) { m ->
                m.groupValues[1] + (m.groupValues[2].toLong() - 1000L * (page - 1))
            }
        }
        val doc = Jsoup.parse(html, url)
        if (page >= 3) doc.select("button").filter { it.text().trim() == "Next" }.forEach { it.attr("disabled", "disabled") }
        if (path.contains("q=nothing")) doc.select("figure").remove()
        return FaPage(url, doc, HeaderParser.parse(doc))
    }

    private fun page(name: String, url: String): FaPage {
        val doc = document(name, url)
        return FaPage(url, doc, HeaderParser.parse(doc))
    }

    private fun document(name: String, url: String): Document = Jsoup.parse(text(name), url)

    private fun text(name: String): String = assets.open(name).bufferedReader().use { it.readText() }

    private companion object {
        const val DEMO_UPLOAD_MS = 3500L
        const val COMIC_FIRST = 66_950_001L
        const val COMIC_PAGES = 3
    }

    private val userPage = Regex("^/(user|gallery|scraps|favorites|journals|watch|unwatch)/([^/?#]+)")

    override fun clearSession() {
        loggedOut = true
    }
}
