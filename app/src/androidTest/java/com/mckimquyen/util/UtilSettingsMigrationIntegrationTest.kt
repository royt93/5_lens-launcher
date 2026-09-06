package com.mckimquyen.util

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.enums.BackgroundMode
import com.mckimquyen.enums.SortType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PREF-001 integration proof: a representative pre-upgrade SharedPreferences fixture (legacy
 * SortType ordinal + legacy background display-string) is migrated correctly on the real Android
 * SharedPreferences boundary, and the migrated values survive a simulated process restart
 * (a fresh UtilSettings instance backed by the same on-disk file).
 */
@RunWith(AndroidJUnit4::class)
class UtilSettingsMigrationIntegrationTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun clearPrefs() {
        rawPrefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        rawPrefs().edit().clear().commit()
    }

    @Test
    fun legacyFixture_migratesAndSurvivesSimulatedProcessRestart() {
        // A representative fixture of what a real pre-PREF-001 install had on disk.
        rawPrefs().edit()
            .putInt(UtilSettings.KEY_SORT_TYPE, SortType.ICON_COLOR_ASCENDING.ordinal)
            .putString(UtilSettings.KEY_BACKGROUND, "Color")
            .commit()

        val firstInstance = UtilSettings(context)
        assertEquals(SortType.ICON_COLOR_ASCENDING, firstInstance.sortType)
        assertEquals(BackgroundMode.COLOR, firstInstance.backgroundMode)

        // Simulate process death/restart: a brand-new UtilSettings backed by the same real file.
        val afterRestart = UtilSettings(context)
        assertEquals(
            "migrated sort type must survive process restart",
            SortType.ICON_COLOR_ASCENDING,
            afterRestart.sortType
        )
        assertEquals(
            "migrated background mode must survive process restart",
            BackgroundMode.COLOR,
            afterRestart.backgroundMode
        )
        // The migration must have written the stable keys on real device storage, not just
        // returned the right in-memory value.
        assertEquals(SortType.ICON_COLOR_ASCENDING.name, rawPrefs().getString(UtilSettings.KEY_SORT_TYPE_NAME, null))
        assertEquals(BackgroundMode.COLOR.name, rawPrefs().getString(UtilSettings.KEY_BACKGROUND_MODE, null))
    }

    @Test
    fun corruptLegacyFixture_defaultsInsteadOfCrashing() {
        rawPrefs().edit()
            .putInt(UtilSettings.KEY_SORT_TYPE, Int.MAX_VALUE)
            .putString(UtilSettings.KEY_BACKGROUND, "corrupted-value")
            .commit()

        val settings = UtilSettings(context)
        assertEquals(UtilSettings.DEFAULT_SORT_TYPE_ENUM, settings.sortType)
        assertEquals(UtilSettings.DEFAULT_BACKGROUND_MODE, settings.backgroundMode)
    }

    @Test
    fun everySortTypeOrdinal_migratesCorrectly_onRealDeviceStorage() {
        for (sortType in SortType.entries) {
            rawPrefs().edit().clear().putInt(UtilSettings.KEY_SORT_TYPE, sortType.ordinal).commit()
            val settings = UtilSettings(context)
            assertEquals("ordinal ${sortType.ordinal} on real device storage", sortType, settings.sortType)
        }
    }

    @Test
    fun everyBackgroundModeLiteral_migratesCorrectly_onRealDeviceStorage() {
        val legacyLiteralByMode = mapOf(
            BackgroundMode.WALLPAPER to "Wallpaper",
            BackgroundMode.COLOR to "Color",
        )
        for ((mode, legacyLiteral) in legacyLiteralByMode) {
            rawPrefs().edit().clear().putString(UtilSettings.KEY_BACKGROUND, legacyLiteral).commit()
            val settings = UtilSettings(context)
            assertEquals("legacy \"$legacyLiteral\" on real device storage", mode, settings.backgroundMode)
        }
    }

    @Test
    fun iconPackDefaultMarker_survivesSimulatedProcessRestart_onRealDeviceStorage() {
        // No legacy migration exists for this key (it never had an ordinal), but the fix - a
        // stable sentinel instead of a hardcoded English literal compared against the dialog's
        // translated text - must hold on real on-device SharedPreferences, not just in-memory.
        assertEquals(
            UtilSettings.DEFAULT_ICON_PACK_LABEL_NAME,
            UtilSettings(context).getString(UtilSettings.KEY_ICON_PACK_LABEL_NAME)
        )

        UtilSettings(context).save(UtilSettings.KEY_ICON_PACK_LABEL_NAME, UtilSettings.DEFAULT_ICON_PACK_LABEL_NAME)
        val afterRestart = UtilSettings(context)
        assertEquals(
            UtilSettings.DEFAULT_ICON_PACK_LABEL_NAME,
            afterRestart.getString(UtilSettings.KEY_ICON_PACK_LABEL_NAME)
        )
    }
}
