package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LensVignetteTest {
    @Test fun opaqueInsideTheFocusZone() {
        val inside = LensVignette.intensity(DepthOfField.FOCUS_ZONE, 1f)
        assertEquals(0f, inside, 0f)
        assertEquals(LensVignette.OPAQUE_ALPHA, LensVignette.alpha(inside))
    }

    @Test fun opaqueWheneverTheTransitionIsZero() {
        assertEquals(0f, LensVignette.intensity(1f, 0f), 0f)
        assertEquals(LensVignette.OPAQUE_ALPHA, LensVignette.alpha(LensVignette.intensity(1f, 0f)))
    }

    @Test fun floorAtTheFarthestPointIsHalfOpacity() {
        val farthest = LensVignette.intensity(1f, 1f)
        assertEquals(1f, farthest, 1e-6f)
        assertEquals(128, LensVignette.alpha(farthest))
    }

    @Test fun alphaNeverFallsBelowTheFloorAndNeverRisesWithDistance() {
        var previous = LensVignette.OPAQUE_ALPHA
        var distance = 0f
        while (distance <= 1.0001f) {
            val a = LensVignette.alpha(LensVignette.intensity(distance, 1f))
            assertTrue("alpha $a at $distance must not exceed previous $previous", a <= previous)
            assertTrue("alpha $a below floor", a >= 128)
            previous = a
            distance += 0.05f
        }
    }

    @Test fun alphaClampsOutOfRangeIntensity() {
        assertEquals(LensVignette.OPAQUE_ALPHA, LensVignette.alpha(-3f))
        assertEquals(128, LensVignette.alpha(7f))
    }

    @Test fun nonFiniteInputsFailVisible() {
        assertEquals(0f, LensVignette.intensity(Float.NaN, 1f), 0f)
        assertEquals(0f, LensVignette.intensity(0.9f, Float.POSITIVE_INFINITY), 0f)
        assertEquals(LensVignette.OPAQUE_ALPHA, LensVignette.alpha(Float.NaN))
        assertEquals(LensVignette.OPAQUE_ALPHA, LensVignette.alpha(Float.NEGATIVE_INFINITY))
    }

    @Test fun combineTakesTheLowerAlphaAndIsSymmetric() {
        assertEquals(100, LensVignette.combine(100, 200))
        assertEquals(100, LensVignette.combine(200, 100))
        assertEquals(180, LensVignette.combine(255, 180))
        assertEquals(255, LensVignette.combine(255, 255))
    }

    @Test fun combineNeverMultipliesTheTwoEffects() {
        // 128 x 128 / 255 would be 64; the owner decision is "lower of the two", so it must stay 128.
        assertEquals(128, LensVignette.combine(128, 128))
    }

    @Test fun gateRequiresBothTheSettingAndALiveLens() {
        assertTrue(LensVignette.shouldApply(enabled = true, lensVisible = true))
        assertFalse(LensVignette.shouldApply(enabled = false, lensVisible = true))
        assertFalse(LensVignette.shouldApply(enabled = true, lensVisible = false))
        assertFalse(LensVignette.shouldApply(enabled = false, lensVisible = false))
    }
}
