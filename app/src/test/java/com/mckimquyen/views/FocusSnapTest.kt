package com.mckimquyen.views

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class FocusSnapTest {
    private val density = 3f
    private val out = FloatArray(2)

    private fun cell(cx: Float, cy: Float) = RectF(cx - 10f, cy - 10f, cx + 10f, cy + 10f)

    @Test fun theConstantsAreTheDesignedValues() {
        assertEquals(120f, FocusSnap.SPEED_THRESHOLD_DP_PER_S, 0f)
        assertEquals(16f, FocusSnap.MAX_PULL_DP, 0f)
    }

    @Test fun theEffectAppliesOnlyToAnEnabledRealPanWithoutReducedMotion() {
        for (enabled in listOf(false, true)) for (panning in listOf(false, true)) for (reduce in listOf(false, true)) {
            assertEquals("enabled=$enabled panning=$panning reduce=$reduce",
                enabled && panning && !reduce, FocusSnap.shouldApply(enabled, panning, reduce))
        }
    }

    @Test fun gainFallsLinearlyFromOneAtRestToZeroAtTheThreshold() {
        assertEquals(1f, FocusSnap.gain(0f), 0f)
        assertEquals(0.5f, FocusSnap.gain(60f), 1e-6f)
        assertEquals(0f, FocusSnap.gain(120f), 0f)
        assertEquals(0f, FocusSnap.gain(500f), 0f)
    }

    @Test fun gainIsZeroForNonFiniteOrNegativeInput() {
        assertEquals(0f, FocusSnap.gain(Float.NaN), 0f)
        assertEquals(0f, FocusSnap.gain(Float.POSITIVE_INFINITY), 0f)
        assertEquals(1f, FocusSnap.gain(-5f), 0f)
    }

    @Test fun nearestAppIsTheClosestCentreAndTiesGoToTheLowestIndex() {
        val rects = arrayOf(cell(0f, 0f), cell(100f, 0f), cell(200f, 0f))
        assertEquals(1, FocusSnap.nearestApp(90f, 5f, rects, 3))
        assertEquals(0, FocusSnap.nearestApp(50f, 0f, rects, 3))
        assertEquals(2, FocusSnap.nearestApp(1000f, 0f, rects, 3))
    }

    @Test fun nearestAppIgnoresCellsBeyondTheAppCountAndNonFiniteInput() {
        val rects = arrayOf(cell(0f, 0f), cell(100f, 0f), cell(200f, 0f))
        assertEquals("cell 2 has no app", 1, FocusSnap.nearestApp(200f, 0f, rects, 2))
        assertEquals(-1, FocusSnap.nearestApp(0f, 0f, rects, 0))
        assertEquals(-1, FocusSnap.nearestApp(Float.NaN, 0f, rects, 3))
        assertEquals(-1, FocusSnap.nearestApp(0f, 0f, emptyArray(), 3))
    }

    @Test fun pullMovesTowardTheTargetAndNeverExceedsTheCap() {
        val maxPx = FocusSnap.MAX_PULL_DP * density
        FocusSnap.pull(0f, 0f, 300f, 400f, maxPx, 1f, out)
        assertEquals(maxPx, hypot(out[0], out[1]), 1e-3f)
        assertEquals("direction x", 0.6f * maxPx, out[0], 1e-3f)
        assertEquals("direction y", 0.8f * maxPx, out[1], 1e-3f)
    }

    @Test fun pullStopsOnTheTargetWhenItIsCloserThanTheCap() {
        val maxPx = FocusSnap.MAX_PULL_DP * density
        FocusSnap.pull(10f, 10f, 13f, 14f, maxPx, 1f, out)
        assertEquals(3f, out[0], 1e-4f)
        assertEquals(4f, out[1], 1e-4f)
    }

    @Test fun pullScalesWithGainAndIsZeroWithoutGainOrDistance() {
        val maxPx = FocusSnap.MAX_PULL_DP * density
        FocusSnap.pull(0f, 0f, 300f, 0f, maxPx, 0.25f, out)
        assertEquals(0.25f * maxPx, out[0], 1e-3f)
        FocusSnap.pull(0f, 0f, 300f, 0f, maxPx, 0f, out)
        assertEquals(0f, out[0], 0f)
        FocusSnap.pull(5f, 5f, 5f, 5f, maxPx, 1f, out)
        assertEquals(0f, out[0], 0f)
        assertEquals(0f, out[1], 0f)
    }

    @Test fun pullIsZeroForNonFiniteInput() {
        val maxPx = FocusSnap.MAX_PULL_DP * density
        FocusSnap.pull(0f, 0f, Float.NaN, 0f, maxPx, 1f, out)
        assertEquals(0f, out[0], 0f); assertEquals(0f, out[1], 0f)
        FocusSnap.pull(0f, 0f, 10f, 0f, Float.NaN, 1f, out)
        assertEquals(0f, out[0], 0f); assertEquals(0f, out[1], 0f)
    }

    @Test fun trackerHasNoSpeedUntilTwoSamplesExist() {
        val t = FocusSnap.Tracker()
        assertEquals(Float.POSITIVE_INFINITY, t.speedDpPerS(0L), 0f)
        t.onMove(0f, 0f, 0L, density)
        assertEquals(Float.POSITIVE_INFINITY, t.speedDpPerS(1L), 0f)
    }

    @Test fun trackerReportsDistanceInDpOverElapsedSeconds() {
        val t = FocusSnap.Tracker()
        t.onMove(0f, 0f, 0L, density)
        t.onMove(30f, 40f, 100L, density) // 50 px = 16.667 dp in 0.1 s
        assertEquals(50f / density / 0.1f, t.speedDpPerS(100L), 1e-2f)
    }

    @Test fun aPausedFingerSlowsDownBecauseTimeSinceTheLastSampleCounts() {
        val t = FocusSnap.Tracker()
        t.onMove(0f, 0f, 0L, density)
        t.onMove(30f, 40f, 100L, density)
        val moving = t.speedDpPerS(100L)
        val paused = t.speedDpPerS(1_100L)
        assertTrue("paused $paused must be below moving $moving", paused < moving)
        assertEquals(50f / density / 1.0f, paused, 1e-2f)
    }

    @Test fun nonPositiveElapsedTimeFailsClosedThenRecoversOnTheNextGoodSample() {
        val t = FocusSnap.Tracker()
        t.onMove(0f, 0f, 100L, density)
        t.onMove(1f, 0f, 100L, density)
        assertEquals(Float.POSITIVE_INFINITY, t.speedDpPerS(100L), 0f)
        t.onMove(2f, 0f, 90L, density)
        assertEquals(Float.POSITIVE_INFINITY, t.speedDpPerS(100L), 0f)
        t.onMove(3f, 0f, 190L, density)
        assertEquals(1f / density / 0.1f, t.speedDpPerS(190L), 1e-2f)
    }

    @Test fun nonFiniteSamplesFailClosedAndNeverPoisonLaterOnes() {
        val t = FocusSnap.Tracker()
        t.onMove(0f, 0f, 0L, density)
        t.onMove(Float.NaN, 0f, 10L, density)
        assertEquals(Float.POSITIVE_INFINITY, t.speedDpPerS(10L), 0f)
        t.onMove(1f, 0f, 20L, density)
        assertEquals(Float.POSITIVE_INFINITY, t.speedDpPerS(20L), 0f)
        t.onMove(2f, 0f, 120L, density)
        assertEquals(1f / density / 0.1f, t.speedDpPerS(120L), 1e-2f)
    }

    @Test fun resetForgetsEverySample() {
        val t = FocusSnap.Tracker()
        t.onMove(0f, 0f, 0L, density)
        t.onMove(1f, 0f, 100L, density)
        t.reset()
        assertEquals(Float.POSITIVE_INFINITY, t.speedDpPerS(100L), 0f)
        t.onMove(50f, 50f, 200L, density)
        assertEquals("first sample after reset has no speed", Float.POSITIVE_INFINITY, t.speedDpPerS(200L), 0f)
    }

    @Test fun aNonPositiveDensityFailsClosed() {
        val t = FocusSnap.Tracker()
        t.onMove(0f, 0f, 0L, 0f)
        t.onMove(10f, 0f, 100L, 0f)
        assertFalse(t.speedDpPerS(100L).isFinite())
    }

    @Test fun theRedrawWindowIsTheDesignedValue() = assertEquals(500L, FocusSnap.REDRAW_WINDOW_MS)

    @Test fun trackerAsksForFramesOnlyForTheWindowAfterTheLastMove() {
        val t = FocusSnap.Tracker()
        assertFalse("no sample, no redraw", t.needsRedraw(0L))
        t.onMove(0f, 0f, 1_000L, density)
        assertTrue(t.needsRedraw(1_000L))
        assertTrue(t.needsRedraw(1_000L + FocusSnap.REDRAW_WINDOW_MS - 1))
        assertFalse(t.needsRedraw(1_000L + FocusSnap.REDRAW_WINDOW_MS))
        t.reset()
        assertFalse(t.needsRedraw(1_000L))
    }
}
