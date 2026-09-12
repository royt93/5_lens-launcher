package com.mckimquyen.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.util.Locale

object LocaleHelper {
    private const val SELECTED_LANGUAGE = "Locale.Helper.Selected.Language"
    private const val IS_LANGUAGE_SELECTED = "Locale.Helper.Language.Selected"

    data class AppLanguage(val code: String, val nativeName: String, val flag: String, val englishName: String)

    val supportedLanguages = listOf(
        AppLanguage("en", "English", "🇺🇸", "English"),
        AppLanguage("vi", "Tiếng Việt", "🇻🇳", "Vietnamese"),
        AppLanguage("th", "ภาษาไทย", "🇹🇭", "Thai"),
        AppLanguage("zh", "简体中文", "🇨🇳", "Chinese (Simplified)"),
        AppLanguage("ja", "日本語", "🇯🇵", "Japanese"),
        AppLanguage("ko", "한국어", "🇰🇷", "Korean"),
        AppLanguage("ru", "Русский", "🇷🇺", "Russian"),
        AppLanguage("de", "Deutsch", "🇩🇪", "German"),
        AppLanguage("fr", "Français", "🇫🇷", "French"),
        AppLanguage("es", "Español", "🇪🇸", "Spanish"),
        AppLanguage("pt", "Português", "🇵🇹", "Portuguese"),
        AppLanguage("hi", "हिन्दी", "🇮🇳", "Hindi"),
        AppLanguage("km", "ភាសាខ្មែរ", "🇰🇭", "Khmer"),
        AppLanguage("lo", "ພາສາລາວ", "🇱🇦", "Lao"),
        AppLanguage("ar", "العربية", "🇸🇦", "Arabic"),
        AppLanguage("in", "Bahasa Indonesia", "🇮🇩", "Indonesian"),
        AppLanguage("it", "Italiano", "🇮🇹", "Italian")
    )

    fun onAttach(context: Context): Context {
        val lang = getLanguage(context)
        return setLocale(context, lang)
    }

    fun getLanguage(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return preferences.getString(SELECTED_LANGUAGE, Locale.getDefault().language) ?: "en"
    }

    fun isLanguageSelected(context: Context): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return preferences.getBoolean(IS_LANGUAGE_SELECTED, false)
    }

    fun setLanguageSelected(context: Context) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        preferences.edit { putBoolean(IS_LANGUAGE_SELECTED, true) }
    }

    fun setLocale(context: Context, language: String): Context {
        persist(context, language)

        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)

        // minSdk is 25, so SDK_INT is always >= N (API 24) — the pre-N legacy path can't run.
        return updateResources(context, locale)
    }

    private fun persist(context: Context, language: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        preferences.edit { putString(SELECTED_LANGUAGE, language) }
    }

    // AppBundleLocaleChanges is a false positive here: app/build.gradle's `bundle.language.enableSplit`
    // is already `false` (language resources are not split), which is exactly what this check asks for —
    // lint just can't see across module Gradle config into this file.
    @SuppressLint("AppBundleLocaleChanges")
    private fun updateResources(context: Context, locale: Locale): Context {
        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}
