package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.enums.BackgroundMode
import com.mckimquyen.enums.SortType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * PREF-001: proves SortType/BackgroundMode are persisted by stable, locale-independent name
 * (not enum ordinal or a translated/hardcoded display string), and that legacy values written
 * by older app versions are safely migrated - including corrupt/unknown legacy values, which
 * must default rather than crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsPrefMigrationTest {

    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    // ---- SortType ----

    @Test
    fun `sortType defaults to LABEL_ASCENDING when nothing is stored`() {
        assertEquals(SortType.LABEL_ASCENDING, settings.sortType)
    }

    @Test
    fun `sortType migrates a legacy ordinal to the stable name key`() {
        rawPrefs().edit().putInt(UtilSettings.KEY_SORT_TYPE, SortType.OPEN_COUNT_DESCENDING.ordinal).commit()

        val result = settings.sortType

        assertEquals(SortType.OPEN_COUNT_DESCENDING, result)
        // Write-through: the stable key must now hold the migrated value.
        assertEquals(
            SortType.OPEN_COUNT_DESCENDING.name,
            rawPrefs().getString(UtilSettings.KEY_SORT_TYPE_NAME, null)
        )
    }

    @Test
    fun `sortType defaults instead of crashing on an out-of-range legacy ordinal`() {
        rawPrefs().edit().putInt(UtilSettings.KEY_SORT_TYPE, 999).commit()

        assertEquals(SortType.LABEL_ASCENDING, settings.sortType)
    }

    @Test
    fun `sortType defaults instead of crashing on a corrupt stable-key value`() {
        rawPrefs().edit().putString(UtilSettings.KEY_SORT_TYPE_NAME, "NOT_A_REAL_SORT_TYPE").commit()

        assertEquals(SortType.LABEL_ASCENDING, settings.sortType)
    }

    @Test
    fun `sortType prefers the stable name key over a stale legacy ordinal`() {
        rawPrefs().edit()
            .putInt(UtilSettings.KEY_SORT_TYPE, SortType.LABEL_ASCENDING.ordinal)
            .putString(UtilSettings.KEY_SORT_TYPE_NAME, SortType.ICON_COLOR_DESCENDING.name)
            .commit()

        assertEquals(SortType.ICON_COLOR_DESCENDING, settings.sortType)
    }

    @Test
    fun `save SortType persists by name, not ordinal`() {
        settings.save(SortType.INSTALL_DATE_DESCENDING)

        assertEquals(
            SortType.INSTALL_DATE_DESCENDING.name,
            rawPrefs().getString(UtilSettings.KEY_SORT_TYPE_NAME, null)
        )
        assertEquals(SortType.INSTALL_DATE_DESCENDING, settings.sortType)
    }

    // ---- BackgroundMode ----

    @Test
    fun `backgroundMode defaults to WALLPAPER when nothing is stored`() {
        assertEquals(BackgroundMode.WALLPAPER, settings.backgroundMode)
    }

    @Test
    fun `backgroundMode migrates the legacy Color literal`() {
        rawPrefs().edit().putString(UtilSettings.KEY_BACKGROUND, "Color").commit()

        val result = settings.backgroundMode

        assertEquals(BackgroundMode.COLOR, result)
        assertEquals(BackgroundMode.COLOR.name, rawPrefs().getString(UtilSettings.KEY_BACKGROUND_MODE, null))
    }

    @Test
    fun `backgroundMode migrates the legacy Wallpaper literal`() {
        rawPrefs().edit().putString(UtilSettings.KEY_BACKGROUND, "Wallpaper").commit()

        assertEquals(BackgroundMode.WALLPAPER, settings.backgroundMode)
    }

    @Test
    fun `backgroundMode defaults instead of crashing on an unknown legacy literal`() {
        // e.g. a future localized arrays.xml override changing the persisted display text.
        rawPrefs().edit().putString(UtilSettings.KEY_BACKGROUND, "Fondo de pantalla").commit()

        assertEquals(BackgroundMode.WALLPAPER, settings.backgroundMode)
    }

    @Test
    fun `backgroundMode prefers the stable name key over a stale legacy literal`() {
        rawPrefs().edit()
            .putString(UtilSettings.KEY_BACKGROUND, "Wallpaper")
            .putString(UtilSettings.KEY_BACKGROUND_MODE, BackgroundMode.COLOR.name)
            .commit()

        assertEquals(BackgroundMode.COLOR, settings.backgroundMode)
    }

    @Test
    fun `save BackgroundMode persists by name`() {
        settings.save(BackgroundMode.COLOR)

        assertEquals(BackgroundMode.COLOR.name, rawPrefs().getString(UtilSettings.KEY_BACKGROUND_MODE, null))
        assertEquals(BackgroundMode.COLOR, settings.backgroundMode)
    }

    // ---- Every legacy/current value (DoD: "parameterized unit tests cover every
    // legacy/current/corrupt value") ----

    @Test
    fun `every SortType legacy ordinal migrates to its exact matching enum value`() {
        for (sortType in SortType.entries) {
            rawPrefs().edit().clear().putInt(UtilSettings.KEY_SORT_TYPE, sortType.ordinal).commit()
            val fresh = UtilSettings(context)

            assertEquals("ordinal ${sortType.ordinal} must migrate to $sortType", sortType, fresh.sortType)
            assertEquals(
                "ordinal ${sortType.ordinal} must write through to the stable key",
                sortType.name,
                rawPrefs().getString(UtilSettings.KEY_SORT_TYPE_NAME, null)
            )
        }
    }

    @Test
    fun `every SortType round-trips through save and read by name`() {
        for (sortType in SortType.entries) {
            settings.save(sortType)
            assertEquals("save/read must round-trip for $sortType", sortType, settings.sortType)
        }
    }

    @Test
    fun `every BackgroundMode legacy literal migrates to its exact matching enum value`() {
        val legacyLiteralByMode = mapOf(
            BackgroundMode.WALLPAPER to "Wallpaper",
            BackgroundMode.COLOR to "Color",
        )
        assertEquals(
            "test fixture must cover every BackgroundMode value",
            BackgroundMode.entries.toSet(),
            legacyLiteralByMode.keys
        )
        for ((mode, legacyLiteral) in legacyLiteralByMode) {
            rawPrefs().edit().clear().putString(UtilSettings.KEY_BACKGROUND, legacyLiteral).commit()
            val fresh = UtilSettings(context)

            assertEquals("legacy \"$legacyLiteral\" must migrate to $mode", mode, fresh.backgroundMode)
            assertEquals(
                "legacy \"$legacyLiteral\" must write through to the stable key",
                mode.name,
                rawPrefs().getString(UtilSettings.KEY_BACKGROUND_MODE, null)
            )
        }
    }

    @Test
    fun `every BackgroundMode round-trips through save and read by name`() {
        for (mode in BackgroundMode.entries) {
            settings.save(mode)
            assertEquals("save/read must round-trip for $mode", mode, settings.backgroundMode)
        }
    }

    // ---- Icon pack default sentinel ----

    @Test
    fun `icon pack default sentinel is a stable non-translated marker`() {
        // Guards against re-introducing a hardcoded English display string (e.g. "Default Icon
        // Pack") as the persisted sentinel, which desyncs from the translated dialog item text
        // once the device/app locale isn't English.
        assertEquals("__default_icon_pack__", UtilSettings.DEFAULT_ICON_PACK_LABEL_NAME)
    }

    @Test
    fun `icon pack getString falls back to the stable marker when nothing is stored`() {
        assertEquals(
            UtilSettings.DEFAULT_ICON_PACK_LABEL_NAME,
            settings.getString(UtilSettings.KEY_ICON_PACK_LABEL_NAME)
        )
    }

    @Test
    fun `icon pack getString returns a real pack name unchanged once one is selected`() {
        settings.save(UtilSettings.KEY_ICON_PACK_LABEL_NAME, "Nova Icon Pack")
        assertEquals("Nova Icon Pack", settings.getString(UtilSettings.KEY_ICON_PACK_LABEL_NAME))
    }
}
