package com.mckimquyen.ui

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.mckimquyen.R
import com.mckimquyen.util.LensPhysicsPreset
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-004: tapping a preset button must apply its exact (distortion, scale, animation-time)
 * triple to the real, persisted [UtilSettings] values the sliders themselves write to - proving
 * the "instant, safe preview" acceptance criterion actually changes what `LensView` reads, not
 * just the slider's own displayed text.
 */
@RunWith(AndroidJUnit4::class)
class FrmLensPhysicsPresetsWidgetTest {

    @Test
    fun tappingGentlePreset_appliesGentleValuesToSettings() {
        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        lateinit var settings: UtilSettings
        scenario.onFragment { fragment ->
            settings = UtilSettings(fragment.requireContext())
            fragment.requireView().findViewById<MaterialButton>(R.id.btnPresetGentle).performClick()
        }

        assertEquals(LensPhysicsPreset.GENTLE.distortionFactor, settings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR))
        assertEquals(LensPhysicsPreset.GENTLE.scaleFactor, settings.getFloat(UtilSettings.KEY_SCALE_FACTOR))
        assertEquals(LensPhysicsPreset.GENTLE.animationTimeMs, settings.getLong(UtilSettings.KEY_ANIMATION_TIME))

        scenario.close()
    }

    @Test
    fun tappingSnappyPreset_appliesSnappyValuesToSettings() {
        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        lateinit var settings: UtilSettings
        scenario.onFragment { fragment ->
            settings = UtilSettings(fragment.requireContext())
            fragment.requireView().findViewById<MaterialButton>(R.id.btnPresetSnappy).performClick()
        }

        assertEquals(LensPhysicsPreset.SNAPPY.distortionFactor, settings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR))
        assertEquals(LensPhysicsPreset.SNAPPY.scaleFactor, settings.getFloat(UtilSettings.KEY_SCALE_FACTOR))
        assertEquals(LensPhysicsPreset.SNAPPY.animationTimeMs, settings.getLong(UtilSettings.KEY_ANIMATION_TIME))

        scenario.close()
    }

    @Test
    fun tappingStandardPreset_afterSnappy_restoresDefaultsInstantly() {
        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        lateinit var settings: UtilSettings
        scenario.onFragment { fragment ->
            settings = UtilSettings(fragment.requireContext())
            val view = fragment.requireView()
            view.findViewById<MaterialButton>(R.id.btnPresetSnappy).performClick()
            view.findViewById<MaterialButton>(R.id.btnPresetStandard).performClick()
        }

        assertEquals(UtilSettings.DEFAULT_DISTORTION_FACTOR, settings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR))
        assertEquals(UtilSettings.DEFAULT_SCALE_FACTOR, settings.getFloat(UtilSettings.KEY_SCALE_FACTOR))
        assertEquals(UtilSettings.DEFAULT_ANIMATION_TIME, settings.getLong(UtilSettings.KEY_ANIMATION_TIME))

        scenario.close()
    }
}
