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
class UtilSettingsLensInertiaTest {
    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test fun lensInertiaIsOffByDefault() {
        assertFalse(freshSettings().getBoolean(UtilSettings.KEY_LENS_INERTIA))
        assertFalse(UtilSettings.DEFAULT_LENS_INERTIA)
    }

    @Test fun enablingAndDisablingPersists() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_LENS_INERTIA, true)
        assertTrue(settings.getBoolean(UtilSettings.KEY_LENS_INERTIA))
        settings.save(UtilSettings.KEY_LENS_INERTIA, false)
        assertFalse(settings.getBoolean(UtilSettings.KEY_LENS_INERTIA))
    }

    @Test fun theKeyIsStable() = assertEquals("lens_inertia", UtilSettings.KEY_LENS_INERTIA)
}
