package com.mckimquyen.ui

import android.content.Context
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.mckimquyen.R
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LensPhysicsPreset
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-008 Phase 3: the Lens settings tab writes curvature to the *active* lens, not to the
 * shared value. The existing [FrmLensPhysicsPresetsWidgetTest] only ever runs with the default
 * lens active, where the per-lens key deliberately collapses back onto the legacy global key -
 * so it cannot tell a correct implementation from one that ignores the active lens entirely.
 * These tests point `KEY_ACTIVE_LENS_ID` at another lens, which is where the two differ.
 */
@RunWith(AndroidJUnit4::class)
class FrmLensPerLensWidgetTest {

    private val workLens = "work-lens"

    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        settings = UtilSettings(context)
        clearLensState()
    }

    @After
    fun tearDown() {
        clearLensState()
    }

    private fun clearLensState() {
        settings.deleteLensSettings(workLens)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(UtilSettings.KEY_ACTIVE_LENS_ID)
            .remove(UtilSettings.KEY_DISTORTION_FACTOR)
            .apply()
    }

    private fun activate(lensId: String) {
        settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, lensId)
    }

    /**
     * Drags the slider for real: a touch sequence across the track is the only way to make the
     * Slider report `fromUser = true`, which is exactly the branch that persists the value.
     * Setting `slider.value` programmatically reports `fromUser = false` and writes nothing, so
     * a test that did that would pass against an implementation that saves nothing at all.
     */
    private fun dragSliderToEnd(slider: Slider) {
        val location = IntArray(2)
        slider.getLocationOnScreen(location)
        val y = (slider.height / 2).toFloat()
        val downTime = android.os.SystemClock.uptimeMillis()

        fun send(action: Int, x: Float) {
            val event = android.view.MotionEvent.obtain(
                downTime, android.os.SystemClock.uptimeMillis(), action, x, y, 0
            )
            slider.dispatchTouchEvent(event)
            event.recycle()
        }

        send(android.view.MotionEvent.ACTION_DOWN, slider.width / 2f)
        send(android.view.MotionEvent.ACTION_MOVE, slider.width.toFloat())
        send(android.view.MotionEvent.ACTION_UP, slider.width.toFloat())
    }

    @Test
    fun draggingTheSlider_writesToTheActiveLens_notTheSharedValue() {
        settings.save(UtilSettings.KEY_DISTORTION_FACTOR, 2.0f)
        activate(workLens)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            dragSliderToEnd(fragment.requireView().findViewById(R.id.sbDistortionFactor))
        }

        val perLens = settings.getDistortionFactor(workLens)
        org.junit.Assert.assertTrue(
            "Dragging the slider must persist a new curvature for the active lens (got $perLens)",
            perLens > 2.0f
        )
        assertEquals(
            "The shared curvature must be untouched while a non-default lens is active",
            2.0f,
            settings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR),
            0.001f
        )
        scenario.close()
    }

    @Test
    fun preset_appliesCurvatureToTheActiveLensOnly() {
        settings.save(UtilSettings.KEY_DISTORTION_FACTOR, 2.0f)
        activate(workLens)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<MaterialButton>(R.id.btnPresetSnappy).performClick()
        }

        assertEquals(
            LensPhysicsPreset.SNAPPY.distortionFactor,
            settings.getDistortionFactor(workLens),
            0.001f
        )
        assertEquals(
            "A preset must not rewrite the shared curvature of every other lens",
            2.0f,
            settings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR),
            0.001f
        )
        // Scale factor and animation time stay global by owner scope decision.
        assertEquals(
            LensPhysicsPreset.SNAPPY.scaleFactor,
            settings.getFloat(UtilSettings.KEY_SCALE_FACTOR),
            0.001f
        )
        scenario.close()
    }

    @Test
    fun slider_showsTheActiveLensValue_notTheSharedOne() {
        settings.save(UtilSettings.KEY_DISTORTION_FACTOR, 2.0f)
        settings.saveDistortionFactor(workLens, 4.0f)
        activate(workLens)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            val slider = fragment.requireView().findViewById<Slider>(R.id.sbDistortionFactor)
            assertEquals(4.0f, slider.value, 0.001f)
        }
        scenario.close()
    }

    @Test
    fun onResume_picksUpALensSwitchThatHappenedWhileTheTabWasAway() {
        settings.save(UtilSettings.KEY_DISTORTION_FACTOR, 2.0f)
        settings.saveDistortionFactor(workLens, 4.0f)
        // Starts on the default lens...
        activate(LensWorkspace.DEFAULT_LENS_ID)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            assertEquals(
                2.0f,
                fragment.requireView().findViewById<Slider>(R.id.sbDistortionFactor).value,
                0.001f
            )
        }

        // ...the user goes Home, swipes to another lens, and comes back to this tab.
        activate(workLens)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)

        scenario.onFragment { fragment ->
            assertEquals(
                "Resuming must re-read the active lens, not keep the one from onViewCreated",
                4.0f,
                fragment.requireView().findViewById<Slider>(R.id.sbDistortionFactor).value,
                0.001f
            )
        }
        scenario.close()
    }

    @Test
    fun resetToDefaults_resetsTheActiveLensAndLeavesOtherLensesAlone() {
        settings.save(UtilSettings.KEY_DISTORTION_FACTOR, 2.0f)
        settings.saveDistortionFactor(workLens, 4.5f)
        activate(workLens)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment -> fragment.onDefaultsReset() }

        assertEquals(
            UtilSettings.DEFAULT_DISTORTION_FACTOR,
            settings.getDistortionFactor(workLens),
            0.001f
        )
        assertEquals(
            "Reset applies to the lens being edited, not to everyone's shared value",
            2.0f,
            settings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR),
            0.001f
        )
        scenario.close()
    }

    @Test
    fun defaultLens_stillWritesTheLegacyGlobalKey() {
        activate(LensWorkspace.DEFAULT_LENS_ID)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            fragment.requireView().findViewById<MaterialButton>(R.id.btnPresetGentle).performClick()
        }

        // The whole backward-compatibility promise: a single-lens install behaves as before.
        assertEquals(
            LensPhysicsPreset.GENTLE.distortionFactor,
            settings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR),
            0.001f
        )
        scenario.close()
    }
}
