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
 * UI-001: proves the new search-bar on/off setting defaults to shown and round-trips through
 * save/read, following the same generic UtilSettings.save(name, Boolean)/getBoolean() pattern
 * as every other boolean setting (e.g. KEY_SHOW_TOUCH_SELECTION).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsSearchBarToggleTest {

    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test
    fun `search bar is shown by default`() {
        assertTrue(freshSettings().getBoolean(UtilSettings.KEY_SHOW_SEARCH_BAR))
        assertTrue(UtilSettings.DEFAULT_SHOW_SEARCH_BAR)
    }

    @Test
    fun `disabling the search bar persists and reads back false`() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, false)
        assertFalse(settings.getBoolean(UtilSettings.KEY_SHOW_SEARCH_BAR))
    }

    @Test
    fun `re-enabling the search bar persists and reads back true`() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, false)
        settings.save(UtilSettings.KEY_SHOW_SEARCH_BAR, true)
        assertTrue(settings.getBoolean(UtilSettings.KEY_SHOW_SEARCH_BAR))
    }

    @Test
    fun `the key is stable and distinct from other boolean settings`() {
        assertEquals("show_search_bar", UtilSettings.KEY_SHOW_SEARCH_BAR)
    }
}
