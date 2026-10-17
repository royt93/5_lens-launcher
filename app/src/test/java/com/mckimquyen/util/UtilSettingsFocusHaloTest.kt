package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
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
class UtilSettingsFocusHaloTest {

    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun defaultShowFocusHaloIsTrue() {
        assertTrue(settings.getBoolean(UtilSettings.KEY_SHOW_FOCUS_HALO))
    }

    @Test
    fun saveAndRetrieveShowFocusHalo() {
        settings.save(UtilSettings.KEY_SHOW_FOCUS_HALO, false)
        assertFalse(UtilSettings(context).getBoolean(UtilSettings.KEY_SHOW_FOCUS_HALO))

        settings.save(UtilSettings.KEY_SHOW_FOCUS_HALO, true)
        assertTrue(UtilSettings(context).getBoolean(UtilSettings.KEY_SHOW_FOCUS_HALO))
    }
}
