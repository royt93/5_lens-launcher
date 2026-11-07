package com.mckimquyen.views

import com.mckimquyen.views.ChromaticAberration.TopFarthest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ChromaticAberrationTest {
    private val density = 3f
    private val zone = DepthOfField.FOCUS_ZONE

    @Test fun theCapIsEightAndTheOffsetRangeIsOneToThreeDp() {
        assertEquals(8, ChromaticAberration.MAX_ICONS)
        assertEquals(1f, ChromaticAberration.MIN_OFFSET_DP, 0f)
        assertEquals(3f, ChromaticAberration.MAX_OFFSET_DP, 0f)
    }

    @Test fun theEffectAppliesOnlyWhenEnabledAndTheLensIsVisible() {
        for (enabled in listOf(false, true)) for (visible in listOf(false, true)) {
            assertEquals("enabled=$enabled visible=$visible", enabled && visible,
                ChromaticAberration.shouldApply(enabled, visible))
        }
    }

    @Test fun thereIsNoOffsetInsideTheSharpZoneOrWithoutATransition() {
        assertEquals(0f, ChromaticAberration.offsetPx(0f, 1f, density), 0f)
        assertEquals(0f, ChromaticAberration.offsetPx(zone, 1f, density), 0f)
        assertEquals(0f, ChromaticAberration.offsetPx(1f, 0f, density), 0f)
    }

    @Test fun justOutsideTheZoneTheOffsetStartsAtOneDpAndTheFarthestIconGetsThreeDp() {
        assertEquals(1f * density, ChromaticAberration.offsetPx(zone + 0.0001f, 1f, density), 0.01f)
        assertEquals(3f * density, ChromaticAberration.offsetPx(1f, 1f, density), 0.001f)
    }

    @Test fun theOffsetNeverFallsAsTheDistanceGrows() {
        var previous = 0f
        var d = zone
        while (d <= 1.0001f) {
            val o = ChromaticAberration.offsetPx(d, 1f, density)
            assertTrue("offset $o at $d must not drop below $previous", o >= previous)
            previous = o
            d += 0.02f
        }
    }

    @Test fun theOffsetScalesWithDensityAndTheTransitionAndClampsOutOfRangeInput() {
        assertEquals(2f * ChromaticAberration.offsetPx(0.6f, 1f, 2f), ChromaticAberration.offsetPx(0.6f, 1f, 4f), 0.001f)
        assertEquals(0.5f * ChromaticAberration.offsetPx(0.6f, 1f, density), ChromaticAberration.offsetPx(0.6f, 0.5f, density), 0.001f)
        assertEquals(ChromaticAberration.offsetPx(1f, 1f, density), ChromaticAberration.offsetPx(9f, 1f, density), 0f)
        assertEquals(ChromaticAberration.offsetPx(0.6f, 1f, density), ChromaticAberration.offsetPx(0.6f, 7f, density), 0f)
        assertEquals(0f, ChromaticAberration.offsetPx(-3f, 1f, density), 0f)
    }

    @Test fun nonFiniteInputProducesNoOffset() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(0f, ChromaticAberration.offsetPx(bad, 1f, density), 0f)
            assertEquals(0f, ChromaticAberration.offsetPx(0.8f, bad, density), 0f)
            assertEquals(0f, ChromaticAberration.offsetPx(0.8f, 1f, bad), 0f)
        }
        assertEquals(0f, ChromaticAberration.offsetPx(0.8f, 1f, 0f), 0f)
        assertEquals(0f, ChromaticAberration.offsetPx(0.8f, 1f, -2f), 0f)
    }

    @Test fun theDirectionIsAUnitVectorFromTheTouchToTheCell() {
        val out = FloatArray(2)
        ChromaticAberration.direction(100f, 100f, 400f, 500f, out)
        assertEquals(1f, sqrt(out[0] * out[0] + out[1] * out[1]), 1e-5f)
        assertEquals(0.6f, out[0], 1e-5f)
        assertEquals(0.8f, out[1], 1e-5f)
        ChromaticAberration.direction(100f, 100f, 0f, 100f, out)
        assertEquals(-1f, out[0], 1e-5f)
        assertEquals(0f, out[1], 1e-5f)
    }

    @Test fun theDirectionIsZeroWhenTheCellIsOnTheTouchOrAnInputIsNotFinite() {
        val out = floatArrayOf(7f, 7f)
        ChromaticAberration.direction(50f, 60f, 50f, 60f, out)
        assertEquals(0f, out[0], 0f); assertEquals(0f, out[1], 0f)
        out[0] = 7f; out[1] = 7f
        ChromaticAberration.direction(Float.NaN, 60f, 5f, 6f, out)
        assertEquals(0f, out[0], 0f); assertEquals(0f, out[1], 0f)
    }

    @Test fun anIconIsFringedOnlyOutsideTheZoneAndInDofBandsZeroAndOne() {
        assertFalse(ChromaticAberration.isFringed(zone, 0))
        assertTrue(ChromaticAberration.isFringed(zone + 0.01f, 0))
        assertTrue(ChromaticAberration.isFringed(0.9f, 1))
        assertFalse("band 2 is blurred, a fringe would fight the blur", ChromaticAberration.isFringed(0.9f, 2))
        assertFalse(ChromaticAberration.isFringed(Float.NaN, 0))
        assertFalse(ChromaticAberration.isFringed(0.9f, -1))
    }

    private fun selected(top: TopFarthest) = (0 until top.count).map { top.indexAt(it) }.sorted()

    @Test fun topFarthestKeepsTheLargestDistancesInAnyInsertionOrder() {
        for (order in listOf(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), listOf(9, 8, 7, 6, 5, 4, 3, 2, 1, 0), listOf(4, 9, 0, 5, 2, 7, 1, 8, 3, 6))) {
            val top = TopFarthest(3)
            order.forEach { top.offer(it / 10f, it) }
            assertEquals(order.toString(), listOf(7, 8, 9), selected(top))
            assertEquals(3, top.count)
        }
    }

    @Test fun topFarthestWithFewerEntriesThanCapacityKeepsAll() {
        val top = TopFarthest(8)
        top.offer(0.5f, 11); top.offer(0.3f, 12)
        assertEquals(2, top.count)
        assertEquals(listOf(11, 12), selected(top))
    }

    @Test fun topFarthestIgnoresNonFiniteAndNeverExceedsCapacity() {
        val top = TopFarthest(2)
        assertEquals(-1, top.offer(Float.NaN, 1))
        assertEquals(-1, top.offer(Float.POSITIVE_INFINITY, 2))
        assertEquals(0, top.count)
        repeat(50) { top.offer(it / 50f, it) }
        assertEquals(2, top.count)
        assertEquals(listOf(48, 49), selected(top))
    }

    @Test fun offerReturnsAStableSlotForKeptEntriesAndMinusOneForRejectedOnes() {
        val top = TopFarthest(2)
        val a = top.offer(0.5f, 100)
        val b = top.offer(0.6f, 101)
        assertTrue(a in 0..1 && b in 0..1 && a != b)
        assertEquals(-1, top.offer(0.1f, 102))
        assertEquals(100, top.indexAt(a)); assertEquals(0.5f, top.distanceAt(a), 0f)
        assertEquals(101, top.indexAt(b)); assertEquals(0.6f, top.distanceAt(b), 0f)
    }

    @Test fun aDisplacedSlotIsReusedByTheFartherEntry() {
        val top = TopFarthest(2)
        val low = top.offer(0.2f, 1)
        top.offer(0.9f, 2)
        val reused = top.offer(0.5f, 3)
        assertEquals("the slot of the nearest displaced entry is reused", low, reused)
        assertEquals(3, top.indexAt(reused))
        assertEquals(listOf(2, 3), selected(top))
    }

    @Test fun equalDistancesDoNotDisplaceAnEarlierEqualEntry() {
        val top = TopFarthest(2)
        top.offer(0.5f, 1); top.offer(0.5f, 2)
        assertEquals(-1, top.offer(0.5f, 3))
        assertEquals(listOf(1, 2), selected(top))
    }

    @Test fun resetEmptiesTheSelectorAndAllowsReuse() {
        val top = TopFarthest(2)
        top.offer(0.5f, 1); top.offer(0.6f, 2)
        top.reset()
        assertEquals(0, top.count)
        top.offer(0.1f, 9)
        assertEquals(listOf(9), selected(top))
    }

    @Test fun aCapacityBelowOneIsRejected() {
        assertTrue(runCatching { TopFarthest(0) }.isFailure)
        assertTrue(runCatching { TopFarthest(-1) }.isFailure)
    }
}
