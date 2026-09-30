package com.mckimquyen.util

import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * FEAT-009: the SearchBar's recent-apps-panel icon can be hidden independently of the panel
 * itself (the lens-menu entry point always stays available). Defaults to shown, same generic
 * UtilSettings.save(name, Boolean)/getBoolean() pattern as every other boolean setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsRecentAppsPanelToggleTest {

    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test
    fun `recent apps panel icon is shown by default`() {
        assertTrue(freshSettings().getBoolean(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED))
        assertTrue(UtilSettings.DEFAULT_RECENT_APPS_QUICK_PANEL_ENABLED)
    }

    @Test
    fun `disabling the icon persists and reads back false`() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, false)
        assertFalse(settings.getBoolean(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED))
    }

    @Test
    fun `re-enabling the icon persists and reads back true`() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, false)
        settings.save(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED, true)
        assertTrue(settings.getBoolean(UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED))
    }

    @Test
    fun `the key is stable and distinct from other boolean settings`() {
        assertEquals("recent_apps_quick_panel_enabled", UtilSettings.KEY_RECENT_APPS_QUICK_PANEL_ENABLED)
    }
}
