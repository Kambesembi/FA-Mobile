package com.kambesembi.famobile.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** The app's languages; [System] follows the phone. Names are shown in their own language. */
enum class AppLanguage(val tag: String, val endonym: String?) {
    System("", null),
    English("en", "English"),
    German("de", "Deutsch"),
    Russian("ru", "Русский"),
    ;

    companion object {
        fun of(tag: String?): AppLanguage = entries.firstOrNull { it.tag.isNotEmpty() && it.tag == tag } ?: System

        /** The language chosen for the app. */
        fun current(context: Context): AppLanguage =
            if (Build.VERSION.SDK_INT >= 33) {
                of(context.getSystemService(LocaleManager::class.java).applicationLocales.get(0)?.language)
            } else {
                of(prefs(context).getString(KEY, null))
            }

        /**
         * Switches the app to [language]. Android 13+ keeps the choice itself and redraws the app;
         * older versions keep it here and [activity] starts over in the new language.
         */
        fun set(activity: Activity, language: AppLanguage) {
            if (Build.VERSION.SDK_INT >= 33) {
                activity.getSystemService(LocaleManager::class.java).applicationLocales =
                    if (language == System) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
            } else {
                prefs(activity).edit().putString(KEY, language.tag).apply()
                activity.recreate()
            }
        }

        /** Before Android 13: [base] in the chosen language, for an activity's or the app's attachBaseContext. */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= 33) return base
            val language = of(prefs(base).getString(KEY, null))
            if (language == System) return base
            val locale = Locale.forLanguageTag(language.tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
            return base.createConfigurationContext(config)
        }

        // SharedPreferences, not DataStore: attachBaseContext has to read it right away.
        private fun prefs(context: Context) = context.getSharedPreferences("language", Context.MODE_PRIVATE)
        private const val KEY = "tag"
    }
}
