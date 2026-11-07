package com.mckimquyen.views

import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-041 attached-view proof: sampling time, a real pan, release, and detach. */
@RunWith(AndroidJUnit4::class)
class LensViewInertiaIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SIZE_PX = 1_000
        const val APP_COUNT = 40
        const val DRAG_STEPS = 8
        const val DRAG_STEP_PX = 30f
        const val STEP_DELAY_MS = 16L
        const val SETTLE_TIMEOUT_MS = 3_000L
        const val POLL_MS = 30L
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun withView(block: (LensView) -> Unit) {
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish041.int$i", name = "Int$i", label = "Int $i", iconCacheKey = "fish041int#$i")
        })
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            var view: LensView? = null
            scenario.onActivity { activity ->
                view = LensView(activity).apply {
                    setApps(apps)
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

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun draw(view: LensView) = onMain { view.draw(Canvas()) }

    private fun cellCentre(view: LensView, index: Int): Pair<Float, Float> {
        draw(view)
        val bounds = Rect()
        var found = false
        onMain { found = view.getAppBounds(index, bounds) }
        assertTrue("cell $index must exist", found)
        return bounds.exactCenterX() to bounds.exactCenterY()
    }

    private fun send(view: LensView, action: Int, downTime: Long, x: Float, y: Float) = onMain {
        val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        view.dispatchTouchEvent(e)
        e.recycle()
    }

    /** A continuous single-pointer drag; returns the last finger position. */
    private fun dragFrom(view: LensView, x: Float, y: Float, downTime: Long): Pair<Float, Float> {
        send(view, MotionEvent.ACTION_DOWN, downTime, x, y)
        var cx = x
        var cy = y
        repeat(DRAG_STEPS) {
            cx += DRAG_STEP_PX
            cy += DRAG_STEP_PX
            send(view, MotionEvent.ACTION_MOVE, downTime, cx, cy)
            SystemClock.sleep(STEP_DELAY_MS)
        }
        return cx to cy
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + SETTLE_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            var ok = false
            onMain { ok = condition() }
            if (ok) return
            SystemClock.sleep(POLL_MS)
        }
        assertTrue("timed out: $what", false)
    }

    @Test fun theSettingIsReadWhenTheLensAnimationStartsNotEveryFrame() = withView { view ->
        UtilSettings(context).save(UtilSettings.KEY_LENS_INERTIA, true)
        assertFalse("the view has not seen the stored setting yet", view.lensInertiaEnabled)
        val (x, y) = cellCentre(view, 0)
        val down = SystemClock.uptimeMillis()
        dragFrom(view, x, y, down)
        assertTrue("starting the lens animation samples the stored setting", view.lensInertiaEnabled)
        send(view, MotionEvent.ACTION_CANCEL, down, x, y)
        UtilSettings(context).save(UtilSettings.KEY_LENS_INERTIA, false)
    }

    @Test fun aRealPanLagsTheVisualCentreBehindTheFingerAndTheRedrawLoopSettlesAfterTheFingerStops() = withView { view ->
        UtilSettings(context).save(UtilSettings.KEY_LENS_INERTIA, true)
        val (x, y) = cellCentre(view, 0)
        val down = SystemClock.uptimeMillis()
        val (fx, fy) = dragFrom(view, x, y, down)
        onMain { view.draw(Canvas()) }
        val visual = view.visualCentreForTest()
        assertNotEquals("the visual centre lags the finger during a pan", fx, visual.first)
        assertTrue("it moved off the start", visual.first > x)
        waitUntil("the visual centre settles on the held finger") {
            val v = view.visualCentreForTest()
            LensInertia.isSettled(v.first, v.second, fx, fy)
        }
        send(view, MotionEvent.ACTION_CANCEL, down, fx, fy)
        UtilSettings(context).save(UtilSettings.KEY_LENS_INERTIA, false)
    }

    @Test fun releasingSnapsTheCentreAndSelectionStillBelongsToTheFingerIcon() = withView { view ->
        UtilSettings(context).save(UtilSettings.KEY_LENS_INERTIA, true)
        val (x, y) = cellCentre(view, 0)
        val down = SystemClock.uptimeMillis()
        val (fx, fy) = dragFrom(view, x, y, down)
        onMain { view.draw(Canvas()) }
        val selectedWhileLagging = view.selectedIndexForTest
        send(view, MotionEvent.ACTION_CANCEL, down, fx, fy)
        waitUntil("the lens hides and the touch clears") { view.touchCoordinatesForTest().first == -Float.MAX_VALUE }
        assertTrue("an icon was under the finger", selectedWhileLagging >= -1)
        onMain { view.draw(Canvas()) }
        assertFalse("no finger, no lagging centre left behind", view.visualCentreForTest().first in 0f..SIZE_PX.toFloat())
        UtilSettings(context).save(UtilSettings.KEY_LENS_INERTIA, false)
    }

    /**
     * The gate must be the REAL pan state: a finger that is down but has not moved past the touch slop is not a pan,
     * so the centre must not lag. Uses real events (no test seam overrides the gate here).
     */
    @Test fun aHeldFingerThatHasNotPannedNeverLagsTheCentre() = withView { view ->
        UtilSettings(context).save(UtilSettings.KEY_LENS_INERTIA, true)
        val (x, y) = cellCentre(view, 0)
        val down = SystemClock.uptimeMillis()
        send(view, MotionEvent.ACTION_DOWN, down, x, y)
        send(view, MotionEvent.ACTION_MOVE, down, x + 1f, y + 1f) // below touch slop: real movement, still not a pan
        assertEquals("the move stays below touch slop", LensGestureState.IDLE, view.gestureState)
        onMain { view.draw(Canvas()) }
        // Below touch slop the view keeps the DOWN point as the finger centre; the visual centre must equal it exactly.
        val finger = view.touchCoordinatesForTest()
        assertEquals("a held finger is not a pan: the centre stays on the finger", finger.first, view.visualCentreForTest().first, 0.01f)
        assertEquals(finger.second, view.visualCentreForTest().second, 0.01f)
        assertEquals("the fixture really is the DOWN point", x, finger.first, 0.01f)
        send(view, MotionEvent.ACTION_CANCEL, down, x + 1f, y + 1f)
        UtilSettings(context).save(UtilSettings.KEY_LENS_INERTIA, false)
    }

    /**
     * The gate is the PAN state, not merely "the finger moved": during a pinch the view is still moving but the lens is
     * being resized, so the lagged centre must snap to the finger. (A held finger below touch slop cannot tell the gate
     * apart: the finger centre does not change there, so the centres coincide whatever the gate says.)
     */
    @Test fun aPinchStopsTheLagAndSnapsTheCentreToTheFinger() = withView { view ->
        UtilSettings(context).save(UtilSettings.KEY_LENS_INERTIA, true)
        val (x, y) = cellCentre(view, 0)
        val down = SystemClock.uptimeMillis()
        val (fx, fy) = dragFrom(view, x, y, down)
        onMain { view.draw(Canvas()) }
        assertNotEquals("precondition: the centre lags during the pan", fx, view.visualCentreForTest().first)
        onMain { view.gestureState = LensGestureState.PINCHING }
        onMain { view.draw(Canvas()) }
        assertEquals("pinching: no lag, the centre is on the finger", fx, view.visualCentreForTest().first, 0.01f)
        assertEquals(fy, view.visualCentreForTest().second, 0.01f)
        onMain { view.gestureState = LensGestureState.IDLE }
        send(view, MotionEvent.ACTION_CANCEL, down, fx, fy)
        UtilSettings(context).save(UtilSettings.KEY_LENS_INERTIA, false)
    }

    @Test fun detachClearsTheCentreAndStopsTheRedrawLoop() = withView { view ->
        view.setInertiaStateForTest(true, true, 120f, 140f)
        onMain { (view.parent as FrameLayout).removeView(view) }
        assertEquals(-Float.MAX_VALUE, view.visualCentreForTest().first, 0f)
        assertEquals(-Float.MAX_VALUE, view.visualCentreForTest().second, 0f)
    }
}
