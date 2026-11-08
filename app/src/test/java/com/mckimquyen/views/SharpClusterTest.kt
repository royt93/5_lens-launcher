package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharpClusterTest {
    private val cells = 6
    private val tracker = SharpCluster.Tracker(cells)

    /** One frame: cell i is at distances[i]; returns whether a pulse fires at [nowMs]. */
    private fun frame(nowMs: Long, vararg distances: Float): Boolean {
        tracker.beginFrame()
        distances.forEachIndexed { i, d -> tracker.markCell(i, d) }
        return tracker.endFrame(nowMs)
    }

    private val far = 0.5f
    private val near = 0.05f

    @Test fun theConstantsAreTheDesignedValues() {
        assertEquals(0.18f, SharpCluster.ENTER_DISTANCE, 0f)
        assertEquals(0.22f, SharpCluster.EXIT_DISTANCE, 0f)
        assertEquals(120L, SharpCluster.MIN_PULSE_GAP_MS)
    }

    @Test fun theFirstFrameIsAlwaysABaselineAndNeverPulses() {
        assertFalse(frame(0, near, near, far, far, far, far))
        assertEquals(2, tracker.count)
    }

    @Test fun aClusterThatGrowsPulsesOnce() {
        frame(0, near, far, far, far, far, far)
        assertTrue("cluster 1 -> 2", frame(1_000, near, near, far, far, far, far))
        assertEquals(2, tracker.count)
    }

    @Test fun aClusterThatShrinksPulsesOnce() {
        frame(0, near, near, near, far, far, far)
        assertTrue("cluster 3 -> 2", frame(1_000, near, near, far, far, far, far))
        assertEquals(2, tracker.count)
    }

    @Test fun anUnchangedClusterNeverPulses() {
        frame(0, near, near, far, far, far, far)
        assertFalse(frame(1_000, near, near, far, far, far, far))
        assertFalse(frame(2_000, 0.01f, 0.1f, far, far, far, far))
    }

    @Test fun severalCellsChangingInOneFrameGiveExactlyOnePulse() {
        frame(0, far, far, far, far, far, far)
        assertTrue(frame(1_000, near, near, near, near, far, far))
        assertEquals(4, tracker.count)
        assertFalse("the next identical frame is not a second pulse", frame(2_000, near, near, near, near, far, far))
    }

    @Test fun aCellBetweenTheTwoThresholdsKeepsItsPreviousState() {
        frame(0, 0.1f, far, far, far, far, far)                       // cell 0 is in
        assertFalse("0.19 is between 0.18 and 0.22, stays in", frame(1_000, 0.19f, far, far, far, far, far))
        assertFalse("0.21 is still between, stays in", frame(2_000, 0.21f, far, far, far, far, far))
        assertEquals(1, tracker.count)
        assertTrue("0.23 is past the exit threshold", frame(3_000, 0.23f, far, far, far, far, far))
        assertEquals(0, tracker.count)
        assertFalse("0.21 again: stays out", frame(4_000, 0.21f, far, far, far, far, far))
        assertFalse("0.19 again: stays out", frame(5_000, 0.19f, far, far, far, far, far))
        assertTrue("0.17 is inside the enter threshold", frame(6_000, 0.17f, far, far, far, far, far))
        assertEquals(1, tracker.count)
    }

    @Test fun twoPulsesCloserThan120MsAreMergedIntoOne() {
        frame(0, near, far, far, far, far, far)
        assertTrue(frame(1_000, near, near, far, far, far, far))
        assertFalse("119 ms later: dropped", frame(1_119, near, near, near, far, far, far))
    }

    @Test fun aPulseExactly120MsAfterTheLastIsAllowed() {
        frame(0, near, far, far, far, far, far)
        assertTrue(frame(1_000, near, near, far, far, far, far))
        assertTrue("120 ms later: allowed", frame(1_120, near, near, near, far, far, far))
    }

    @Test fun aChangeDroppedInsideTheWindowMovesTheBaselineSoNoLatePulseAppears() {
        frame(0, near, far, far, far, far, far)
        assertTrue(frame(1_000, near, near, far, far, far, far))               // pulse, count 2
        assertFalse(frame(1_050, near, near, near, far, far, far))             // dropped, count 3
        assertEquals(3, tracker.count)
        assertFalse("same size again after the window: nothing to report", frame(1_500, near, near, near, far, far, far))
    }

    @Test fun nonFiniteDistancesAreIgnoredAndNeverCountedAsInside() {
        frame(0, far, far, far, far, far, far)
        assertFalse(frame(1_000, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, far, far, far))
        assertEquals(0, tracker.count)
        assertTrue("a real cell going near afterwards is still detected", frame(2_000, near, far, far, far, far, far))
    }

    @Test fun resetMakesTheNextFrameANewBaseline() {
        frame(0, near, far, far, far, far, far)
        tracker.reset()
        assertFalse("after reset the same frame is a baseline, not a change", frame(1_000, near, near, far, far, far, far))
        assertEquals(2, tracker.count)
    }

    @Test fun resetAlsoForgetsThePulseClockSoANewGestureIsNotThrottledByTheOldOne() {
        frame(0, near, far, far, far, far, far)
        assertTrue(frame(1_000, near, near, far, far, far, far))
        tracker.reset()
        frame(1_010, near, far, far, far, far, far)                              // baseline, 10 ms after the last pulse
        assertTrue("a change 10 ms into the new gesture is not held back by the old gesture", frame(1_020, near, near, far, far, far, far))
    }

    @Test fun aMarkOutsideTheGridIsIgnoredNotACrash() {
        tracker.beginFrame()
        tracker.markCell(-1, near)
        tracker.markCell(cells, near)
        tracker.markCell(0, near)
        assertFalse(tracker.endFrame(0))
        assertEquals(1, tracker.count)
    }

    @Test fun growingTheGridKeepsTheStateWithoutAPulse() {
        frame(0, near, near, far, far, far, far)
        tracker.resize(cells + 4)
        tracker.beginFrame()
        listOf(near, near, far, far, far, far, far, far, far, far).forEachIndexed { i, d -> tracker.markCell(i, d) }
        assertFalse("a bigger grid with the same cluster is not a change", tracker.endFrame(1_000))
        assertEquals(2, tracker.count)
    }

    @Test fun anEmptyFrameHasAZeroCluster() {
        frame(0, near, far, far, far, far, far)
        tracker.beginFrame()
        assertTrue("cluster 1 -> 0 is a change", tracker.endFrame(1_000))
        assertEquals(0, tracker.count)
    }
    @Test fun everyVisibleSizeChangeHappensAtMostOncePerFrame() {
        // Several cells enter simultaneously: one boolean decision per rendered frame, regardless of changed-cell count.
        frame(0, far, far, far, far, far, far)
        assertTrue(frame(500, near, near, near, near, far, far))
        assertFalse(frame(510, near, near, near, near, far, far))
    }

    @Test fun aClockResetStartsANewThrottleWindow() {
        frame(10_000, near, far, far, far, far, far)
        assertTrue(frame(10_100, near, near, far, far, far, far))
        tracker.reset()
        frame(2, near, far, far, far, far, far)
        assertTrue("monotonic time of a new gesture is still allowed", frame(3, near, near, far, far, far, far))
    }
    @Test fun theBoundaryPulseIsAlwaysLightWhateverTheUserChoseForOrdinaryHaptics() {
        for (chosen in com.mckimquyen.util.HapticIntensity.entries) {
            assertEquals("user chose $chosen", com.mckimquyen.util.HapticIntensity.LIGHT, SharpCluster.pulseIntensity(chosen))
        }
    }

    /**
     * A cell that is inside the cluster only because of hysteresis (distance 0.19, between the thresholds) must keep that
     * state across a resize. If resize wiped the per-cell memory, the next frame would read it as outside and the cluster
     * would appear to shrink, producing a pulse for a change that never happened.
     */
    @Test fun aResizeKeepsTheHysteresisMemoryOfCellsThatStillExist() {
        frame(0, 0.1f, far, far, far, far, far)                          // cell 0 joins the cluster
        tracker.resize(cells + 4)
        tracker.beginFrame()
        tracker.markCell(0, 0.19f)                                        // between the thresholds: must stay in
        for (i in 1 until cells + 4) tracker.markCell(i, far)
        assertFalse("no change in cluster size, no pulse", tracker.endFrame(1_000))
        assertEquals("cell 0 kept its inside state through the resize", 1, tracker.count)
    }
}
