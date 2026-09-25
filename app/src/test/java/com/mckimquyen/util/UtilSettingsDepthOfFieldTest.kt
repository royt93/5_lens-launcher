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
 * FISH-007: depth-of-field blur setting defaults to OFF (owner requirement), persists through
 * save/read roundtrips, and has a dedicated key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsDepthOfFieldTest {

    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test
    fun `depth of field is off by default`() {
        assertFalse(freshSettings().getBoolean(UtilSettings.KEY_DEPTH_OF_FIELD))
        assertFalse(UtilSettings.DEFAULT_DEPTH_OF_FIELD)
    }

    @Test
    fun `enabling depth of field persists and reads back true`() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_DEPTH_OF_FIELD, true)
        assertTrue(settings.getBoolean(UtilSettings.KEY_DEPTH_OF_FIELD))
    }

    @Test
    fun `disabling depth of field persists and reads back false`() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_DEPTH_OF_FIELD, true)
        settings.save(UtilSettings.KEY_DEPTH_OF_FIELD, false)
        assertFalse(settings.getBoolean(UtilSettings.KEY_DEPTH_OF_FIELD))
    }

    @Test
    fun `the key is stable and distinct from other boolean settings`() {
        assertEquals("depth_of_field_blur", UtilSettings.KEY_DEPTH_OF_FIELD)
    }
}
