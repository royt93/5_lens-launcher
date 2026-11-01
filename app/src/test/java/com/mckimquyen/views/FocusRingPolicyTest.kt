package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** FISH-031: every decision of the shortcut ring, as pure functions. */
class FocusRingPolicyTest {
    @Test fun ringNeedsShortcutsAndCleanModeOff() {
        assertTrue(FocusRingPolicy.shouldShowRing(cleanMode = false, shortcutCount = 1))
        assertTrue(FocusRingPolicy.shouldShowRing(cleanMode = false, shortcutCount = 3))
        assertFalse(FocusRingPolicy.shouldShowRing(cleanMode = false, shortcutCount = 0))
        assertFalse(FocusRingPolicy.shouldShowRing(cleanMode = true, shortcutCount = 3))
        assertFalse(FocusRingPolicy.shouldShowRing(cleanMode = true, shortcutCount = 0))
    }

    @Test fun reducedMotionSuppressesTheExtraScale() {
        assertTrue(FocusRingPolicy.shouldSuppressExtraScale(reduceMotion = true))
        assertFalse(FocusRingPolicy.shouldSuppressExtraScale(reduceMotion = false))
    }

    @Test fun dragStartsOnlyOffAChipPastSlopWithOtherLenses() {
        assertTrue(FocusRingPolicy.shouldAllowDragStart(hasOtherLenses = true, activeChipIndex = -1, movedPastSlop = true))
        assertFalse(FocusRingPolicy.shouldAllowDragStart(hasOtherLenses = true, activeChipIndex = 0, movedPastSlop = true))
        assertFalse(FocusRingPolicy.shouldAllowDragStart(hasOtherLenses = false, activeChipIndex = -1, movedPastSlop = true))
        assertFalse(FocusRingPolicy.shouldAllowDragStart(hasOtherLenses = true, activeChipIndex = -1, movedPastSlop = false))
    }

    @Test fun releaseOnAChipRunsThatShortcut() {
        for (i in 0..2) {
            assertEquals(FocusRingReleaseAction.RunShortcut(i), FocusRingPolicy.resolveRelease(true, i, false))
        }
    }

    @Test fun releaseOffAChipOpensTheAppMenu() {
        assertEquals(FocusRingReleaseAction.OpenAppMenu, FocusRingPolicy.resolveRelease(true, -1, false))
    }

    @Test fun releaseAfterADragDoesNothing() {
        assertEquals(FocusRingReleaseAction.None, FocusRingPolicy.resolveRelease(true, 1, true))
        assertEquals(FocusRingReleaseAction.None, FocusRingPolicy.resolveRelease(false, -1, true))
    }

    @Test fun aStaleChipIndexIsIgnoredWhenNoRingIsActive() {
        assertEquals(FocusRingReleaseAction.OpenAppMenu, FocusRingPolicy.resolveRelease(false, 3, false))
    }
}
