package com.kambesembi.famobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kambesembi.famobile.data.model.SavedArtist
import com.kambesembi.famobile.data.model.SavedCodec
import com.kambesembi.famobile.data.model.SavedSubmission
import com.kambesembi.famobile.data.notify.ActivityCheck
import com.kambesembi.famobile.data.parse.Counts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { System, Dark, Light }

/** How often the background check looks for new activity. */
enum class CheckInterval(val minutes: Long) { Off(0), Min15(15), Min30(30), Hour1(60), Hour3(180) }

private val Context.dataStore by preferencesDataStore(name = "settings")

class AppSettings(private val context: Context) {

    companion object {
        const val DEFAULT_COLUMNS = 2
    }

    private val themeKey = stringPreferencesKey("theme")
    private val intervalKey = stringPreferencesKey("check_interval")
    private val seenKey = stringPreferencesKey("seen_counts")
    private val askedKey = booleanPreferencesKey("asked_notifications")
    private val savedArtistsKey = stringPreferencesKey("saved_artists")
    private val savedSubmissionsKey = stringPreferencesKey("saved_submissions")
    private val gridColumnsKey = intPreferencesKey("grid_columns")
    private val sfwKey = booleanPreferencesKey("sfw")
    private val faSettingsKey = stringSetPreferencesKey("fa_settings")
    private val updateChecksKey = booleanPreferencesKey("update_checks")
    private val comicHorizontalKey = booleanPreferencesKey("comic_horizontal")
    private val notifiedUpdateKey = stringPreferencesKey("notified_update")
    private val troubleKey = intPreferencesKey("check_trouble")

    val theme: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    /** Submissions per row in every grid (portrait), 1–5. */
    val gridColumns: Flow<Int> = context.dataStore.data.map { (it[gridColumnsKey] ?: DEFAULT_COLUMNS).coerceIn(1, 5) }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { it[gridColumnsKey] = columns.coerceIn(1, 5) }
    }

    /** SFW mode: only General submissions in the grids (off by default). */
    val sfw: Flow<Boolean> = context.dataStore.data.map { it[sfwKey] ?: false }

    suspend fun setSfw(on: Boolean) {
        context.dataStore.edit { it[sfwKey] = on }
    }

    /** The entries chosen for the "FA settings" page (ids from FaSetting.all). */
    val faSettings: Flow<Set<String>> = context.dataStore.data.map { it[faSettingsKey] ?: emptySet() }

    suspend fun setFaSettings(ids: Set<String>) {
        context.dataStore.edit { it[faSettingsKey] = ids }
    }

    /** The comic reader: pages side by side (one per swipe) instead of one under the other. */
    val comicHorizontal: Flow<Boolean> = context.dataStore.data.map { it[comicHorizontalKey] ?: false }

    suspend fun setComicHorizontal(on: Boolean) {
        context.dataStore.edit { it[comicHorizontalKey] = on }
    }

    /** Whether the app looks for new versions on GitHub (on by default). */
    val updateChecks: Flow<Boolean> = context.dataStore.data.map { it[updateChecksKey] ?: true }

    suspend fun setUpdateChecks(on: Boolean) {
        context.dataStore.edit { it[updateChecksKey] = on }
    }

    /** The newest version a notification was shown for, so each one is announced once. */
    suspend fun notifiedUpdate(): String? = context.dataStore.data.first()[notifiedUpdateKey]

    suspend fun setNotifiedUpdate(version: String) {
        context.dataStore.edit { it[notifiedUpdateKey] = version }
    }

    val checkInterval: Flow<CheckInterval> = context.dataStore.data.map { prefs ->
        prefs[intervalKey]?.let { runCatching { CheckInterval.valueOf(it) }.getOrNull() } ?: CheckInterval.Min30
    }

    suspend fun setCheckInterval(interval: CheckInterval) {
        context.dataStore.edit { it[intervalKey] = interval.name }
    }

    /** The counters as last seen, in the app or by the background check. */
    suspend fun seenCounts(): Counts? = ActivityCheck.decode(context.dataStore.data.first()[seenKey])

    suspend fun setSeenCounts(counts: Counts) {
        context.dataStore.edit { it[seenKey] = ActivityCheck.encode(counts) }
    }

    /** Background checks in a row that couldn't look (logged out, or Cloudflare wants its check); 0 when all is well. */
    suspend fun checkTrouble(): Int = context.dataStore.data.first()[troubleKey] ?: 0

    suspend fun setCheckTrouble(count: Int) {
        if (checkTrouble() != count) context.dataStore.edit { it[troubleKey] = count }
    }

    /** Whether the app already asked for the notification permission once (Android 13+). */
    suspend fun askedForNotifications(): Boolean = context.dataStore.data.first()[askedKey] == true

    suspend fun setAskedForNotifications() {
        context.dataStore.edit { it[askedKey] = true }
    }

    /** Saved artists, newest first. */
    val savedArtists: Flow<List<SavedArtist>> = context.dataStore.data.map { SavedCodec.decodeArtists(it[savedArtistsKey]) }

    /** Saves the artist, or removes them when already saved. */
    suspend fun toggleSavedArtist(artist: SavedArtist) {
        context.dataStore.edit { prefs ->
            val current = SavedCodec.decodeArtists(prefs[savedArtistsKey])
            val next = if (current.any { it.name == artist.name }) current.filter { it.name != artist.name } else listOf(artist) + current
            prefs[savedArtistsKey] = SavedCodec.encodeArtists(next)
        }
    }

    /** Saved submissions, newest first. */
    val savedSubmissions: Flow<List<SavedSubmission>> =
        context.dataStore.data.map { SavedCodec.decodeSubmissions(it[savedSubmissionsKey]) }

    suspend fun toggleSavedSubmission(submission: SavedSubmission) {
        context.dataStore.edit { prefs ->
            val current = SavedCodec.decodeSubmissions(prefs[savedSubmissionsKey])
            val next = if (current.any { it.id == submission.id }) current.filter { it.id != submission.id } else listOf(submission) + current
            prefs[savedSubmissionsKey] = SavedCodec.encodeSubmissions(next)
        }
    }
}
