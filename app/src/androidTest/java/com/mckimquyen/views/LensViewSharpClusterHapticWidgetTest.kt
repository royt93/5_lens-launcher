package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-042 rev2 widget proof on a bare LensView drawn into a software canvas. No test seam primes the tracker: every pulse
 * below is produced by really moving the lens centre across the grid, so a gate test can only be silenced by its gate.
 */
@RunWith(AndroidJUnit4::class)
class LensViewSharpClusterHapticWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SIZE_PX = 1_000
        const val APP_COUNT = 120
        const val SWEEP_STEPS = 160
        const val SWEEP_STEP_PX = 6
        const val FULL_ANIMATION = 1f
        const val LIGHT_AMPLITUDE = 80
        const val MIN_GAP_MS = 120L
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var lensView: LensView
    private val haptics = mutableListOf<Int>()

    @Before
    fun setUp() {
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish042.w$i", name = "W$i", label = "W $i")
        })
        instrumentation.runOnMainSync {
            lensView = LensView(context).apply {
                layout(0, 0, SIZE_PX, SIZE_PX)
                setApps(apps)
                onHapticPerformed = { haptics.add(it) }
            }
        }
        haptics.clear()
    }

    private fun render() {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync { lensView.draw(Canvas(bitmap)) }
        bitmap.recycle()
    }

    private fun startPan() {
        val moving = LensView::class.java.getDeclaredField("mMoving").apply { isAccessible = true }
        moving.setBoolean(lensView, true)
        instrumentation.runOnMainSync { lensView.gestureState = LensGestureState.PANNING }
    }

    private fun touchAt(x: Int, y: Int, reduceMotion: Boolean = false) =
        lensView.setLensStateForTest(x.toFloat(), y.toFloat(), FULL_ANIMATION, reduceMotion)

    /** One slow horizontal sweep at mid height; returns the number of light pulses and the number of cluster-size changes. */
    private fun sweep(reduceMotion: Boolean = false, pan: Boolean = true): Pair<Int, Int> {
        var lightPulses = 0
        var sizeChanges = 0
        var previous = -1
        touchAt(40, SIZE_PX / 2, reduceMotion)
        if (pan) startPan()
        var x = 40
        repeat(SWEEP_STEPS) {
            touchAt(x, SIZE_PX / 2, reduceMotion)
            val before = haptics.size
            render()
            lightPulses += haptics.drop(before).count { it == LIGHT_AMPLITUDE }
            val size = lensView.sharpClusterSizeForTest
            if (previous != -1 && size != previous) sizeChanges++
            previous = size
            // The tracker throttles in real time, so give each frame a little wall-clock spacing.
            Thread.sleep(4)
            x += SWEEP_STEP_PX
        }
        return lightPulses to sizeChanges
    }

    @Test fun aSweepReachesClusterSizeChangesAndEmitsAtLeastOneLightPulse() {
        val (pulses, changes) = sweep()
        assertTrue("the cluster size really changes during a sweep (was $changes)", changes >= 3)
        assertTrue("at least one LIGHT pulse is emitted (was $pulses)", pulses >= 1)
    }

    @Test fun pulsesAreFewerThanClusterChangesBecauseTheyAreThrottled() {
        val (pulses, changes) = sweep()
        assertTrue("throttled: pulses ($pulses) never exceed size changes ($changes)", pulses <= changes)
    }

    @Test fun withTheHoverSwitchOffNoPulseFiresEvenThoughTheClusterChanges() {
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        val (pulses, _) = sweep()
        assertEquals("switch off", 0, pulses)
        assertTrue("and no haptic of any kind", haptics.isEmpty())
    }

    @Test fun reducedMotionSuppressesPulsesEvenThoughTheClusterChanges() {
        val gesture = LensView::class.java.getDeclaredField("mGestureReduceMotion").apply { isAccessible = true }
        gesture.set(lensView, true)
        val (pulses, _) = sweep(reduceMotion = true)
        assertEquals("reduced motion", 0, pulses)
    }

    @Test fun withoutARealPanNoPulseFiresEvenThoughTheLensMoves() {
        val (pulses, _) = sweep(pan = false)
        assertEquals("no pan", 0, pulses)
    }

    /** The most haptics any single rendered frame produced across the sweep (cluster pulse and hover pulse must not stack). */
    private fun busiestFrameDuringASweep(): Int {
        var busiest = 0
        touchAt(40, SIZE_PX / 2)
        startPan()
        var x = 40
        repeat(SWEEP_STEPS) {
            touchAt(x, SIZE_PX / 2)
            val before = haptics.size
            render()
            busiest = maxOf(busiest, haptics.size - before)
            Thread.sleep(4)
            x += SWEEP_STEP_PX
        }
        return busiest
    }

    @Test fun noSingleFrameEverEmitsMoreThanOneHaptic() {
        assertTrue("one haptic per frame: the cluster pulse suppresses the hover pulse", busiestFrameDuringASweep() <= 1)
    }

    /**
     * A sweep lasts well over 120 ms and the cluster changes many times, so the 120 ms rule must let SEVERAL pulses through.
     * With a broken clock (constant time) only the very first pulse could ever fire and every later one would be throttled.
     */
    @Test fun aLongSweepEmitsSeveralPulsesSpacedByAtLeastTheMinimumGap() {
        val times = mutableListOf<Long>()
        lensView.onHapticPerformed = { amplitude ->
            haptics.add(amplitude)
            if (amplitude == LIGHT_AMPLITUDE) times.add(android.os.SystemClock.uptimeMillis())
        }
        sweep()
        assertTrue("several pulses over a long sweep, was ${times.size}", times.size >= 2)
        times.zipWithNext().forEach { (a, b) ->
            assertTrue("pulses ${b - a} ms apart, need at least ${MIN_GAP_MS - 15}", b - a >= MIN_GAP_MS - 15)
        }
    }

    @Test fun actionUpAndActionCancelWipeTheTrackerSoTheNextGestureStartsFromABaseline() {
        for (action in intArrayOf(android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL)) {
            touchAt(40, SIZE_PX / 2)
            startPan()
            render()
            val sizeBefore = lensView.sharpClusterSizeForTest
            assertTrue("the cluster is non-empty before the gesture ends", sizeBefore > 0)
            val down = android.os.SystemClock.uptimeMillis()
            instrumentation.runOnMainSync {
                val end = android.view.MotionEvent.obtain(down, down + 30, action, 40f, SIZE_PX / 2f, 0)
                lensView.dispatchTouchEvent(end)
                end.recycle()
            }
            assertEquals("tracker was reset by action $action", 0, lensView.sharpClusterSizeForTest)
        }
    }

    @Test fun theFirstFrameOfAPanIsABaselineAndNeverPulses() {
        touchAt(40, SIZE_PX / 2)
        startPan()
        haptics.clear()
        render()
        assertFalse("first frame never pulses", haptics.contains(LIGHT_AMPLITUDE))
    }
}
