package com.tcltv.sahne.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/**
 * In-app language choice. Kept in plain SharedPreferences rather than DataStore because it must be read
 * synchronously in attachBaseContext, before the activity has any coroutine scope.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    TURKISH("tr"),
    ENGLISH("en");

    companion object {
        private const val PREFS = "app_language"
        private const val KEY = "language"

        fun current(context: Context): AppLanguage {
            val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return entries.firstOrNull { it.name == stored } ?: SYSTEM
        }

        fun save(context: Context, language: AppLanguage) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.name).apply()
        }

        /** Returns a context whose resources, date formats and Locale.getDefault() follow the chosen language. */
        fun wrap(base: Context): Context {
            val tag = current(base).tag
            if (tag == null) {
                // Undo a previous in-app choice so dates and city search follow the system again.
                Locale.setDefault(Resources.getSystem().configuration.locales[0])
                return base
            }
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
            return base.createConfigurationContext(config)
        }
    }
}
