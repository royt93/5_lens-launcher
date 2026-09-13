package com.mckimquyen.util

import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-002 integration proof: icon-size defaults are initialized from the real device
 * configuration at the Android SharedPreferences boundary, while explicit values still win.
 */
@RunWith(AndroidJUnit4::class)
class UtilSettingsIconSizeIntegrationTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun clearPrefs() {
        rawPrefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        rawPrefs().edit().clear().commit()
    }

    @Test
    fun firstRun_materializesDeviceSpecificDefault_onRealDeviceStorage() {
        val expected = UtilSettings.calculateAutoDefaultIconSize(
            context.resources.configuration.smallestScreenWidthDp
        )

        val settings = UtilSettings(context)

        assertEquals(expected, settings.getFloat(UtilSettings.KEY_ICON_SIZE), 0.001f)
        assertEquals(expected, rawPrefs().getFloat(UtilSettings.KEY_ICON_SIZE, Float.NaN), 0.001f)
    }

    @Test
    fun explicitUserValue_survivesInitializationAndRestart_onRealDeviceStorage() {
        rawPrefs().edit().putFloat(UtilSettings.KEY_ICON_SIZE, 31f).commit()

        assertEquals(31f, UtilSettings(context).getFloat(UtilSettings.KEY_ICON_SIZE), 0.001f)
        assertEquals(31f, UtilSettings(context).getFloat(UtilSettings.KEY_ICON_SIZE), 0.001f)
        assertEquals(31f, rawPrefs().getFloat(UtilSettings.KEY_ICON_SIZE, Float.NaN), 0.001f)
    }

    @Test
    fun outOfRangeSavedValue_isClamped_onRealDeviceStorage() {
        val max = UtilSettings.MAX_ICON_SIZE + UtilSettings.MIN_ICON_SIZE
        rawPrefs().edit().putFloat(UtilSettings.KEY_ICON_SIZE, max + 100f).commit()

        assertEquals(max, UtilSettings(context).getFloat(UtilSettings.KEY_ICON_SIZE), 0.001f)
        assertEquals(max, rawPrefs().getFloat(UtilSettings.KEY_ICON_SIZE, Float.NaN), 0.001f)
    }
}
