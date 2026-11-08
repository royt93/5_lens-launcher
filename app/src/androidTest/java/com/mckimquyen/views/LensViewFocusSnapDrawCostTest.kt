package com.mckimquyen.views

import android.graphics.Canvas
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-044 attribution benchmark, not a gate: CPU time of drawGrid with a no-op canvas, so it is free of the
 * 1 ms gfxinfo resolution and of adb swipe noise. Three modes are interleaved:
 *  OFF     snap switch off
 *  CONTROL snap on, finger exactly on the nearest centre (snap maths run, pull is zero so no second select rect)
 *  SNAP    snap on, finger offset (snap maths run and the second select rect is computed for every cell)
 * Results go to logcat under tag F044COST; it asserts that the three modes ran and that SNAP stays within
 * [MAX_SNAP_TO_OFF_RATIO] of OFF.
 */
@RunWith(AndroidJUnit4::class)
class LensViewFocusSnapDrawCostTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val VIEW_SIZE_PX = 1_000
        const val APP_COUNT = 120
        const val WARMUP_DRAWS = 300
        const val DRAWS_PER_ROUND = 400
        const val ROUNDS = 9
        const val NANOS_PER_MICRO = 1_000L
        const val TAG = "F044COST"
        /** Measured SNAP/OFF is about 1.3; the bound only catches an order-of-magnitude regression, not noise. */
        const val MAX_SNAP_TO_OFF_RATIO = 2.0
    }

    private enum class Mode { OFF, CONTROL, SNAP }

    @Test fun drawCostPerMode() {
        val perMode = Mode.values().associateWith { ArrayList<Long>() }
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = LensView(activity).apply {
                    setApps(ArrayList((0 until APP_COUNT).map { App(id = it, packageName = "com.fish044.c$it", name = "C$it", label = "C $it") }))
                    layoutParams = FrameLayout.LayoutParams(VIEW_SIZE_PX, VIEW_SIZE_PX)
                    activity.setContentView(this)
                    measure(View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY))
                    layout(0, 0, VIEW_SIZE_PX, VIEW_SIZE_PX)
                    draw(Canvas())
                }
                val density = activity.resources.displayMetrics.density
                val rects = view.baseRectsForTest()
                val cell = rects[rects.size / 2]
                val offsetPx = 10f * density

                fun configure(mode: Mode) {
                    val x = if (mode == Mode.SNAP) cell.centerX() + offsetPx else cell.centerX()
                    val y = if (mode == Mode.SNAP) cell.centerY() + offsetPx else cell.centerY()
                    view.setSnapStateForTest(enabled = mode != Mode.OFF, panning = true, speedDpPerS = 0f)
                    view.setLensStateForTest(touchX = x, touchY = y, animationMultiplier = 1f, reduceMotion = false)
                }

                repeat(WARMUP_DRAWS) { Mode.values().forEach { m -> configure(m); view.draw(Canvas()) } }
                val canvas = Canvas()
                repeat(ROUNDS) {
                    Mode.values().forEach { mode ->
                        configure(mode)
                        val start = SystemClock.elapsedRealtimeNanos()
                        repeat(DRAWS_PER_ROUND) { view.draw(canvas) }
                        perMode.getValue(mode).add((SystemClock.elapsedRealtimeNanos() - start) / DRAWS_PER_ROUND / NANOS_PER_MICRO)
                    }
                }
            }
        }
        val median = perMode.mapValues { (_, v) -> v.sorted()[v.size / 2] }
        Mode.values().forEach { Log.i(TAG, "${it.name}: median ${median.getValue(it)} us/draw, rounds=${perMode.getValue(it)}") }
        assertTrue(perMode.values.all { it.size == ROUNDS })
        assertTrue("SNAP ${median.getValue(Mode.SNAP)} us vs OFF ${median.getValue(Mode.OFF)} us",
            median.getValue(Mode.SNAP) < median.getValue(Mode.OFF) * MAX_SNAP_TO_OFF_RATIO)
    }
}
