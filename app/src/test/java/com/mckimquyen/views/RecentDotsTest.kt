package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentDotsTest {
    private val density = 3f

    @Test fun theConstantsAreTheDesignedValues() {
        assertEquals(3, RecentDots.MAX_DOTS)
        assertEquals(24L * 60 * 60 * 1000, RecentDots.WINDOW_MS)
        assertEquals(0.12f, RecentDots.FRACTION, 0f)
        assertEquals(1.5f, RecentDots.MIN_DP, 0f)
        assertEquals(4f, RecentDots.MAX_DP, 0f)
    }

    @Test fun theEffectNeedsTheSwitchNotCleanModeARealDragAndTheAppsLens() {
        for (enabled in listOf(false, true)) for (clean in listOf(false, true)) for (dragging in listOf(false, true)) for (visible in listOf(false, true)) {
            assertEquals(
                "enabled=$enabled clean=$clean dragging=$dragging visible=$visible",
                enabled && !clean && dragging && visible,
                RecentDots.shouldApply(enabled, clean, dragging, visible)
            )
        }
    }

    @Test fun aSmallIconGetsTheMinimumRadius() {
        // 17 px icon: 12% = 2.04 px, below 1.5 dp = 4.5 px
        assertEquals(1.5f * density, RecentDots.radiusPx(17f, density), 1e-4f)
    }

    @Test fun aMidIconScalesWithItsWidth() {
        // 54 px: 12% = 6.48 px, between 4.5 px and 12 px
        assertEquals(0.12f * 54f, RecentDots.radiusPx(54f, density), 1e-4f)
    }

    @Test fun aBigIconIsCappedAtTheMaximumRadius() {
        assertEquals(4f * density, RecentDots.radiusPx(168f, density), 1e-4f)
        assertEquals(4f * density, RecentDots.radiusPx(10_000f, density), 1e-4f)
    }

    @Test fun theTwoBoundsAreReachedExactlyAtTheCrossoverWidths() {
        val minWidth = RecentDots.MIN_DP * density / RecentDots.FRACTION     // 37.5 px
        val maxWidth = RecentDots.MAX_DP * density / RecentDots.FRACTION     // 100 px
        assertEquals(RecentDots.MIN_DP * density, RecentDots.radiusPx(minWidth, density), 1e-3f)
        assertEquals(RecentDots.MAX_DP * density, RecentDots.radiusPx(maxWidth, density), 1e-3f)
        assertTrue(RecentDots.radiusPx(minWidth + 1f, density) > RecentDots.MIN_DP * density)
        assertTrue(RecentDots.radiusPx(maxWidth - 1f, density) < RecentDots.MAX_DP * density)
    }

    @Test fun nonFiniteOrNonPositiveInputsGiveNoDot() {
        for (w in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, 0f)) {
            assertEquals("width=$w", 0f, RecentDots.radiusPx(w, density), 0f)
        }
        for (d in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f)) {
            assertEquals("density=$d", 0f, RecentDots.radiusPx(54f, d), 0f)
        }
    }

    @Test fun theRadiusNeverExceedsHalfTheIconSoTheDotCannotCoverIt() {
        for (w in listOf(6f, 9f, 12f, 17f, 21f, 27f, 38f)) {
            assertTrue("width=$w radius=${RecentDots.radiusPx(w, density)}", RecentDots.radiusPx(w, density) <= w / 2f)
        }
        assertFalse(RecentDots.radiusPx(54f, density) > 54f / 2f)
    }
}
