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
class UtilSettingsT9Test {

    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun defaultEnableT9SearchKeypadIsFalse() {
        assertFalse(settings.getEnableT9SearchKeypad())
    }

    @Test
    fun saveAndRetrieveEnableT9SearchKeypad() {
        settings.saveEnableT9SearchKeypad(true)
        assertTrue(UtilSettings(context).getEnableT9SearchKeypad())

        settings.saveEnableT9SearchKeypad(false)
        assertFalse(UtilSettings(context).getEnableT9SearchKeypad())
    }
}
