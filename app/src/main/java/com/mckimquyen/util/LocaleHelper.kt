package com.mckimquyen.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.util.Locale

object LocaleHelper {
    private const val SELECTED_LANGUAGE = "Locale.Helper.Selected.Language"
    private const val IS_LANGUAGE_SELECTED = "Locale.Helper.Language.Selected"
    private const val FRAMEWORK_LOCALE_MIGRATED = "Locale.Helper.Framework.Migrated"

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
        val applicationContext = context.applicationContext
        val lang = if (Build.VERSION.SDK_INT >= 33) {
            val localeManager = applicationContext.getSystemService(android.app.LocaleManager::class.java)
            val appLocales = localeManager?.applicationLocales
            val frameworkTag = appLocales?.takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
            val effectiveTag = migrateLegacyLocaleIfNeeded(applicationContext, frameworkTag.orEmpty())
            resourceLanguageForAndroidTag(effectiveTag)
        } else {
            getLanguage(applicationContext)
        }
        return updateResources(context, Locale.forLanguageTag(lang))
    }

    fun getLanguage(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val localeManager = context.applicationContext.getSystemService(android.app.LocaleManager::class.java)
            val appLocales = localeManager?.applicationLocales
            if (appLocales != null && !appLocales.isEmpty) {
                return resourceLanguageForAndroidTag(appLocales.get(0).toLanguageTag())
            }
            val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            val oldValue = preferences.getString(SELECTED_LANGUAGE, null)
            val systemLanguage = context.applicationContext.resources.configuration.locales[0].language
            return if (preferences.getBoolean(FRAMEWORK_LOCALE_MIGRATED, false)) {
                resourceLanguageForAndroidTag(systemLanguage)
            } else {
                oldValue ?: resourceLanguageForAndroidTag(systemLanguage)
            }
        }
        val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return preferences.getString(SELECTED_LANGUAGE, Locale.getDefault().language) ?: "en"
    }

    internal fun toAndroidLanguageTag(resourceCode: String): String =
        if (resourceCode == "in") "id" else resourceCode

    internal fun toResourceLanguageCode(androidTag: String): String =
        if (androidTag == "id") "in" else androidTag

    fun resolveFrameworkLanguage(
        frameworkTag: String,
        migrationComplete: Boolean,
        legacyLanguage: String?,
        systemLanguage: String
    ): String = when {
        frameworkTag.isNotEmpty() -> resourceLanguageForAndroidTag(frameworkTag)
        migrationComplete -> resourceLanguageForAndroidTag(systemLanguage)
        !legacyLanguage.isNullOrBlank() -> legacyLanguage
        else -> resourceLanguageForAndroidTag(systemLanguage)
    }

    internal fun migrateLegacyLocaleIfNeeded(context: Context, frameworkTag: String): String {
        if (Build.VERSION.SDK_INT < 33) return frameworkTag
        val applicationContext = context.applicationContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val migrationComplete = preferences.getBoolean(FRAMEWORK_LOCALE_MIGRATED, false)
        val legacy = preferences.getString(SELECTED_LANGUAGE, null)
        val selected = resolveFrameworkLanguage(
            frameworkTag,
            migrationComplete,
            legacy,
            applicationContext.resources.configuration.locales[0].toLanguageTag()
        )
        if (frameworkTag.isEmpty() && !migrationComplete && !legacy.isNullOrBlank()) {
            applicationContext.getSystemService(android.app.LocaleManager::class.java)
                ?.applicationLocales = LocaleList.forLanguageTags(toAndroidLanguageTag(selected))
        }
        if (!migrationComplete) preferences.edit { putBoolean(FRAMEWORK_LOCALE_MIGRATED, true) }
        return toAndroidLanguageTag(selected)
    }

    private fun resourceLanguageForAndroidTag(tag: String): String =
        toResourceLanguageCode(Locale.forLanguageTag(tag).language)

    fun isLanguageSelected(context: Context): Boolean {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        return preferences.getBoolean(IS_LANGUAGE_SELECTED, false)
    }

    fun setLanguageSelected(context: Context) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        preferences.edit { putBoolean(IS_LANGUAGE_SELECTED, true) }
    }

    fun setLocale(context: Context, language: String): Context {
        val normalizedLanguage = toResourceLanguageCode(Locale.forLanguageTag(language).language)
        val currentLang = getLanguage(context)
        val changed = currentLang != normalizedLanguage
        if (Build.VERSION.SDK_INT >= 33) {
            val localeManager = context.applicationContext.getSystemService(android.app.LocaleManager::class.java)
            localeManager?.applicationLocales = LocaleList.forLanguageTags(toAndroidLanguageTag(normalizedLanguage))
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext).edit {
                putBoolean(FRAMEWORK_LOCALE_MIGRATED, true)
                putBoolean(IS_LANGUAGE_SELECTED, true)
            }
        } else {
            persist(context, normalizedLanguage)
        }

        val locale = Locale.forLanguageTag(normalizedLanguage)
        Locale.setDefault(locale)
        val newContext = updateResources(context, locale)
        if (changed) com.mckimquyen.services.AppEventManager.notifyAppsUpdated()
        return newContext
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
