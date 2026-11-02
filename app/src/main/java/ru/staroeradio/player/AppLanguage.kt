package ru.staroeradio.player

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/** Russian is the default; an explicit English choice persists across launches. */
object AppLanguage {
    const val PREF_LANGUAGE = "app_language"

    fun selection(context: Context): String =
        context.getSharedPreferences(PlaybackService.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_LANGUAGE, "ru").let { if (it == "en") "en" else "ru" }

    fun localized(context: Context): Context {
        val language = selection(context)
        val configuration = Configuration(context.resources.configuration)
        val locale = Locale.forLanguageTag(language)
        configuration.setLocales(LocaleList(locale))
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    fun save(context: Context, language: String) {
        context.getSharedPreferences(PlaybackService.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_LANGUAGE, language).apply()
    }
}
