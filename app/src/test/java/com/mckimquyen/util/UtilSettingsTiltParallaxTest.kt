package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsTiltParallaxTest {
    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    private fun raw() = PreferenceManager.getDefaultSharedPreferences(context)

    @Test fun isOffByDefaultAndTheDefaultConstantSaysSo() {
        assertFalse(UtilSettings.DEFAULT_TILT_PARALLAX)
        assertFalse(settings.getTiltParallax())
        assertFalse("reading the default must not write it", raw().contains(UtilSettings.KEY_TILT_PARALLAX))
    }

    @Test fun savesAndReadsBackBothValues() {
        settings.saveTiltParallax(true)
        assertTrue(settings.getTiltParallax())
        assertTrue(raw().getBoolean(UtilSettings.KEY_TILT_PARALLAX, false))
        settings.saveTiltParallax(false)
        assertFalse(settings.getTiltParallax())
    }

    @Test fun isGlobalSoItNeverTouchesPerLensKeys() {
        settings.saveDistortionFactor("work", 4f)
        val before = raw().all.filterKeys { it != UtilSettings.KEY_TILT_PARALLAX }
        settings.saveTiltParallax(true)
        assertEquals(before, raw().all.filterKeys { it != UtilSettings.KEY_TILT_PARALLAX })
        assertEquals("tilt_parallax", UtilSettings.KEY_TILT_PARALLAX)
    }

    @Test fun getBooleanDispatchesTheKeyWithItsOwnDefault() {
        assertFalse(settings.getBoolean(UtilSettings.KEY_TILT_PARALLAX))
        raw().edit().putBoolean(UtilSettings.KEY_TILT_PARALLAX, true).commit()
        assertTrue(settings.getBoolean(UtilSettings.KEY_TILT_PARALLAX))
    }
}
