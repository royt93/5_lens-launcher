package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LensInertiaTest {
    private val tau = LensInertia.TIME_CONSTANT_MS

    @Test fun theConstantsAreTheDesignedValues() {
        assertEquals(100f, LensInertia.TIME_CONSTANT_MS, 0f)
        assertEquals(1f, LensInertia.EPSILON_PX, 0f)
        assertEquals(0.5f, LensInertia.SAME_CENTRE_PX, 0f)
        assertEquals(50f, LensInertia.MAX_DT_MS, 0f)
    }

    @Test fun theEffectAppliesOnlyToAnEnabledRealPanWithoutReducedMotion() {
        for (enabled in listOf(false, true)) for (panning in listOf(false, true)) for (reduce in listOf(false, true)) {
            assertEquals("enabled=$enabled panning=$panning reduce=$reduce",
                enabled && panning && !reduce, LensInertia.shouldApply(enabled, panning, reduce))
        }
    }

    @Test fun theTimeConstantCoversSixtyThreePercentOfTheGapAcrossFramesOfTheSameTotalTime() {
        // 100 ms in 16 ms frames (the per-step clamp is 50 ms, so one 100 ms step is not allowed)
        var v = 0f
        repeat(25) { v = LensInertia.step(v, 100f, tau / 25f) }
        assertEquals(100f * (1f - Math.exp(-1.0).toFloat()), v, 0.05f)
        assertTrue("about 63%, was $v", v > 62f && v < 64f)
    }

    @Test fun stepConvergesMonotonicallyAndNeverOvershoots() {
        var v = 0f
        var previous = 0f
        repeat(200) {
            v = LensInertia.step(v, 100f, 16f)
            assertTrue("never past the target, was $v", v <= 100f)
            assertTrue("never moves away, $v < $previous", v >= previous)
            previous = v
        }
        assertEquals(100f, v, 0.01f)
    }

    @Test fun stepConvergesFromAboveToo() {
        var v = 500f
        repeat(200) { v = LensInertia.step(v, 100f, 16f); assertTrue(v >= 100f) }
        assertEquals(100f, v, 0.01f)
    }

    @Test fun stepIsFrameRateIndependent() {
        val one = LensInertia.step(0f, 100f, 32f)
        val two = LensInertia.step(LensInertia.step(0f, 100f, 16f), 100f, 16f)
        assertEquals(one, two, 0.001f)
    }

    @Test fun aZeroOrNegativeOrNonFiniteDtKeepsTheValue() {
        assertEquals(10f, LensInertia.step(10f, 100f, 0f), 0f)
        assertEquals(10f, LensInertia.step(10f, 100f, -5f), 0f)
        assertEquals(10f, LensInertia.step(10f, 100f, Float.NaN), 0f)
        assertEquals(10f, LensInertia.step(10f, 100f, Float.POSITIVE_INFINITY), 0f)
    }

    @Test fun aHitchIsClampedSoTheCentreCannotTeleport() {
        val hitch = LensInertia.step(0f, 100f, 5000f)
        val clamped = LensInertia.step(0f, 100f, LensInertia.MAX_DT_MS)
        assertEquals(clamped, hitch, 0.0001f)
        assertTrue("a 5 s hitch must not reach the target, was $hitch", hitch < 100f)
    }

    @Test fun nonFiniteInputNeverPoisonsTheCentre() {
        assertEquals(10f, LensInertia.step(10f, Float.NaN, 16f), 0f)
        assertEquals(10f, LensInertia.step(10f, Float.POSITIVE_INFINITY, 16f), 0f)
        assertEquals(100f, LensInertia.step(Float.NaN, 100f, 16f), 0f)
        assertEquals(100f, LensInertia.step(Float.NEGATIVE_INFINITY, 100f, 16f), 0f)
    }

    @Test fun settledMeansBothAxesWithinEpsilon() {
        assertTrue(LensInertia.isSettled(10f, 10f, 10f, 10f))
        assertTrue(LensInertia.isSettled(10f, 10f, 10.5f, 10.5f))
        assertFalse(LensInertia.isSettled(10f, 10f, 12f, 10f))
        assertFalse(LensInertia.isSettled(10f, 10f, 10f, 12f))
        assertFalse("non-finite is never settled so the loop cannot stall on garbage", LensInertia.isSettled(Float.NaN, 0f, 0f, 0f))
    }

    @Test fun sameCentreIsTighterThanSettled() {
        assertTrue(LensInertia.sameCentre(10f, 10f, 10f, 10f))
        assertTrue(LensInertia.sameCentre(10f, 10f, 10.3f, 10f))
        assertFalse(LensInertia.sameCentre(10f, 10f, 10.8f, 10f))
        assertFalse(LensInertia.sameCentre(Float.NaN, 0f, 0f, 0f))
        assertTrue(LensInertia.SAME_CENTRE_PX < LensInertia.EPSILON_PX)
    }

    @Test fun aSettledCentreCostsNothingExtra() {
        var vx = 0f
        var vy = 0f
        repeat(300) { vx = LensInertia.step(vx, 200f, 16f); vy = LensInertia.step(vy, 300f, 16f) }
        assertTrue(abs(vx - 200f) < LensInertia.SAME_CENTRE_PX)
        assertTrue(LensInertia.sameCentre(vx, vy, 200f, 300f))
    }
}
