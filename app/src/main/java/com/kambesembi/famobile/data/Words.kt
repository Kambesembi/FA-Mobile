package com.kambesembi.famobile.data

import android.content.Context
import androidx.annotation.StringRes
import com.kambesembi.famobile.R

/**
 * The app's own words for when FA gives none (FA's messages stay as FA sends them). The data layer
 * has no screen to ask for a text, so the app hands its context in at start; without one (unit
 * tests) the English text stands.
 */
object Words {
    enum class Text(val english: String, @StringRes val res: Int) {
        Network("Network error", R.string.error_network),
        PageUnreadable("Fur Affinity's page couldn't be read", R.string.words_page_unreadable),
        UnexpectedPage("Fur Affinity sent an unexpected page", R.string.words_unexpected_page),
        NoSubmission("This submission can't be shown.", R.string.words_no_submission),
        NoProfile("This user page couldn't be read.", R.string.words_no_profile),
        NoJournal("This journal can't be shown.", R.string.words_no_journal),
        NoNote("This note couldn't be shown.", R.string.words_no_note),
        NotePrepare("Couldn't prepare the note. Please try again.", R.string.words_note_prepare),
        NoteUnconfirmed("Fur Affinity didn't confirm the note was sent.", R.string.words_note_unconfirmed),
        SettingOnFa("This setting can't be changed from the app; use \"Change on FA\".", R.string.words_setting_on_fa),
        SettingRefused("Fur Affinity didn't take the change.", R.string.words_setting_refused),
        SlowDown("Fur Affinity asks to slow down; try again in a moment.", R.string.words_slow_down),
        UpdateUnreadable("GitHub's answer couldn't be read", R.string.words_update_unreadable),
    }

    /** Set once by the app; it follows the chosen language. */
    @Volatile
    var context: Context? = null

    fun get(text: Text): String = runCatching { context?.getString(text.res) }.getOrNull() ?: text.english
}
