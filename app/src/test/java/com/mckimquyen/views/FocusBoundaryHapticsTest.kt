package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusBoundaryHapticsTest {
    private val tracker = FocusBoundaryHaptics.Tracker()

    @Test fun hysteresisThresholdsStraddleTheFocusZone() {
        assertEquals(0.18f, FocusBoundaryHaptics.ENTER_DISTANCE, 0f)
        assertEquals(0.22f, FocusBoundaryHaptics.EXIT_DISTANCE, 0f)
        assertEquals(0.02f, FocusBoundaryHaptics.HYSTERESIS, 0f)
    }

    @Test fun firstSampleEstablishesTheBaselineWithoutPulse() {
        assertFalse(tracker.observe("pkg/A", 0.1f))
        assertFalse(tracker.observe("pkg/B", 0.3f))
    }

    @Test fun enteringAndLeavingSharpZonePulseOnceEachDirection() {
        assertFalse(tracker.observe("pkg/A", 0.3f))
        assertTrue(tracker.observe("pkg/A", 0.17f))
        assertFalse(tracker.observe("pkg/A", 0.1f))
        assertTrue(tracker.observe("pkg/A", 0.23f))
        assertFalse(tracker.observe("pkg/A", 0.3f))
    }

    @Test fun samplesInsideHysteresisBandKeepThePreviousState() {
        assertFalse(tracker.observe("pkg/A", 0.3f))
        assertTrue(tracker.observe("pkg/A", 0.17f))
        assertFalse(tracker.observe("pkg/A", 0.19f))
        assertFalse(tracker.observe("pkg/A", 0.21f))
        assertTrue(tracker.observe("pkg/A", 0.23f))
        assertFalse(tracker.observe("pkg/A", 0.21f))
        assertFalse(tracker.observe("pkg/A", 0.19f))
        assertTrue(tracker.observe("pkg/A", 0.17f))
    }

    @Test fun changingSelectedAppRebaselinesWithoutPulse() {
        assertFalse(tracker.observe("pkg/A", 0.3f))
        assertFalse(tracker.observe("pkg/B", 0.1f))
        assertFalse(tracker.observe("pkg/B", 0.17f))
        assertTrue(tracker.observe("pkg/B", 0.23f))
    }

    @Test fun nonFiniteDistanceDoesNotChangeStateOrPulse() {
        assertFalse(tracker.observe("pkg/A", 0.3f))
        assertFalse(tracker.observe("pkg/A", Float.NaN))
        assertFalse(tracker.observe("pkg/A", Float.POSITIVE_INFINITY))
        assertTrue(tracker.observe("pkg/A", 0.17f))
    }

    @Test fun resetMakesTheNextSampleANewBaseline() {
        assertFalse(tracker.observe("pkg/A", 0.3f))
        assertTrue(tracker.observe("pkg/A", 0.17f))
        tracker.reset()
        assertFalse(tracker.observe("pkg/A", 0.1f))
    }
    @Test fun theBoundaryPulseIsAlwaysLightWhateverTheUserChoseForOrdinaryHaptics() {
        for (chosen in com.mckimquyen.util.HapticIntensity.entries) {
            assertEquals("user chose $chosen", com.mckimquyen.util.HapticIntensity.LIGHT, FocusBoundaryHaptics.pulseIntensity(chosen))
        }
    }
    /** Without a real wipe, the next sample of the SAME app would be read as outside -> inside and pulse. */
    @Test fun resetReallyForgetsTheSameAppSoItsNextSampleCannotPulse() {
        assertFalse(tracker.observe("pkg/A", 0.3f))     // baseline: outside
        tracker.reset()
        assertFalse("after reset the same app is a fresh baseline, not a crossing", tracker.observe("pkg/A", 0.1f))
    }

    @Test fun resetAlsoForgetsTheInsideState() {
        assertFalse(tracker.observe("pkg/A", 0.1f))     // baseline: inside
        tracker.reset()
        assertFalse(tracker.observe("pkg/A", 0.3f))     // fresh baseline: outside, no exit pulse
        assertTrue("and now a real entry is detected from that new baseline", tracker.observe("pkg/A", 0.1f))
    }

    /** A NaN in the middle must neither pulse nor corrupt the state: the next finite sample behaves as if it never happened. */
    @Test fun aNonFiniteSampleBetweenTwoFiniteOnesChangesNothing() {
        assertFalse(tracker.observe("pkg/A", 0.3f))
        assertFalse(tracker.observe("pkg/A", Float.NaN))
        assertFalse("NaN must not re-baseline the app", tracker.observe("pkg/A", Float.NaN))
        assertTrue("the crossing is still detected afterwards", tracker.observe("pkg/A", 0.1f))
        assertFalse(tracker.observe("pkg/A", Float.NEGATIVE_INFINITY))
        assertTrue("and leaving is still detected", tracker.observe("pkg/A", 0.5f))
    }

    @Test fun aNonFiniteFirstSampleDoesNotEstablishABaseline() {
        assertFalse(tracker.observe("pkg/A", Float.NaN))
        assertFalse("the first FINITE sample is the baseline", tracker.observe("pkg/A", 0.1f))
        assertTrue(tracker.observe("pkg/A", 0.5f))
    }
}
