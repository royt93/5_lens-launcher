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
class UtilSettingsFocusMagnifyTest {
    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test fun focusMagnifyIsOffByDefault() {
        assertFalse(freshSettings().getBoolean(UtilSettings.KEY_FOCUS_MAGNIFY))
        assertFalse(UtilSettings.DEFAULT_FOCUS_MAGNIFY)
    }

    @Test fun enablingAndDisablingPersists() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_FOCUS_MAGNIFY, true)
        assertTrue(settings.getBoolean(UtilSettings.KEY_FOCUS_MAGNIFY))
        settings.save(UtilSettings.KEY_FOCUS_MAGNIFY, false)
        assertFalse(settings.getBoolean(UtilSettings.KEY_FOCUS_MAGNIFY))
    }

    @Test fun theKeyIsStable() = assertEquals("focus_magnify", UtilSettings.KEY_FOCUS_MAGNIFY)
}
