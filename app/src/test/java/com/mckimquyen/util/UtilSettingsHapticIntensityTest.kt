package com.mckimquyen.util

import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** FISH-017: the haptic level defaults to MEDIUM, round-trips, and survives corrupt storage. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsHapticIntensityTest {

    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    private fun rawPrefs() =
        PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication())

    @Test
    fun `never saved reads medium`() {
        assertEquals(HapticIntensity.MEDIUM, freshSettings().getHapticIntensity())
        assertEquals(HapticIntensity.DEFAULT.ordinal, UtilSettings.DEFAULT_HAPTIC_INTENSITY)
    }

    @Test
    fun `every level round trips`() {
        val settings = freshSettings()
        for (level in HapticIntensity.entries) {
            settings.saveHapticIntensity(level)
            assertEquals(level, settings.getHapticIntensity())
        }
    }

    @Test
    fun `corrupt stored ordinal reads the default`() {
        val settings = freshSettings()
        rawPrefs().edit().putInt(UtilSettings.KEY_HAPTIC_INTENSITY, 99).commit()
        assertEquals(HapticIntensity.DEFAULT, settings.getHapticIntensity())
        rawPrefs().edit().putInt(UtilSettings.KEY_HAPTIC_INTENSITY, -3).commit()
        assertEquals(HapticIntensity.DEFAULT, settings.getHapticIntensity())
    }

    @Test
    fun `wrong typed stored value reads the default`() {
        // M4: a String under the int key throws ClassCastException from prefs.getInt - must
        // fall back cleanly instead of crashing the caller.
        val settings = freshSettings()
        rawPrefs().edit().putString(UtilSettings.KEY_HAPTIC_INTENSITY, "not-an-int").commit()
        assertEquals(HapticIntensity.DEFAULT, settings.getHapticIntensity())
    }

    @Test
    fun `the key is stable and carries no lens suffix`() {
        assertEquals("haptic_intensity", UtilSettings.KEY_HAPTIC_INTENSITY)
    }

    @Test
    fun `saving does not touch the hover and launch switches`() {
        val settings = freshSettings()
        settings.saveHapticIntensity(HapticIntensity.STRONG)
        assertEquals(UtilSettings.DEFAULT_VIBRATE_APP_HOVER, settings.getBoolean(UtilSettings.KEY_VIBRATE_APP_HOVER))
        assertEquals(UtilSettings.DEFAULT_VIBRATE_APP_LAUNCH, settings.getBoolean(UtilSettings.KEY_VIBRATE_APP_LAUNCH))
    }
}
