package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import org.junit.After
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
class UtilSettingsElasticOverscrollTest {

    private lateinit var context: Context
    private lateinit var settings: UtilSettings
    private var hadSetting = false
    private var originalSetting = false

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        hadSetting = prefs.contains(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL)
        originalSetting = prefs.getBoolean(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL, false)
        settings = UtilSettings(context)
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
            if (hadSetting) putBoolean(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL, originalSetting)
            else remove(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL)
        }.commit()
    }

    @Test
    fun defaultElasticOverscrollIsTrue() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL).commit()
        assertTrue(settings.getLensElasticOverscroll())
    }

    @Test
    fun saveAndRetrieveElasticOverscroll() {
        settings.saveLensElasticOverscroll(false)
        assertFalse(UtilSettings(context).getLensElasticOverscroll())
        settings.saveLensElasticOverscroll(true)
        assertTrue(UtilSettings(context).getLensElasticOverscroll())
    }
}
