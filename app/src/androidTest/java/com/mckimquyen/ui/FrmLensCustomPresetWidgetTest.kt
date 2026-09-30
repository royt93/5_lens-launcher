package com.mckimquyen.ui

import android.content.Context
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.mckimquyen.R
import com.mckimquyen.util.LensPhysicsPreset
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-015: the Custom preset button must start disabled, become usable only after Save is
 * tapped, and then apply the exact three values that were showing on the sliders at save time -
 * not the fixed [LensPhysicsPreset] values, which is exactly what an implementation that
 * accidentally reused [LensPhysicsPreset.STANDARD] instead of the saved value would still pass
 * if these tests only checked "did something get applied".
 */
@RunWith(AndroidJUnit4::class)
class FrmLensCustomPresetWidgetTest {

    private val workLens = "work-lens-custom"

    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        settings = UtilSettings(context)
        clearState()
    }

    @After
    fun tearDown() {
        clearState()
    }

    private fun clearState() {
        settings.deleteLensSettings(workLens)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(UtilSettings.KEY_ACTIVE_LENS_ID)
            .remove(UtilSettings.KEY_DISTORTION_FACTOR)
            .remove(UtilSettings.KEY_SCALE_FACTOR)
            .remove(UtilSettings.KEY_ANIMATION_TIME)
            .remove(UtilSettings.KEY_CUSTOM_DISTORTION_FACTOR)
            .remove(UtilSettings.KEY_CUSTOM_SCALE_FACTOR)
            .remove(UtilSettings.KEY_CUSTOM_ANIMATION_TIME)
            .apply()
    }

    private fun activate(lensId: String) {
        settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, lensId)
    }

    @Test
    fun customButton_startsDisabled() {
        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            val button = fragment.requireView().findViewById<MaterialButton>(R.id.btnPresetCustom)
            assertFalse("Custom must be disabled before any save", button.isEnabled)
        }
        scenario.close()
    }

    @Test
    fun saveButton_capturesCurrentSlidersAndEnablesCustom() {
        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<MaterialButton>(R.id.btnPresetSnappy).performClick()
            fragment.requireView().findViewById<MaterialButton>(R.id.btnSaveCustomPreset).performClick()
        }

        assertTrue(settings.hasCustomPreset())
        assertEquals(
            LensPhysicsPreset.SNAPPY.distortionFactor,
            settings.getCustomDistortionFactor(com.mckimquyen.model.LensWorkspace.DEFAULT_LENS_ID),
            0.001f
        )
        assertEquals(LensPhysicsPreset.SNAPPY.scaleFactor, settings.getCustomScaleFactor(), 0.001f)
        assertEquals(LensPhysicsPreset.SNAPPY.animationTimeMs, settings.getCustomAnimationTime())

        scenario.onFragment { fragment ->
            val button = fragment.requireView().findViewById<MaterialButton>(R.id.btnPresetCustom)
            assertTrue("Custom must be enabled immediately after a save", button.isEnabled)
        }
        scenario.close()
    }

    @Test
    fun customButton_appliesTheExactSavedTriple() {
        settings.saveCustomDistortionFactor(
            com.mckimquyen.model.LensWorkspace.DEFAULT_LENS_ID,
            3.7f
        )
        settings.saveCustomScaleFactor(1.6f)
        // 280 is a valid stepSize(10)-aligned value from valueFrom(100) - matching what the real
        // Slider (android:stepSize="10.0" in frm_lens.xml) could ever actually report.
        settings.saveCustomAnimationTime(280L)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<MaterialButton>(R.id.btnPresetCustom).performClick()
        }

        assertEquals(
            3.7f,
            settings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR),
            0.001f
        )
        assertEquals(1.6f, settings.getFloat(UtilSettings.KEY_SCALE_FACTOR), 0.001f)
        assertEquals(280L, settings.getLong(UtilSettings.KEY_ANIMATION_TIME))

        scenario.onFragment { fragment ->
            val slider = fragment.requireView().findViewById<Slider>(R.id.sbDistortionFactor)
            assertEquals(3.7f, slider.value, 0.001f)
        }
        scenario.close()
    }

    @Test
    fun customDistortion_isIsolatedPerLens() {
        activate(workLens)
        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<MaterialButton>(R.id.btnPresetSnappy).performClick()
            fragment.requireView().findViewById<MaterialButton>(R.id.btnSaveCustomPreset).performClick()
        }

        assertEquals(
            "Saving Custom on the work lens must not create a base override",
            LensPhysicsPreset.SNAPPY.distortionFactor,
            settings.getCustomDistortionFactor(workLens),
            0.001f
        )
        assertEquals(
            "The default lens must still fall back cleanly (no custom saved for it)",
            settings.getDistortionFactor(com.mckimquyen.model.LensWorkspace.DEFAULT_LENS_ID),
            settings.getCustomDistortionFactor(com.mckimquyen.model.LensWorkspace.DEFAULT_LENS_ID),
            0.001f
        )
        scenario.close()
    }
}
