package com.mckimquyen.views

import android.graphics.Canvas
import android.os.SystemClock
import android.util.Log
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

/**
 * Tuning evidence, not a gate on feel: real DOWN/MOVE/UP sweeps at several finger speeds over grids of 40, 120 and 270
 * apps. For each it logs the MEASURED finger speed (from the event clock) and the average and largest pull of the
 * optical centre away from the finger, in dp. Log tag F044PROF. It asserts the shape the design promises: a slow pan
 * pulls, a pan well above the threshold does not, and no pull ever exceeds the cap.
 */
@RunWith(AndroidJUnit4::class)
class LensViewFocusSnapProfileTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val VIEW_SIZE_PX = 1_000
        const val STEPS = 50
        const val FRAME_DELAY_MS = 16L
        const val PAN_START_DP = 40f
        const val TAG = "F044PROF"
        const val MS_PER_S = 1000f
        const val ABOVE_THRESHOLD_FACTOR = 1.3f
        const val EPSILON_DP = 0.05f
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val density = context.resources.displayMetrics.density

    @After fun tearDown() { UtilSettings(context).save(UtilSettings.KEY_FOCUS_SNAP, false) }

    private class Row(val speedDpPerS: Float, val meanPullDp: Float, val maxPullDp: Float)

    private fun sweep(appCount: Int, stepPerAxisDp: Float): Row {
        UtilSettings(context).save(UtilSettings.KEY_FOCUS_SNAP, true)
        var sumPull = 0f
        var maxPull = 0f
        var samples = 0
        var speedSum = 0f
        var speedSamples = 0
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            var view: LensView? = null
            scenario.onActivity { activity ->
                view = LensView(activity).apply {
                    setApps(ArrayList((0 until appCount).map { App(id = it, packageName = "com.prof.p$it", name = "P$it", label = "P $it") }))
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
            x += PAN_START_DP * density; y -= PAN_START_DP * density
            send(MotionEvent.ACTION_MOVE, x, y)
            var lastX = x
            var lastY = y
            var lastMs = SystemClock.uptimeMillis()
            repeat(STEPS) {
                x += stepPerAxisDp * density
                y -= stepPerAxisDp * density
                send(MotionEvent.ACTION_MOVE, x, y)
                val nowMs = SystemClock.uptimeMillis()
                instrumentation.runOnMainSync {
                    target.draw(Canvas())
                    val t = target.touchCoordinatesForTest()
                    val o = target.opticalCentreForTest()
                    val pullDp = hypot(o.first - t.first, o.second - t.second) / density
                    sumPull += pullDp; maxPull = maxOf(maxPull, pullDp); samples++
                }
                val dt = (nowMs - lastMs).coerceAtLeast(1L)
                speedSum += hypot(x - lastX, y - lastY) / density / (dt / MS_PER_S); speedSamples++
                lastX = x; lastY = y; lastMs = nowMs
                SystemClock.sleep(FRAME_DELAY_MS)
            }
            send(MotionEvent.ACTION_UP, x, y)
        }
        return Row(speedSum / speedSamples, sumPull / samples, maxPull)
    }

    @Test fun pullProfileAcrossSpeedsAndGridDensities() {
        val stepsDp = listOf(0.15f, 0.5f, 1.0f, 1.5f, 2.5f, 6.0f)
        val capDp = FocusSnap.MAX_PULL_DP
        for (apps in listOf(40, 120, 270)) {
            val rows = stepsDp.map { sweep(apps, it) }
            rows.forEach {
                Log.i(TAG, "apps=$apps speed=${"%.0f".format(it.speedDpPerS)}dp/s meanPull=${"%.2f".format(it.meanPullDp)}dp maxPull=${"%.2f".format(it.maxPullDp)}dp")
                assertTrue("pull ${it.maxPullDp} within cap $capDp", it.maxPullDp <= capDp + EPSILON_DP)
            }
            assertTrue("apps=$apps: the slowest pan pulls (mean ${rows.first().meanPullDp})", rows.first().meanPullDp > EPSILON_DP)
            val fast = rows.last()
            assertTrue("apps=$apps: speed ${fast.speedDpPerS} is above the threshold", fast.speedDpPerS > FocusSnap.SPEED_THRESHOLD_DP_PER_S * ABOVE_THRESHOLD_FACTOR)
            assertEquals("apps=$apps: a fast pan does not pull", 0f, fast.maxPullDp, EPSILON_DP)
        }
    }
}
