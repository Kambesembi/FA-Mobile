package com.kambesembi.famobile.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kambesembi.famobile.MainActivity
import com.kambesembi.famobile.R

/** Android notifications for new FA activity, one channel per category so each can be muted. */
object Notifier {

    private val Category.channel get() = "activity-" + name.lowercase()

    @get:StringRes
    private val Category.channelName: Int
        get() = when (this) {
            Category.Submissions -> R.string.channel_submissions
            Category.Comments -> R.string.channel_comments
            Category.Journals -> R.string.channel_journals
            Category.Favorites -> R.string.channel_favorites
            Category.Watches -> R.string.channel_watches
            Category.Notes -> R.string.channel_notes
            Category.Tickets -> R.string.channel_tickets
        }

    @get:PluralsRes
    private val Category.title: Int
        get() = when (this) {
            Category.Submissions -> R.plurals.news_submissions
            Category.Comments -> R.plurals.news_comments
            Category.Journals -> R.plurals.news_journals
            Category.Favorites -> R.plurals.news_favorites
            Category.Watches -> R.plurals.news_watches
            Category.Notes -> R.plurals.news_notes
            Category.Tickets -> R.plurals.news_tickets
        }

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            Category.entries.map { c ->
                // New submissions arrive all day; they shouldn't buzz.
                val importance = if (c == Category.Submissions) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_DEFAULT
                NotificationChannel(c.channel, context.getString(c.channelName), importance)
            },
        )
    }

    fun permissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canPost(context: Context): Boolean =
        permissionGranted(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** [avatars]: by user name, for notifications about a single person (see [NotificationAvatars]). */
    fun post(context: Context, news: List<News>, avatars: Map<String, Bitmap> = emptyMap()) {
        if (news.isEmpty() || !canPost(context)) return
        createChannels(context)
        val manager = NotificationManagerCompat.from(context)
        for (n in news) {
            val open = Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_TAB, n.category.tab)
            val pending = PendingIntent.getActivity(
                context,
                n.category.ordinal,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val count = context.resources.getQuantityString(n.category.title, n.added, n.added)
            val total = context.getString(R.string.news_total, n.total)
            val builder = NotificationCompat.Builder(context, n.category.channel)
                .setSmallIcon(R.drawable.ic_stat_paw)
                .setColor(0xFFF0C27A.toInt())
                .setNumber(n.total)
            NotificationAvatars.userOf(n)?.let { avatars[it] }?.let { builder.setLargeIcon(it) }
            val lines = n.lines
            when {
                // Just one new thing, and we know what: say it.
                n.added == 1 && lines.size == 1 -> {
                    val line = lines.single()
                    val (title, text) = single(context, line)
                    builder.setContentTitle(title).setContentText(text ?: total)
                    if (text != null) builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
                }
                // Several: the count on top, then who did what, newest first.
                lines.isNotEmpty() -> {
                    val style = NotificationCompat.InboxStyle().setBigContentTitle(count)
                    lines.forEach { style.addLine(line(context, it)) }
                    if (n.added > lines.size) style.setSummaryText(context.getString(R.string.news_more, n.added - lines.size))
                    builder.setContentTitle(count).setContentText(line(context, lines.first())).setStyle(style)
                }
                else -> builder.setContentTitle(count).setContentText(total)
            }
            val notification = builder
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            try {
                manager.notify(n.category.ordinal, notification)
            } catch (_: SecurityException) {
                return // the permission was taken away in the meantime
            }
        }
    }

    /** One line in a list: "artist2 is now watching you", "artist3: Commission question", … */
    private fun line(context: Context, line: NewsLine): String {
        val subject = line.subject ?: "…"
        return when (line.kind) {
            LineKind.Watch -> context.getString(R.string.activity_watch, line.actor)
            LineKind.SubmissionComment -> context.getString(R.string.activity_submission_comment, line.actor, subject)
            LineKind.JournalComment -> context.getString(R.string.activity_journal_comment, line.actor, subject)
            LineKind.Shout -> context.getString(R.string.activity_shout, line.actor)
            LineKind.Favorite -> context.getString(R.string.activity_favorite, line.actor, subject)
            LineKind.Journal -> context.getString(R.string.activity_journal, line.actor, subject)
            LineKind.Note -> context.getString(R.string.news_note_line, line.actor, subject)
            LineKind.Submission -> context.getString(R.string.news_submission_line, subject, line.actor)
        }
    }

    /** A notification about one thing: its title, and the text under it (null: the total). */
    private fun single(context: Context, line: NewsLine): Pair<String, String?> = when (line.kind) {
        LineKind.Note -> context.getString(R.string.news_note_from, line.actor) to line.subject
        LineKind.Submission -> context.getString(R.string.news_submission_from, line.actor) to line.subject
        else -> line(context, line) to null
    }

    /**
     * The background check can't look any more ([loggedOut], or else Cloudflare wants its check):
     * said once, so the missing notifications aren't taken for "nothing new".
     */
    fun stopped(context: Context, loggedOut: Boolean) {
        if (!canPost(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(STATUS_CHANNEL, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, STATUS_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = context.getString(if (loggedOut) R.string.check_stopped_logged_out else R.string.check_stopped_challenge)
        val notification = NotificationCompat.Builder(context, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_paw)
            .setColor(0xFFF0C27A.toInt())
            .setContentTitle(context.getString(R.string.check_stopped_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(STATUS_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private const val STATUS_CHANNEL = "status"
    private const val STATUS_ID = 7100

    /** The app is open and shows the counters, so the notifications have done their job (uploads' stay). */
    fun clear(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        Category.entries.forEach { manager.cancel(it.ordinal) }
        manager.cancel(STATUS_ID)
    }
}
