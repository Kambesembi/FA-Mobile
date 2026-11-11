package com.kambesembi.famobile.data.notify

import com.kambesembi.famobile.data.net.PageSource
import java.io.IOException
import com.kambesembi.famobile.data.net.ChallengeRequired
import com.kambesembi.famobile.data.model.NoteFolder
import com.kambesembi.famobile.data.AppSettings
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kambesembi.famobile.FaApp
import com.kambesembi.famobile.data.CheckInterval
import com.kambesembi.famobile.data.FaUrls
import com.kambesembi.famobile.data.parse.ActivityParser
import com.kambesembi.famobile.data.parse.InboxParser
import com.kambesembi.famobile.data.parse.NotesParser
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import org.jsoup.nodes.Document

/**
 * Loads FA's small counts page in the background and posts a notification for each counter that
 * grew. It never shows a Cloudflare check: if one is needed, this round is skipped and the app
 * deals with it the next time it is opened.
 */
class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FaApp).container
        if (container.isDemo) return Result.success()
        // Its own connection, so a check never waits behind (or changes) what the app is loading.
        val source = container.background
        val settings = container.settings
        val page = try {
            source.fetch(FaUrls.COUNTS_PAGE)
        } catch (e: ChallengeRequired) {
            trouble(settings, loggedOut = false)
            return Result.success()
        } catch (e: IOException) {
            return Result.success() // no connection, FA busy: the next round looks again
        }
        if (page.header.user == null) {
            // With FA's header but nobody in it: the login is gone. (A bare error page says nothing.)
            if (page.header.present) trouble(settings, loggedOut = true)
            return Result.success()
        }
        settings.setCheckTrouble(0)
        val now = page.header.counts
        val news = ActivityCheck.news(settings.seenCounts(), now)
        settings.setSeenCounts(now)
        val detailed = withDetails(source, news)
        val avatars = runCatching { NotificationAvatars.load(applicationContext, container.imageLoader, detailed) }.getOrDefault(emptyMap())
        Notifier.post(applicationContext, detailed, avatars)
        return Result.success()
    }

    /**
     * Who did what: only for categories that grew, one page each (the activity page, the notes list,
     * the inbox). If one can't be loaded, that notification just says how many are new.
     */
    private suspend fun withDetails(source: PageSource, news: List<News>): List<News> {
        val needed = news.mapNotNull { NewsDetails.source(it.category) }.toSet()
        suspend fun <T> load(kind: NewsDetails.Source, path: String, parse: (Document) -> T): T? =
            if (kind in needed) quietly { parse(source.fetch(path).document) } else null
        val activity = load(NewsDetails.Source.Activity, "/msg/others/", ActivityParser::parse)
        // FA keeps the notes folder on its side. The check asks for the inbox, unless the app is showing
        // another folder right now: switching it away would mix up the list on screen.
        val appInInbox = (applicationContext as FaApp).container.notes.state.value.folder == NoteFolder.Inbox
        val notes = if (appInInbox) load(NewsDetails.Source.Notes, "/controls/switchbox/${NoteFolder.Inbox.path}/") { NotesParser.list(it).notes } else null
        val inbox = load(NewsDetails.Source.Inbox, "/msg/submissions/new@72/") { InboxParser.parse(it).items }
        return news.map { n ->
            val count = minOf(n.added, NewsDetails.MAX_LINES)
            val lines = when (NewsDetails.source(n.category)) {
                NewsDetails.Source.Activity -> activity?.let { NewsDetails.fromActivity(n.category, it, count) }
                NewsDetails.Source.Notes -> notes?.let { NewsDetails.fromNotes(it, count) }
                NewsDetails.Source.Inbox -> inbox?.let { NewsDetails.fromInbox(it, count) }
                null -> null
            }
            n.copy(lines = lines.orEmpty())
        }
    }

    /**
     * A round that couldn't look. Said once: at the first when logged out, at the third in a row for
     * Cloudflare's check (which often passes by itself the next time).
     */
    private suspend fun trouble(settings: AppSettings, loggedOut: Boolean) {
        val count = settings.checkTrouble() + 1
        settings.setCheckTrouble(count)
        if (count == (if (loggedOut) 1 else CHALLENGE_ROUNDS)) Notifier.stopped(applicationContext, loggedOut)
    }

    /** Null when it didn't work out; Android stopping the check is passed on, not swallowed. */
    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val NAME = "activity-check"
        private const val CHALLENGE_ROUNDS = 3

        /** [replace] after the interval was changed; otherwise an existing schedule is kept. */
        fun schedule(context: Context, interval: CheckInterval, replace: Boolean) {
            val work = WorkManager.getInstance(context)
            if (interval == CheckInterval.Off) {
                work.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<CheckWorker>(interval.minutes, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(
                NAME,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
