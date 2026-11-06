package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPeakingTest {
    @Test fun strokeIsFourDp() = assertEquals(4f, FocusPeaking.STROKE_DP, 0f)

    @Test fun anIconAtTheFocusPointAndOnTheZoneEdgeIsSharp() {
        assertTrue(FocusPeaking.isSharp(0f))
        assertTrue(FocusPeaking.isSharp(DepthOfField.FOCUS_ZONE))
    }

    @Test fun anIconJustOutsideTheZoneOrFarAwayIsNotSharp() {
        assertFalse(FocusPeaking.isSharp(DepthOfField.FOCUS_ZONE + 0.001f))
        assertFalse(FocusPeaking.isSharp(1f))
    }

    @Test fun nonFiniteDistanceIsNeverSharp() {
        assertFalse(FocusPeaking.isSharp(Float.NaN))
        assertFalse(FocusPeaking.isSharp(Float.POSITIVE_INFINITY))
        assertFalse(FocusPeaking.isSharp(Float.NEGATIVE_INFINITY))
    }

    @Test fun theOutlineAppliesOnlyWhenEnabledWithAFocusCentreOnTheAppsLens() {
        for (enabled in listOf(false, true)) for (centre in listOf(false, true)) for (visible in listOf(false, true)) {
            val expected = enabled && centre && visible
            assertEquals("enabled=$enabled centre=$centre visible=$visible", expected,
                FocusPeaking.shouldApply(enabled, centre, visible))
        }
    }

    @Test fun aPinnedFocusWithoutATouchIsFullyOpaqueWhateverTheTransition() {
        assertEquals(255, FocusPeaking.alpha(pinnedOnly = true, transition = 0f))
        assertEquals(255, FocusPeaking.alpha(pinnedOnly = true, transition = 0.5f))
        assertEquals(255, FocusPeaking.alpha(pinnedOnly = true, transition = Float.NaN))
    }

    @Test fun aLiveTouchFollowsTheLensTransition() {
        assertEquals(0, FocusPeaking.alpha(pinnedOnly = false, transition = 0f))
        assertEquals(128, FocusPeaking.alpha(pinnedOnly = false, transition = 0.5f))
        assertEquals(255, FocusPeaking.alpha(pinnedOnly = false, transition = 1f))
    }

    @Test fun theTransitionIsClampedAndNonFiniteHidesTheOutline() {
        assertEquals(0, FocusPeaking.alpha(pinnedOnly = false, transition = -2f))
        assertEquals(255, FocusPeaking.alpha(pinnedOnly = false, transition = 9f))
        assertEquals(0, FocusPeaking.alpha(pinnedOnly = false, transition = Float.NaN))
        assertEquals(0, FocusPeaking.alpha(pinnedOnly = false, transition = Float.POSITIVE_INFINITY))
    }

    @Test fun theTouchAlphaNeverFallsAsTheTransitionRises() {
        var previous = -1
        var t = 0f
        while (t <= 1.0001f) {
            val a = FocusPeaking.alpha(pinnedOnly = false, transition = t)
            assertTrue("alpha $a at $t must not drop below $previous", a >= previous)
            previous = a
            t += 0.05f
        }
    }
}
