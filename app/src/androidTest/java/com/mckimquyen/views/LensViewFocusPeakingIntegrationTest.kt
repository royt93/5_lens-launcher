package com.mckimquyen.views

import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActHome
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensViewFocusPeakingIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SIZE_PX = 1_000
        const val APP_COUNT = 25
        const val OPAQUE = 255
        const val DRAG_STEPS = 12
        const val STEP_DELAY_MS = 16L
        const val SETTLE_TIMEOUT_MS = 3_000L
        const val POLL_MS = 50L
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun withView(block: (LensView) -> Unit) {
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            var view: LensView? = null
            scenario.onActivity { activity ->
                view = LensView(activity).apply {
                    setApps(ArrayList((0 until APP_COUNT).map { i ->
                        App(id = i, packageName = "com.fish039.int$i", name = "Int$i", label = "Int $i")
                    }))
                    layoutParams = FrameLayout.LayoutParams(SIZE_PX, SIZE_PX)
                    activity.setContentView(this)
                    measure(
                        View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY)
                    )
                    layout(0, 0, SIZE_PX, SIZE_PX)
                    draw(Canvas())
                }
            }
            instrumentation.waitForIdleSync()
            block(requireNotNull(view) { "assigned in onActivity above" })
        }
    }

    private fun draw(view: LensView) = instrumentation.runOnMainSync { view.draw(Canvas()) }
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    /** Centre of a real cell (never a gap); withView already drew once, so the grid cache exists. */
    private fun cellCentre(view: LensView, index: Int = APP_COUNT / 2): Pair<Float, Float> {
        val bounds = Rect()
        var found = false
        onMain { found = view.getAppBounds(index, bounds) }
        assertTrue("cell $index must exist", found)
        return bounds.exactCenterX() to bounds.exactCenterY()
    }

    /** Pins (or, when already pinned, releases) focus on the middle icon: pinning is a toggle. */
    private fun toggleFocus(view: LensView) {
        val (x, y) = cellCentre(view)
        onMain { assertTrue(view.toggleFocusLockAtForTest(x, y)) }
    }

    /**
     * Pinning re-samples the stored setting (a pin has no lens animation), so a test that pins must enable the setting
     * in the store as well as on the field; DeviceStateGuard restores the stored value afterwards.
     */
    private fun enablePeaking(view: LensView) {
        UtilSettings(context).save(UtilSettings.KEY_FOCUS_PEAKING, true)
        onMain { view.focusPeakingEnabled = true }
    }

    @Test fun aPinnedFocusWithoutATouchOutlinesTheClusterAtFullOpacityThenReleasing() = withView { view ->
        enablePeaking(view)
        toggleFocus(view)
        draw(view)
        assertTrue("pinned cluster is outlined", view.focusPeakingRingsForTest >= 1)
        assertTrue("but not the whole grid", view.focusPeakingRingsForTest < APP_COUNT)
        assertEquals(OPAQUE, view.focusPeakingAlphaForTest)
        toggleFocus(view)   // second toggle releases
        draw(view)
        assertEquals("released: no ring", 0, view.focusPeakingRingsForTest)
    }

    @Test fun aPinnedFocusWithTheSettingOffDrawsNothing() = withView { view ->
        onMain { view.focusPeakingEnabled = false }
        toggleFocus(view)
        draw(view)
        assertEquals(0, view.focusPeakingRingsForTest)
    }

    @Test fun theSettingIsSampledWhenFocusIsPinnedSoNoLensAnimationIsNeeded() = withView { view ->
        UtilSettings(context).save(UtilSettings.KEY_FOCUS_PEAKING, true)
        assertFalse("the view has not seen the setting yet", view.focusPeakingEnabled)
        toggleFocus(view)
        assertTrue("pinning samples the stored setting", view.focusPeakingEnabled)
        draw(view)
        assertTrue(view.focusPeakingRingsForTest >= 1)
    }

    @Test fun tiltNeverOutlinesAnything() = withView { view ->
        enablePeaking(view)
        onMain { view.setTiltOffset(120f, 80f) }
        draw(view)
        assertEquals("tilt has no real focus centre", 0, view.focusPeakingRingsForTest)
    }

    @Test fun aLiveTouchWinsOverThePinAndTheRingFollowsTheFinger() = withView { view ->
        enablePeaking(view)
        toggleFocus(view)
        draw(view)
        val pinned = view.focusPeakingRingedForTest.copyOf()
        assertTrue("pinned cluster is outlined", pinned.any { it })

        // Put the lens in a live-touch state on the first (corner) cell. The lens animation only advances when the
        // window draws, so the test sets the multiplier directly, as the FISH-034 widget tests do.
        val (tx, ty) = cellCentre(view, 0)
        onMain { view.setLensStateForTest(tx, ty, 1f, reduceMotion = false) }
        draw(view)
        val touching = view.focusPeakingRingedForTest.copyOf()
        assertTrue("a ring is drawn while touching", touching.any { it })
        assertTrue("the corner cell is ringed under the finger", touching[0])
        assertFalse("the ringed icons moved with the finger, away from the pinned cluster", touching.contentEquals(pinned))
    }

    @Test fun detachAndPauseClearThePinSoTheRingGoesAway() = withView { view ->
        enablePeaking(view)
        toggleFocus(view)
        draw(view)
        assertTrue(view.focusPeakingRingsForTest >= 1)
        onMain { view.clearFocusLockForPause() }
        draw(view)
        assertEquals(0, view.focusPeakingRingsForTest)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test fun onTheRealHomeWithTheHardwareBlurPathTheSharpClusterKeepsItsRings() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        RAppsSingleton.instance.apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish039.home$i", name = "Home$i", label = "Home $i")
        })
        UtilSettings(context).save(UtilSettings.KEY_DEPTH_OF_FIELD, true)
        UtilSettings(context).save(UtilSettings.KEY_FOCUS_PEAKING, true)
        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                var found: LensView? = null
                scenario.onActivity { found = it.findViewById(R.id.lensViews) }
                val view = requireNotNull(found) { "assigned in onActivity above" }
                instrumentation.waitForIdleSync()
                var w = 0f
                var h = 0f
                onMain { w = view.width.toFloat(); h = view.height.toFloat() }
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, x: Float, y: Float) = onMain {
                    val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                    view.dispatchTouchEvent(e)
                    e.recycle()
                }
                send(MotionEvent.ACTION_DOWN, w / 4, h / 4)
                for (i in 1..DRAG_STEPS) {
                    send(MotionEvent.ACTION_MOVE, w / 4 + w / 2 * i / DRAG_STEPS, h / 4 + h / 2 * i / DRAG_STEPS)
                    SystemClock.sleep(STEP_DELAY_MS)
                }
                val deadline = SystemClock.uptimeMillis() + SETTLE_TIMEOUT_MS
                var ok = false
                while (SystemClock.uptimeMillis() < deadline && !ok) {
                    onMain { ok = view.dofLastMode == DepthOfField.Mode.BLUR && view.focusPeakingRingsForTest >= 1 }
                    if (!ok) SystemClock.sleep(POLL_MS)
                }
                assertTrue("BLUR path active and the band-0 cluster is still outlined", ok)
                send(MotionEvent.ACTION_CANCEL, w * 3 / 4, h * 3 / 4)
            }
        } finally {
            prefs.edit().clear().commit()
            RAppsSingleton.instance.clearAllData()
        }
    }
}
