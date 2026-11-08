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
class UtilSettingsFocusSnapTest {
    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test fun focusSnapIsOffByDefault() {
        assertFalse(freshSettings().getBoolean(UtilSettings.KEY_FOCUS_SNAP))
        assertFalse(UtilSettings.DEFAULT_FOCUS_SNAP)
    }

    @Test fun enablingAndDisablingPersists() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_FOCUS_SNAP, true)
        assertTrue(settings.getBoolean(UtilSettings.KEY_FOCUS_SNAP))
        settings.save(UtilSettings.KEY_FOCUS_SNAP, false)
        assertFalse(settings.getBoolean(UtilSettings.KEY_FOCUS_SNAP))
    }

    @Test fun theKeyIsStable() = assertEquals("focus_snap", UtilSettings.KEY_FOCUS_SNAP)
}
