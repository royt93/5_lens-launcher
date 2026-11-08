package com.mckimquyen.views

import android.graphics.Canvas
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.hypot

/** Reachability: real DOWN/MOVE/UP events, no speed or pan hook. Slow moves snap, fast moves and no-switch do not. */
@RunWith(AndroidJUnit4::class)
class LensViewFocusSnapReachabilityTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val VIEW_SIZE_PX = 1_000
        const val APP_COUNT = 120
        const val STEPS = 40
        const val FRAME_DELAY_MS = 16L
        const val SLOW_STEP_DP = 0.5f   // ~45 dp/s diagonal at 16 ms
        const val ADB_SWIPE_STEP_DP = 1.0f  // ~88 dp/s diagonal at 16 ms, the speed of the frame-budget swipes
        const val FAST_STEP_DP = 12f    // ~1000 dp/s diagonal at 16 ms
        const val PAN_START_DP = 40f
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val density = context.resources.displayMetrics.density

    @After fun tearDown() { UtilSettings(context).save(UtilSettings.KEY_FOCUS_SNAP, false) }

    /** Largest finger-to-optical-centre gap (px) seen over a real sweep, plus the selected index at the end. */
    private class Result(val maxGapPx: Float, val selectedAtEnd: Int, val fingerAtEnd: Pair<Float, Float>)

    private fun sweep(switchOn: Boolean, stepDp: Float): Result {
        UtilSettings(context).save(UtilSettings.KEY_FOCUS_SNAP, switchOn)
        var maxGap = 0f
        var selected = -1
        var finger = Pair(0f, 0f)
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            var view: LensView? = null
            scenario.onActivity { activity ->
                view = LensView(activity).apply {
                    setApps(ArrayList((0 until APP_COUNT).map { App(id = it, packageName = "com.fish044.r$it", name = "R$it", label = "R $it") }))
                    layoutParams = FrameLayout.LayoutParams(VIEW_SIZE_PX, VIEW_SIZE_PX)
                    activity.setContentView(this)
                    measure(View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY))
                    layout(0, 0, VIEW_SIZE_PX, VIEW_SIZE_PX)
                    draw(Canvas())
                }
            }
            val target = requireNotNull(view)
            val downTime = SystemClock.uptimeMillis()
            fun send(action: Int, x: Float, y: Float) = instrumentation.runOnMainSync {
                val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                target.dispatchTouchEvent(e); e.recycle()
            }
            var x = 200f
            var y = 700f
            send(MotionEvent.ACTION_DOWN, x, y)
            // Diagonal on purpose: a purely horizontal drag reads as a page swipe and never becomes a lens pan.
            x += PAN_START_DP * density; y -= PAN_START_DP * density
            send(MotionEvent.ACTION_MOVE, x, y)
            repeat(STEPS) {
                x += stepDp * density
                y -= stepDp * density
                send(MotionEvent.ACTION_MOVE, x, y)
                instrumentation.runOnMainSync {
                    target.draw(Canvas())
                    val t = target.touchCoordinatesForTest(); val o = target.opticalCentreForTest()
                    maxGap = maxOf(maxGap, hypot(o.first - t.first, o.second - t.second))
                    selected = target.selectedIndexForTest
                    finger = t
                }
                SystemClock.sleep(FRAME_DELAY_MS)
            }
            send(MotionEvent.ACTION_UP, x, y)
        }
        return Result(maxGap, selected, finger)
    }

    @Test fun aSlowRealPanPullsTheOpticalCentreButNeverBeyondTheCap() {
        val r = sweep(switchOn = true, stepDp = SLOW_STEP_DP)
        assertTrue("slow real pan must pull; gap=${r.maxGapPx}px", r.maxGapPx > 1f)
        assertTrue("gap ${r.maxGapPx} within cap", r.maxGapPx <= FocusSnap.MAX_PULL_DP * density + 0.5f)
    }

    @Test fun aFastRealPanDoesNotPullEvenWithTheSwitchOn() {
        val r = sweep(switchOn = true, stepDp = FAST_STEP_DP)
        assertEquals("fast pan gap", 0f, r.maxGapPx, 0.5f)
    }

    @Test fun withTheSwitchOffASlowPanDoesNotPull() {
        val r = sweep(switchOn = false, stepDp = SLOW_STEP_DP)
        assertEquals("switch off gap", 0f, r.maxGapPx, 0.5f)
    }

    @Test fun selectionIsTheSameWithAndWithoutSnapForTheSameFingerPath() {
        val off = sweep(switchOn = false, stepDp = SLOW_STEP_DP)
        val on = sweep(switchOn = true, stepDp = SLOW_STEP_DP)
        assertEquals("same finger end x", off.fingerAtEnd.first, on.fingerAtEnd.first, 1f)
        assertEquals("selection follows the finger, not the snap", off.selectedAtEnd, on.selectedAtEnd)
    }

    @Test fun aSwipeAtTheFrameBudgetSpeedReallyRunsTheSnapBranch() {
        val r = sweep(switchOn = true, stepDp = ADB_SWIPE_STEP_DP)
        assertTrue("88 dp/s is below 120 dp/s so the pull must run; gap=${r.maxGapPx}px", r.maxGapPx > 1f)
    }
}
