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
 * FEAT-005: "Keep screen on" defaults off (no behavior change for existing users) and round-trips
 * through save/read, following the same generic UtilSettings.save(name, Boolean)/getBoolean()
 * pattern as every other boolean setting (e.g. KEY_SHOW_SEARCH_BAR).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsKeepScreenOnTest {

    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test
    fun `keep screen on is off by default`() {
        assertFalse(freshSettings().getBoolean(UtilSettings.KEY_KEEP_SCREEN_ON))
        assertFalse(UtilSettings.DEFAULT_KEEP_SCREEN_ON)
    }

    @Test
    fun `enabling keep screen on persists and reads back true`() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_KEEP_SCREEN_ON, true)
        assertTrue(settings.getBoolean(UtilSettings.KEY_KEEP_SCREEN_ON))
    }

    @Test
    fun `disabling keep screen on persists and reads back false`() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_KEEP_SCREEN_ON, true)
        settings.save(UtilSettings.KEY_KEEP_SCREEN_ON, false)
        assertFalse(settings.getBoolean(UtilSettings.KEY_KEEP_SCREEN_ON))
    }

    @Test
    fun `the key is stable and distinct from other boolean settings`() {
        assertEquals("keep_screen_on", UtilSettings.KEY_KEEP_SCREEN_ON)
    }
}
