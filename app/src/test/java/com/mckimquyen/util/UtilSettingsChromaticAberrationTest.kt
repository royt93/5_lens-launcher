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
class UtilSettingsChromaticAberrationTest {
    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test fun chromaticAberrationIsOffByDefault() {
        assertFalse(freshSettings().getBoolean(UtilSettings.KEY_CHROMATIC_ABERRATION))
        assertFalse(UtilSettings.DEFAULT_CHROMATIC_ABERRATION)
    }

    @Test fun enablingAndDisablingPersists() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_CHROMATIC_ABERRATION, true)
        assertTrue(settings.getBoolean(UtilSettings.KEY_CHROMATIC_ABERRATION))
        settings.save(UtilSettings.KEY_CHROMATIC_ABERRATION, false)
        assertFalse(settings.getBoolean(UtilSettings.KEY_CHROMATIC_ABERRATION))
    }

    @Test fun theKeyIsStable() = assertEquals("chromatic_aberration", UtilSettings.KEY_CHROMATIC_ABERRATION)
}
