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
import com.mckimquyen.util.HapticIntensity
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Reachability test: a real ACTION_DOWN/MOVE/UP sweep must reach a sharp-cluster change without priming any tracker. */
@RunWith(AndroidJUnit4::class)
class LensViewSharpClusterHapticReachabilityTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val VIEW_SIZE_PX = 1_000
        const val APP_COUNT = 120
        const val SWEEP_STEPS = 160
        const val SWEEP_STEP_PX = 6f
        const val FRAME_DELAY_MS = 16L
        const val LIGHT_AMPLITUDE = 80
        const val MIN_PULSE_GAP_MS = 120L
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun aRealSweepAcrossTheGridReachesClusterSizeChangesAndEmitsThrottledLightPulses() {
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish042.cluster$i", name = "Cluster$i", label = "Cluster $i")
        })
        val amplitudes = ArrayList<Int>()
        val pulseTimes = ArrayList<Long>()
        var view: LensView? = null
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                view = LensView(activity).apply {
                    setApps(apps)
                    layoutParams = FrameLayout.LayoutParams(VIEW_SIZE_PX, VIEW_SIZE_PX)
                    activity.setContentView(this)
                    measure(
                        View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY)
                    )
                    layout(0, 0, VIEW_SIZE_PX, VIEW_SIZE_PX)
                    onHapticPerformed = { amplitudes.add(it); pulseTimes.add(SystemClock.uptimeMillis()) }
                    draw(Canvas())
                }
            }
            val target = requireNotNull(view)
            val downTime = SystemClock.uptimeMillis()
            fun send(action: Int, x: Float, y: Float) = instrumentation.runOnMainSync {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                target.dispatchTouchEvent(event)
                event.recycle()
            }

            // Start in lower-left quadrant, establish a real pan past touch slop, then sweep through the middle.
            val startX = 80f
            val startY = 820f
            send(MotionEvent.ACTION_DOWN, startX, startY)
            send(MotionEvent.ACTION_MOVE, startX + 40f, startY - 40f)
            send(MotionEvent.ACTION_MOVE, startX + 80f, startY - 80f)
            amplitudes.clear()
            pulseTimes.clear()

            var x = startX + 80f
            var y = startY - 80f
            repeat(SWEEP_STEPS) {
                x += SWEEP_STEP_PX
                y -= SWEEP_STEP_PX
                send(MotionEvent.ACTION_MOVE, x, y)
                instrumentation.runOnMainSync { target.draw(Canvas()) }
                SystemClock.sleep(FRAME_DELAY_MS)
            }
            send(MotionEvent.ACTION_UP, x, y)

            val lightTimes = amplitudes.indices.filter { amplitudes[it] == LIGHT_AMPLITUDE }.map { pulseTimes[it] }
            assertTrue("real sweep must reach at least one cluster pulse; amplitudes=$amplitudes", lightTimes.isNotEmpty())
            // Timestamps come from the callback, so they carry a few ms of scheduling jitter; allow a small tolerance.
            val tolerance = 15L
            lightTimes.zipWithNext().forEach { (a, b) ->
                assertTrue("pulses ${b - a} ms apart, need at least ${MIN_PULSE_GAP_MS - tolerance}", b - a >= MIN_PULSE_GAP_MS - tolerance)
            }
            scenario.close()
        }
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
    }
}
