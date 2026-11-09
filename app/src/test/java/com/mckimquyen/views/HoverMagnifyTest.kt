package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoverMagnifyTest {
    private val still = 0f

    @Test fun theConstantsAreTheDesignedValues() {
        assertEquals(200L, HoverMagnify.DWELL_MS)
        assertEquals(150L, HoverMagnify.RAMP_MS)
        assertEquals(15f, HoverMagnify.STILL_SPEED_DP_PER_S, 0f)
        assertEquals(1.2f, HoverMagnify.SCALE, 0f)
    }

    @Test fun theEffectNeedsTheSwitchARealPanAndMotion() {
        for (enabled in listOf(false, true)) for (panning in listOf(false, true)) for (reduce in listOf(false, true)) {
            assertEquals("enabled=$enabled panning=$panning reduce=$reduce", enabled && panning && !reduce, HoverMagnify.shouldApply(enabled, panning, reduce))
        }
    }

    @Test fun scaleIsOneUntilTheDwellHasPassedThenRampsLinearlyToTheTarget() {
        val t = HoverMagnify.Tracker()
        t.onFrame(4, still, 0L)
        assertEquals(1f, t.scaleFor(4, 0L), 0f)
        assertEquals(1f, t.scaleFor(4, HoverMagnify.DWELL_MS - 1), 0f)
        assertEquals(1f, t.scaleFor(4, HoverMagnify.DWELL_MS), 1e-6f)
        assertEquals(1f + (HoverMagnify.SCALE - 1f) / 2f, t.scaleFor(4, HoverMagnify.DWELL_MS + HoverMagnify.RAMP_MS / 2), 1e-4f)
        assertEquals(HoverMagnify.SCALE, t.scaleFor(4, HoverMagnify.DWELL_MS + HoverMagnify.RAMP_MS), 1e-6f)
        assertEquals(HoverMagnify.SCALE, t.scaleFor(4, 5_000L), 0f)
    }

    @Test fun onlyTheDwellingIconIsScaled() {
        val t = HoverMagnify.Tracker()
        t.onFrame(4, still, 0L)
        assertEquals(1f, t.scaleFor(5, 1_000L), 0f)
        assertEquals(1f, t.scaleFor(-1, 1_000L), 0f)
    }

    @Test fun changingTheSelectedIconRestartsTheDwellAndDropsTheScaleAtOnce() {
        val t = HoverMagnify.Tracker()
        t.onFrame(4, still, 0L)
        t.onFrame(4, still, 500L)
        assertEquals(HoverMagnify.SCALE, t.scaleFor(4, 500L), 0f)
        t.onFrame(7, still, 510L)
        assertEquals("old icon is back to 1 immediately", 1f, t.scaleFor(4, 510L), 0f)
        assertEquals("new icon has not dwelled yet", 1f, t.scaleFor(7, 510L + HoverMagnify.DWELL_MS - 1), 0f)
        t.onFrame(7, still, 510L + HoverMagnify.DWELL_MS + HoverMagnify.RAMP_MS)
        assertEquals(HoverMagnify.SCALE, t.scaleFor(7, 510L + HoverMagnify.DWELL_MS + HoverMagnify.RAMP_MS), 1e-6f)
    }

    @Test fun aFingerMovingAtOrAboveTheStillSpeedRestartsTheDwell() {
        val t = HoverMagnify.Tracker()
        t.onFrame(4, still, 0L)
        t.onFrame(4, still, 400L)
        assertEquals(HoverMagnify.SCALE, t.scaleFor(4, 400L), 0f)
        t.onFrame(4, HoverMagnify.STILL_SPEED_DP_PER_S, 410L)
        assertEquals("moving fast again, back to 1", 1f, t.scaleFor(4, 410L), 0f)
        t.onFrame(4, still, 420L)
        assertEquals("a new dwell starts from the first still frame", 1f, t.scaleFor(4, 420L + HoverMagnify.DWELL_MS - 1), 0f)
    }

    @Test fun justBelowTheStillSpeedCountsAsStill() {
        val t = HoverMagnify.Tracker()
        val below = Math.nextDown(HoverMagnify.STILL_SPEED_DP_PER_S)
        t.onFrame(2, below, 0L)
        t.onFrame(2, below, HoverMagnify.DWELL_MS + HoverMagnify.RAMP_MS)
        assertEquals(HoverMagnify.SCALE, t.scaleFor(2, HoverMagnify.DWELL_MS + HoverMagnify.RAMP_MS), 1e-6f)
    }

    @Test fun anUnknownOrNonFiniteSpeedNeverMagnifies() {
        val t = HoverMagnify.Tracker()
        for (speed in listOf(Float.POSITIVE_INFINITY, Float.NaN)) {
            t.reset()
            t.onFrame(3, speed, 0L)
            t.onFrame(3, speed, 5_000L)
            assertEquals("speed=$speed", 1f, t.scaleFor(3, 5_000L), 0f)
        }
    }

    @Test fun noSelectedIconMeansNoMagnification() {
        val t = HoverMagnify.Tracker()
        t.onFrame(-1, still, 0L)
        t.onFrame(-1, still, 5_000L)
        assertEquals(1f, t.scaleFor(-1, 5_000L), 0f)
        assertEquals(1f, t.scaleFor(0, 5_000L), 0f)
    }

    @Test fun needsRedrawWhileDwellingOrRampingAndStopsOnceTheTargetIsReached() {
        val t = HoverMagnify.Tracker()
        assertFalse("nothing selected", t.needsRedraw(0L))
        t.onFrame(4, still, 0L)
        assertTrue(t.needsRedraw(1L))
        assertTrue(t.needsRedraw(HoverMagnify.DWELL_MS + HoverMagnify.RAMP_MS - 1))
        assertFalse("settled at the target", t.needsRedraw(HoverMagnify.DWELL_MS + HoverMagnify.RAMP_MS))
        t.onFrame(4, HoverMagnify.STILL_SPEED_DP_PER_S, 5_000L)
        assertFalse("moving: no dwell is running", t.needsRedraw(5_001L))
    }

    @Test fun resetForgetsTheIconAndTheDwell() {
        val t = HoverMagnify.Tracker()
        t.onFrame(4, still, 0L)
        t.onFrame(4, still, 1_000L)
        t.reset()
        assertEquals(1f, t.scaleFor(4, 1_000L), 0f)
        assertFalse(t.needsRedraw(1_000L))
        t.onFrame(4, still, 1_010L)
        assertEquals("dwell restarts after a reset", 1f, t.scaleFor(4, 1_010L + HoverMagnify.DWELL_MS - 1), 0f)
    }

    @Test fun aClockThatGoesBackwardsNeverProducesAScaleAboveTheTargetOrBelowOne() {
        val t = HoverMagnify.Tracker()
        t.onFrame(4, still, 1_000L)
        for (now in listOf(0L, 500L, 1_000L, 1_199L, 1_200L, 1_275L, 1_350L, 99_999L)) {
            val s = t.scaleFor(4, now)
            assertTrue("scale $s at $now", s in 1f..HoverMagnify.SCALE)
        }
    }
}
