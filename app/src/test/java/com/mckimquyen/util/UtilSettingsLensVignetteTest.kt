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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsLensVignetteTest {
    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test fun `lens vignette is off by default`() {
        assertFalse(freshSettings().getBoolean(UtilSettings.KEY_LENS_VIGNETTE))
        assertFalse(UtilSettings.DEFAULT_LENS_VIGNETTE)
    }

    @Test fun `enabling and disabling persists`() {
        val s = freshSettings()
        s.save(UtilSettings.KEY_LENS_VIGNETTE, true)
        assertTrue(s.getBoolean(UtilSettings.KEY_LENS_VIGNETTE))
        s.save(UtilSettings.KEY_LENS_VIGNETTE, false)
        assertFalse(s.getBoolean(UtilSettings.KEY_LENS_VIGNETTE))
    }

    @Test fun `the key is stable`() {
        assertEquals("lens_vignette", UtilSettings.KEY_LENS_VIGNETTE)
    }
}
