package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-044 x FISH-042: the sharp cluster and its haptic read the OPTICAL centre, so a snapped centre must change what the
 * cluster counts, and the cluster must be counted from exactly that centre (not from the raw finger).
 */
@RunWith(AndroidJUnit4::class)
class LensViewFocusSnapClusterInteractionTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SIZE_PX = 1_000
        const val APP_COUNT = 120
        const val FULL_ANIMATION = 1f
        const val IDLE = -Float.MAX_VALUE
        const val GRID_MARGIN_PX = 60
        const val GRID_STEP_PX = 60
        const val MIN_DIFFERING_POINTS = 5
        const val LIGHT_AMPLITUDE = 80
        const val SWEEP_STEPS = 160
        const val SWEEP_STEP_PX = 6
        const val SWEEP_FRAME_SLEEP_MS = 4L
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var lensView: LensView
    private val haptics = mutableListOf<Int>()

    @Before fun setUp() {
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        val apps = ArrayList((0 until APP_COUNT).map { App(id = it, packageName = "com.fish044.k$it", name = "K$it", label = "K $it") })
        instrumentation.runOnMainSync {
            lensView = LensView(context).apply {
                layout(0, 0, SIZE_PX, SIZE_PX)
                setApps(apps)
                onHapticPerformed = { haptics.add(it) }
            }
        }
        haptics.clear()
    }

    @After fun tearDown() {
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        UtilSettings(context).save(UtilSettings.KEY_FOCUS_SNAP, false)
    }

    private fun render() {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync { lensView.draw(Canvas(bitmap)) }
        bitmap.recycle()
    }

    private fun startPan() {
        LensView::class.java.getDeclaredField("mMoving").apply { isAccessible = true }.setBoolean(lensView, true)
        instrumentation.runOnMainSync { lensView.gestureState = LensGestureState.PANNING }
    }

    /** Sharp-cluster size for a finger at (x, y) starting from a fresh tracker; the idle frame in between resets it. */
    private fun clusterAt(x: Int, y: Int, snap: Boolean): Int {
        instrumentation.runOnMainSync { lensView.setSnapStateForTest(enabled = snap, panning = true, speedDpPerS = 0f) }
        lensView.setLensStateForTest(IDLE, IDLE, FULL_ANIMATION, false)
        render()
        lensView.setLensStateForTest(x.toFloat(), y.toFloat(), FULL_ANIMATION, false)
        startPan()
        render()
        return lensView.sharpClusterSizeForTest
    }

    private fun gridPoints() = (GRID_MARGIN_PX until SIZE_PX - GRID_MARGIN_PX step GRID_STEP_PX).flatMap { y ->
        (GRID_MARGIN_PX until SIZE_PX - GRID_MARGIN_PX step GRID_STEP_PX).map { x -> x to y }
    }

    @Test fun snapChangesWhatTheSharpClusterCountsAtManyFingerPositions() {
        render()
        var differing = 0
        for ((x, y) in gridPoints()) {
            if (clusterAt(x, y, snap = false) != clusterAt(x, y, snap = true)) differing++
        }
        assertTrue("snap must change the cluster size at several of ${gridPoints().size} points, was $differing", differing >= MIN_DIFFERING_POINTS)
    }

    @Test fun theClusterIsCountedFromTheSnappedOpticalCentreNotTheRawFinger() {
        render()
        val rects = lensView.baseRectsForTest()
        for ((x, y) in gridPoints()) {
            val counted = clusterAt(x, y, snap = true)
            val optic = lensView.opticalCentreForTest()
            // The grid has one more cell than apps (121 vs 120); the draw loop skips it, so only app cells count.
            val fromOptic = (0 until APP_COUNT).count {
                DepthOfField.focusDistance(optic.first, optic.second, rects[it].centerX(), rects[it].centerY(), SIZE_PX.toFloat(), SIZE_PX.toFloat()) <= SharpCluster.ENTER_DISTANCE
            }
            assertEquals("cluster at ($x,$y) must be the count around the optical centre ${optic.first},${optic.second}", fromOptic, counted)
        }
    }

    @Test fun aSlowSweepWithSnapOnStillEmitsLightPulsesThrottledBy120Ms() {
        instrumentation.runOnMainSync { lensView.setSnapStateForTest(enabled = true, panning = true, speedDpPerS = 0f) }
        val times = ArrayList<Long>()
        lensView.onHapticPerformed = { if (it == LIGHT_AMPLITUDE) times.add(android.os.SystemClock.uptimeMillis()) }
        var x = 40
        lensView.setLensStateForTest(x.toFloat(), (SIZE_PX / 2).toFloat(), FULL_ANIMATION, false)
        startPan()
        repeat(SWEEP_STEPS) {
            lensView.setLensStateForTest(x.toFloat(), (SIZE_PX / 2).toFloat(), FULL_ANIMATION, false)
            render()
            Thread.sleep(SWEEP_FRAME_SLEEP_MS)
            x += SWEEP_STEP_PX
        }
        assertTrue("a snapped sweep must still reach cluster pulses; pulses=${times.size}", times.isNotEmpty())
        times.zipWithNext().forEach { (a, b) -> assertTrue("pulses ${b - a} ms apart", b - a >= 105L) }
    }
}
