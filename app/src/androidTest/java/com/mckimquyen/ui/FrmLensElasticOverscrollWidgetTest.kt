package com.mckimquyen.ui

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.materialswitch.MaterialSwitch
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrmLensElasticOverscrollWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()


    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val settings get() = UtilSettings(context)
    private var hadSetting = false
    private var originalSetting = true
    private var originalSliders: Map<String, Any?> = emptyMap()

    @Before
    fun setUp() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val activeLens = settings.getString(UtilSettings.KEY_ACTIVE_LENS_ID)
        val suffix = if (activeLens.isNullOrEmpty() || activeLens == com.mckimquyen.model.LensWorkspace.DEFAULT_LENS_ID) "" else "_$activeLens"
        originalSliders = listOf(
            UtilSettings.KEY_ICON_SIZE + suffix,
            UtilSettings.KEY_DISTORTION_FACTOR + suffix,
            UtilSettings.KEY_SCALE_FACTOR,
            UtilSettings.KEY_ANIMATION_TIME
        ).associateWith { prefs.all[it] }
        hadSetting = prefs.contains(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL)
        originalSetting = settings.getLensElasticOverscroll()
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
            if (hadSetting) putBoolean(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL, originalSetting)
            else remove(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL)
            originalSliders.forEach { (key, value) ->
                when (value) {
                    is Float -> putFloat(key, value)
                    is Long -> putLong(key, value)
                    is Int -> putInt(key, value)
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                    null -> remove(key)
                }
            }
        }.commit()
    }

    @Test
    fun switchShowsDefaultPersistsChangeAndResetRestoresDefault() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL).commit()
        launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val switch = fragment.requireView().findViewById<MaterialSwitch>(R.id.swLensElasticOverscroll)
                assertTrue("elastic overscroll defaults on", switch.isChecked)
                switch.isChecked = false
                assertFalse("switch persists off", settings.getLensElasticOverscroll())
                fragment.onDefaultsReset()
                assertTrue("reset-to-defaults restores elastic on", switch.isChecked)
            }
            assertTrue(settings.getLensElasticOverscroll())
        }
    }
}
