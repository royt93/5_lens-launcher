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
class UtilSettingsFocusPeakingTest {
    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test fun focusPeakingIsOffByDefault() {
        assertFalse(freshSettings().getBoolean(UtilSettings.KEY_FOCUS_PEAKING))
        assertFalse(UtilSettings.DEFAULT_FOCUS_PEAKING)
    }

    @Test fun enablingAndDisablingPersists() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_FOCUS_PEAKING, true)
        assertTrue(settings.getBoolean(UtilSettings.KEY_FOCUS_PEAKING))
        settings.save(UtilSettings.KEY_FOCUS_PEAKING, false)
        assertFalse(settings.getBoolean(UtilSettings.KEY_FOCUS_PEAKING))
    }

    @Test fun theKeyIsStable() = assertEquals("focus_peaking", UtilSettings.KEY_FOCUS_PEAKING)
}
