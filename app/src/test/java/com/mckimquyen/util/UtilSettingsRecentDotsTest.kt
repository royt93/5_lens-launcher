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
class UtilSettingsRecentDotsTest {
    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test fun recentDotsAreOffByDefault() {
        assertFalse(freshSettings().getBoolean(UtilSettings.KEY_RECENT_DOTS))
        assertFalse(UtilSettings.DEFAULT_RECENT_DOTS)
    }

    @Test fun enablingAndDisablingPersists() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_RECENT_DOTS, true)
        assertTrue(settings.getBoolean(UtilSettings.KEY_RECENT_DOTS))
        settings.save(UtilSettings.KEY_RECENT_DOTS, false)
        assertFalse(settings.getBoolean(UtilSettings.KEY_RECENT_DOTS))
    }

    @Test fun theKeyIsStable() = assertEquals("recent_dots", UtilSettings.KEY_RECENT_DOTS)
}
