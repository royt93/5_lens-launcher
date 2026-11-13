package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeighbourLabelsTest {
    private val out = IntArray(NeighbourLabels.MAX_NEIGHBOURS)

    @Test fun theConstantsAreTheDesignedValues() {
        assertEquals(2, NeighbourLabels.MAX_NEIGHBOURS)
        assertEquals(140, NeighbourLabels.ALPHA)
        assertEquals(8f, NeighbourLabels.GAP_DP, 0f)
    }

    @Test fun theEffectNeedsTheSwitchTheNameSettingAndARealPanOutsideCleanMode() {
        for (enabled in listOf(false, true)) for (nameShown in listOf(false, true)) for (clean in listOf(false, true)) for (moving in listOf(false, true)) {
            assertEquals(
                "enabled=$enabled nameShown=$nameShown clean=$clean moving=$moving",
                enabled && nameShown && !clean && moving,
                NeighbourLabels.shouldApply(enabled, nameShown, clean, moving)
            )
        }
    }

    @Test fun aMiddleCellHasBothSameRowNeighbours() {
        val n = NeighbourLabels.neighbours(selected = 5, cols = 4, count = 12, out = out)
        assertEquals(2, n)
        assertEquals(setOf(4, 6), out.take(n).toSet())
    }

    @Test fun theFirstColumnHasOnlyARightNeighbourAndTheLastOnlyALeftOne() {
        assertEquals(listOf(1), out.take(NeighbourLabels.neighbours(0, 4, 12, out)))
        assertEquals(listOf(6), out.take(NeighbourLabels.neighbours(7, 4, 12, out)))
    }

    @Test fun neverCrossesIntoTheNextRow() {
        // cell 3 is the last of row 0 (cols = 4); cell 4 is the first of row 1 and must not be its neighbour
        val n = NeighbourLabels.neighbours(3, 4, 12, out)
        assertEquals(listOf(2), out.take(n))
        val m = NeighbourLabels.neighbours(4, 4, 12, out)
        assertEquals(listOf(5), out.take(m))
    }

    @Test fun anUnfilledLastRowDoesNotInventAnAppPastTheCount() {
        // 10 apps in 4 columns: row 2 holds cells 8 and 9 only; cell 9's right neighbour would be 10
        val n = NeighbourLabels.neighbours(9, 4, 10, out)
        assertEquals(listOf(8), out.take(n))
    }

    @Test fun degenerateInputsGiveNoNeighbours() {
        assertEquals(0, NeighbourLabels.neighbours(-1, 4, 12, out))
        assertEquals(0, NeighbourLabels.neighbours(12, 4, 12, out))
        assertEquals(0, NeighbourLabels.neighbours(0, 0, 12, out))
        assertEquals(0, NeighbourLabels.neighbours(0, 1, 1, out))
        assertEquals(0, NeighbourLabels.neighbours(0, 4, 0, out))
    }

    @Test fun oneColumnHasNoSameRowNeighbours() {
        assertEquals(0, NeighbourLabels.neighbours(3, 1, 12, out))
    }

    @Test fun aLabelFitsWhenTheCentresAreAtLeastHalfBothWidthsPlusTheGapApart() {
        // half widths 100 + 60, gap 10 -> needs 170
        assertTrue(NeighbourLabels.fits(0f, 170f, 200f, 120f, 10f))
        assertTrue(NeighbourLabels.fits(0f, -170f, 200f, 120f, 10f))
        assertFalse(NeighbourLabels.fits(0f, 169.9f, 200f, 120f, 10f))
        assertFalse(NeighbourLabels.fits(0f, 0f, 200f, 120f, 10f))
    }

    @Test fun nonFiniteOrNegativeInputsNeverFit() {
        assertFalse(NeighbourLabels.fits(Float.NaN, 500f, 100f, 100f, 8f))
        assertFalse(NeighbourLabels.fits(0f, Float.POSITIVE_INFINITY, 100f, 100f, 8f))
        assertFalse(NeighbourLabels.fits(0f, 500f, -1f, 100f, 8f))
        assertFalse(NeighbourLabels.fits(0f, 500f, 100f, Float.NaN, 8f))
    }
}
