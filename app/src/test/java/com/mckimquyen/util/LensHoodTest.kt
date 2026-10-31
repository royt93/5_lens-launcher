package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LensHoodTest {
    private val lens = 400f
    private val boundary = 1000f
    private val multiplier = 1f
    private val distortion = 2.5f
    /** b = max(lens, boundary - lens) = 600. */
    private val reach = 600f

    private fun hooded(item: Float, hood: Float, mult: Float = multiplier) =
        UtilCalculator.shiftPoint(lens, item, boundary, mult, distortion, hood)

    private fun legacy(item: Float) = UtilCalculator.shiftPoint(lens, item, boundary, multiplier, distortion)

    @Test fun aFullHoodIsExactlyTheLegacyFormula() {
        for (item in listOf(0f, 120f, 399f, 401f, 700f, 1000f)) {
            assertEquals("item $item", legacy(item), hooded(item, LensHood.FULL), 0f)
        }
    }

    @Test fun anIconBeyondTheHoodDoesNotMove() {
        // hood 0.5 => radius 300 px; an icon 500 px right of the lens is outside it.
        assertEquals(900f, hooded(900f, 0.5f), 0f)
        // and so is one 350 px to the left.
        assertEquals(50f, hooded(50f, 0.5f), 0f)
    }

    @Test fun anIconExactlyOnTheHoodEdgeDoesNotMove() {
        val edge = lens + 0.5f * reach
        assertEquals(edge, hooded(edge, 0.5f), 0.001f)
    }

    @Test fun anIconInsideTheHoodStillMovesAwayFromTheLens() {
        val item = lens + 100f
        val moved = hooded(item, 0.5f)
        assertTrue("an icon inside the hood must be pushed outward, got $moved", moved > item)
        assertTrue("but never past the hood edge, got $moved", moved < lens + 0.5f * reach)
    }

    @Test fun theWarpIsContinuousAcrossTheHoodEdge() {
        val edge = lens + 0.5f * reach
        val justInside = hooded(edge - 0.5f, 0.5f)
        assertEquals("no jump at the hood edge", edge - 0.5f, justInside, 1.0f)
    }

    @Test fun theMovementKeepsItsSideOfTheLens() {
        assertTrue(hooded(lens - 100f, 0.5f) < lens)
        assertTrue(hooded(lens + 100f, 0.5f) > lens)
    }

    @Test fun aSmallerHoodNeverMovesAnIconFurtherThanALargerOne() {
        val item = lens + 150f
        val small = hooded(item, 0.4f) - item
        val large = hooded(item, 1.0f) - item
        assertTrue("small hood moved $small vs full $large", small <= large + 0.001f)
    }

    @Test fun anIdleLensOrIdleAnimationLeavesEveryHoodUntouched() {
        for (hood in listOf(LensHood.MIN, 0.5f, LensHood.FULL)) {
            assertEquals(700f, UtilCalculator.shiftPoint(-Float.MAX_VALUE, 700f, boundary, 1f, distortion, hood), 0f)
            assertEquals(700f, hooded(700f, hood, mult = 0f), 0.0001f)
        }
    }

    /** One axis of the icon size: twice the gap between the scaled and the shifted centre. */
    private fun axisSize(item: Float, size: Float, scale: Float, hood: Float, mult: Float = multiplier): Float {
        val scaled = UtilCalculator.scalePoint(lens, item, size, boundary, mult, scale, distortion, hood)
        val shifted = UtilCalculator.shiftPoint(lens, item, boundary, mult, distortion, hood)
        return 2f * Math.abs(scaled - shifted)
    }

    @Test fun anIconOutsideTheHoodKeepsItsOwnSizeEvenWithAScaleFactor() {
        // scale 1.5 would magnify it by 1.5 if only the position were pinned.
        assertEquals(80f, axisSize(item = 900f, size = 80f, scale = 1.5f, hood = 0.5f), 0.001f)
        assertEquals(80f, axisSize(item = 50f, size = 80f, scale = 2.0f, hood = 0.5f), 0.001f)
    }

    @Test fun theIconSizeIsContinuousAcrossTheHoodEdge() {
        val edge = lens + 0.5f * reach
        val justInside = axisSize(item = edge - 0.2f, size = 80f, scale = 1.5f, hood = 0.5f)
        val justOutside = axisSize(item = edge + 0.2f, size = 80f, scale = 1.5f, hood = 0.5f)
        assertEquals("no size jump at the hood edge", justOutside, justInside, 1.0f)
    }

    @Test fun anIconAtTheLensCentreIsMagnifiedByTheFullScaleFactor() {
        // The falloff is 0 at the edge and 1 at the lens, so the centre keeps the full effect.
        val atCentre = axisSize(item = lens + 1f, size = 80f, scale = 1.5f, hood = 0.5f)
        val atEdge = axisSize(item = lens + 0.5f * reach - 1f, size = 80f, scale = 1.5f, hood = 0.5f)
        assertTrue("centre $atCentre must beat the edge $atEdge", atCentre > atEdge)
    }

    @Test fun aFullHoodKeepsTheLegacyScaleOnEveryItem() {
        for (item in listOf(0f, 120f, 401f, 700f, 1000f)) {
            val legacyScaled = UtilCalculator.scalePoint(lens, item, 80f, boundary, 1f, 1.5f, distortion)
            val hoodScaled = UtilCalculator.scalePoint(lens, item, 80f, boundary, 1f, 1.5f, distortion, LensHood.FULL)
            assertEquals("item $item", legacyScaled, hoodScaled, 0f)
        }
    }

    @Test fun theStoredValueIsClampedAndNonFiniteFallsBackToFull() {
        assertEquals(LensHood.FULL, LensHood.clamp(Float.NaN), 0f)
        assertEquals(LensHood.FULL, LensHood.clamp(Float.POSITIVE_INFINITY), 0f)
        assertEquals(LensHood.FULL, LensHood.clamp(3f), 0f)
        assertEquals(LensHood.MIN, LensHood.clamp(-1f), 0f)
        assertEquals(0.6f, LensHood.clamp(0.6f), 0f)
    }

    @Test fun snappingKeepsTheSliderOnItsStep() {
        assertEquals(0.55f, LensHood.snap(0.5612f), 0.0001f)
        assertEquals(LensHood.FULL, LensHood.snap(0.999f), 0f)
        assertEquals(LensHood.MIN, LensHood.snap(0.01f), 0f)
    }

    @Test fun theFalloffOfAFullHoodIsOneEverywhere() {
        // shiftPoint/scalePoint return early at FULL, so only a direct call proves this branch.
        for (distance in listOf(0f, 100f, 600f, 5_000f)) {
            assertEquals("distance $distance", 1f, LensHood.falloff(distance, reach, LensHood.FULL), 0f)
        }
    }

    @Test fun theFalloffIsOneAtTheLensZeroAtTheEdgeAndFlatBeyond() {
        val radius = 0.5f * reach
        assertEquals(1f, LensHood.falloff(0f, reach, 0.5f), 0f)
        assertEquals(0f, LensHood.falloff(radius, reach, 0.5f), 0f)
        assertEquals(0f, LensHood.falloff(radius * 3f, reach, 0.5f), 0f)
    }

    @Test fun theFalloffIsASmoothstepNotALinearRamp() {
        val radius = 0.5f * reach
        // smoothstep(t) = t*t*(3-2t) with t = 1 - distance/radius.
        assertEquals("at a quarter of the radius from the edge", 0.15625f, LensHood.falloff(radius * 0.75f, reach, 0.5f), 0.0001f)
        assertEquals("at the midpoint both curves agree", 0.5f, LensHood.falloff(radius * 0.5f, reach, 0.5f), 0.0001f)
        assertEquals("at three quarters towards the lens", 0.84375f, LensHood.falloff(radius * 0.25f, reach, 0.5f), 0.0001f)
        // zero slope at the edge is what hides the hood boundary: a linear ramp would have slope -1/radius.
        val nearEdge = LensHood.falloff(radius - 1f, reach, 0.5f)
        assertTrue("a smoothstep is almost flat 1 px inside the edge, got $nearEdge", nearEdge < 0.0001f)
    }

    @Test fun theFalloffNeverIncreasesWithDistance() {
        var previous = 1f
        for (step in 0..60) {
            val value = LensHood.falloff(step * 5f, reach, 0.5f)
            assertTrue("falloff rose from $previous to $value at ${step * 5f}", value <= previous + 0.00001f)
            previous = value
        }
    }
}
