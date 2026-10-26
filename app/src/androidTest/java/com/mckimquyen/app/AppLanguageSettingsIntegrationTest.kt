package com.mckimquyen.app

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.util.LocaleHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** I18N-001: system app-language settings and the in-app picker share locale state on API 33+. */
@RunWith(AndroidJUnit4::class)
class AppLanguageSettingsIntegrationTest {
    @get:Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var localeManager: LocaleManager
    private lateinit var originalLocales: LocaleList
    private var originalLanguage: String = "en"
    private var originalSelected: Boolean = false
    private var originalMigrationState = false

    @Before
    fun snapshotLocaleState() {
        assumeTrue("System per-app languages require Android 13+", android.os.Build.VERSION.SDK_INT >= 33)
        localeManager = context.getSystemService(LocaleManager::class.java)
        originalLocales = localeManager.applicationLocales
        originalLanguage = LocaleHelper.getLanguage(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        originalSelected = LocaleHelper.isLanguageSelected(context)
        originalMigrationState = prefs.getBoolean("Locale.Helper.Framework.Migrated", false)
    }

    @After
    fun restoreLocaleState() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        localeManager.applicationLocales = originalLocales
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("Locale.Helper.Language.Selected", originalSelected)
            .putBoolean("Locale.Helper.Framework.Migrated", originalMigrationState)
            .putString("Locale.Helper.Selected.Language", originalLanguage)
            .commit()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    /**
     * Framework locales live in system_server, so a read right after a write may still be empty
     * (LocaleList.get(0) is null then). Poll until the expected language shows up or time runs out.
     */
    private fun awaitFrameworkLanguage(expected: String): String? {
        val deadline = android.os.SystemClock.uptimeMillis() + FRAMEWORK_LOCALE_TIMEOUT_MS
        var actual: String? = null
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            actual = localeManager.applicationLocales.takeIf { !it.isEmpty }?.get(0)?.language
            if (actual == expected) return actual
            Thread.sleep(FRAMEWORK_LOCALE_POLL_MS)
        }
        return actual
    }

    @Test
    fun inAppLanguageSelectionUpdatesFrameworkApplicationLocale() {
        LocaleHelper.setLocale(context, "vi")
        assertEquals("vi", awaitFrameworkLanguage("vi"))
        assertEquals("vi", LocaleHelper.getLanguage(context))
    }

    @Test
    fun frameworkLocaleWinsAfterLegacyPreferenceMigration() {
        LocaleHelper.setLocale(context, "en")
        assertEquals("en", awaitFrameworkLanguage("en"))
        localeManager.applicationLocales = LocaleList.forLanguageTags("ja-JP")
        assertEquals("ja", awaitFrameworkLanguage("ja"))
        assertEquals("ja", LocaleHelper.getLanguage(context))
    }

    private fun awaitFrameworkEmpty(): Boolean {
        val deadline = android.os.SystemClock.uptimeMillis() + FRAMEWORK_LOCALE_TIMEOUT_MS
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (localeManager.applicationLocales.isEmpty) return true
            Thread.sleep(FRAMEWORK_LOCALE_POLL_MS)
        }
        return localeManager.applicationLocales.isEmpty
    }

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Test
    fun legacyPreferenceIsMigratedIntoFrameworkExactlyOnce() {
        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
        assertTrue(awaitFrameworkEmpty())
        prefs().edit().putString("Locale.Helper.Selected.Language", "fr")
            .remove("Locale.Helper.Framework.Migrated").commit()

        LocaleHelper.onAttach(context)

        assertEquals("fr", awaitFrameworkLanguage("fr"))
        assertTrue(prefs().getBoolean("Locale.Helper.Framework.Migrated", false))
    }

    @Test
    fun staleLegacyPreferenceIsNotRevivedAfterMigration() {
        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
        assertTrue(awaitFrameworkEmpty())
        prefs().edit().putString("Locale.Helper.Selected.Language", "de")
            .putBoolean("Locale.Helper.Framework.Migrated", true).commit()

        LocaleHelper.onAttach(context)

        assertTrue("migrated app must not re-apply the stale legacy language", localeManager.applicationLocales.isEmpty)
    }

    private companion object {
        const val FRAMEWORK_LOCALE_TIMEOUT_MS = 5_000L
        const val FRAMEWORK_LOCALE_POLL_MS = 100L
    }
}
