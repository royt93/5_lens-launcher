package com.mckimquyen.ui

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.TextView
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.slider.Slider
import com.mckimquyen.R
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-018 widget proof: the Icon Size slider reads and writes the *active* lens. Same approach
 * as [FrmLensPerLensWidgetTest] for the distortion slider: point `KEY_ACTIVE_LENS_ID` at a
 * non-default lens, because on the default lens the per-lens key collapses onto the shared one
 * and cannot tell a correct implementation from one that ignores the lens.
 */
@RunWith(AndroidJUnit4::class)
class FrmLensIconSizeWidgetTest {

    private val workLens = "work-lens"
    private val otherLens = "other-lens"

    // Set in @Before before every test; JUnit never calls a @Test without running setup.
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
        settings.deleteLensSettings(otherLens)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(UtilSettings.KEY_ACTIVE_LENS_ID)
            .remove(UtilSettings.KEY_ICON_SIZE)
            .apply()
    }

    private fun activate(lensId: String) {
        settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, lensId)
    }

    /** A real touch sequence: only a drag reports `fromUser = true`, the branch that persists. */
    private fun dragSliderToEnd(slider: Slider) {
        val y = (slider.height / 2).toFloat()
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            slider.dispatchTouchEvent(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, slider.width / 2f)
        send(MotionEvent.ACTION_MOVE, slider.width.toFloat())
        send(MotionEvent.ACTION_UP, slider.width.toFloat())
    }

    private fun slider(fragment: FrmLens) =
        fragment.requireView().findViewById<Slider>(R.id.sbMinIconSize)

    @Test
    fun slider_showsTheActiveLensValue_notTheSharedOne() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        settings.saveIconSize(workLens, 50f)
        activate(workLens)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { assertEquals(50f, slider(it).value, 0.001f) }
        scenario.close()
    }

    @Test
    fun aLensWithoutOverride_showsTheSharedValue() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        activate(workLens)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { assertEquals(30f, slider(it).value, 0.001f) }
        scenario.close()
    }

    @Test
    fun draggingTheSlider_writesToTheActiveLens_notTheSharedValue() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        activate(workLens)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { dragSliderToEnd(slider(it)) }

        val perLens = settings.getIconSize(workLens)
        assertTrue("dragging must persist a new size for the active lens (got $perLens)", perLens > 30f)
        assertEquals(
            "the shared value must stay untouched while a non-default lens is active",
            30f,
            settings.getFloat(UtilSettings.KEY_ICON_SIZE),
            0.001f
        )
        scenario.close()
    }

    @Test
    fun draggingTheSlider_leavesOtherLensesAlone() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        settings.saveIconSize(otherLens, 48f)
        activate(workLens)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { dragSliderToEnd(slider(it)) }

        assertEquals(48f, settings.getIconSize(otherLens), 0.001f)
        scenario.close()
    }

    @Test
    fun theLabel_followsTheActiveLensValue() {
        settings.saveIconSize(workLens, 50f)
        activate(workLens)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { fragment ->
            val label = fragment.requireView().findViewById<TextView>(R.id.tvValueMinIconSize)
            assertEquals(fragment.getString(R.string.unit_dp_format, 50), label.text.toString())
        }
        scenario.close()
    }

    @Test
    fun onResume_picksUpALensSwitchThatHappenedWhileTheTabWasAway() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        settings.saveIconSize(workLens, 50f)
        activate(LensWorkspace.DEFAULT_LENS_ID)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { assertEquals(30f, slider(it).value, 0.001f) }

        activate(workLens)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)

        scenario.onFragment {
            assertEquals("resuming must re-read the active lens", 50f, slider(it).value, 0.001f)
        }
        scenario.close()
    }

    @Test
    fun resetToDefaults_resetsTheActiveLensAndLeavesOthersAlone() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        settings.saveIconSize(workLens, 52f)
        settings.saveIconSize(otherLens, 48f)
        activate(workLens)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { it.onDefaultsReset() }

        assertEquals(settings.autoDefaultIconSize, settings.getIconSize(workLens), 0.001f)
        assertEquals("another lens must not be reset", 48f, settings.getIconSize(otherLens), 0.001f)
        assertEquals(
            "the shared value must not be rewritten",
            30f,
            settings.getFloat(UtilSettings.KEY_ICON_SIZE),
            0.001f
        )
        scenario.onFragment { assertEquals(settings.autoDefaultIconSize, slider(it).value, 0.001f) }
        scenario.close()
    }

    @Test
    fun onTheDefaultLens_theSliderStillDrivesTheSharedKey() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        activate(LensWorkspace.DEFAULT_LENS_ID)

        val scenario = launchFragmentInContainer<FrmLens>(themeResId = R.style.AppTheme)
        scenario.onFragment { dragSliderToEnd(slider(it)) }

        assertTrue(
            "default lens must write the legacy shared key",
            settings.getFloat(UtilSettings.KEY_ICON_SIZE) > 30f
        )
        scenario.close()
    }
}
