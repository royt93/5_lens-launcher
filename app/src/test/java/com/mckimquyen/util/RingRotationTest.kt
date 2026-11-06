package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import com.mckimquyen.enums.SortType
import com.mckimquyen.model.App
import com.mckimquyen.model.PinnedZone
import org.junit.Test
import kotlin.math.PI

class RingRotationTest {
    @Test fun rotateForwardMovesTheFirstItemToTheEnd() =
        assertEquals(listOf("b", "c", "d", "a"), RingRotation.rotate(listOf("a", "b", "c", "d"), 1))

    @Test fun rotateBackMovesTheLastItemToTheFront() =
        assertEquals(listOf("d", "a", "b", "c"), RingRotation.rotate(listOf("a", "b", "c", "d"), -1))

    @Test fun rotateWrapsAndIgnoresFullTurns() {
        val l = listOf(1, 2, 3, 4)
        assertEquals(l, RingRotation.rotate(l, 4))
        assertEquals(RingRotation.rotate(l, 1), RingRotation.rotate(l, 5))
        assertEquals(RingRotation.rotate(l, -1), RingRotation.rotate(l, 7))
        assertEquals(RingRotation.rotate(l, 3), RingRotation.rotate(l, -9))
    }

    @Test fun rotateIsSafeForEmptyAndSingleAndNeverMutates() {
        assertEquals(emptyList<Int>(), RingRotation.rotate(emptyList<Int>(), 3))
        assertEquals(listOf(9), RingRotation.rotate(listOf(9), 5))
        val input = mutableListOf(1, 2, 3)
        val out = RingRotation.rotate(input, 1)
        assertEquals(listOf(1, 2, 3), input)
        assertNotSame(input, out)
    }

    @Test fun rotateByGroupRotatesEachGroupAndNeverCrossesABoundary() {
        // key = first digit: group 1 = [10, 11, 12], group 2 = [20, 21]
        val items = listOf(10, 11, 12, 20, 21)
        val out = RingRotation.rotateByGroup(items, 1) { it / 10 }
        assertEquals(listOf(11, 12, 10, 21, 20), out)
        // back one step: group 1 -> [12,10,11], group 2 -> [21,20]
        assertEquals(listOf(12, 10, 11, 21, 20), RingRotation.rotateByGroup(items, -1) { it / 10 })
    }

    @Test fun rotateByGroupOrdersGroupsByAscendingKeyLikeTheSorter() {
        // input is interleaved (Smart Focus arrangement); output is grouped ascending, like a reload
        val items = listOf(20, 10, 21, 11)
        assertEquals(listOf(10, 11, 20, 21), RingRotation.rotateByGroup(items, 0) { it / 10 })
    }

    @Test fun rotateByGroupLeavesSingletonGroupsAndFullTurnsUnchanged() {
        val items = listOf(10, 20, 21)
        assertEquals(listOf(10, 20, 21), RingRotation.rotateByGroup(items, 2) { it / 10 })
        assertEquals(emptyList<Int>(), RingRotation.rotateByGroup(emptyList<Int>(), 1) { it })
    }

    @Test fun wholeStepsTruncateTowardZeroAndRejectNonFinite() {
        val s = RingRotation.STEP_RADIANS
        assertEquals(0, RingRotation.wholeSteps(s * 0.99f))
        assertEquals(1, RingRotation.wholeSteps(s * 1.01f))
        assertEquals(-1, RingRotation.wholeSteps(-s * 1.99f))
        assertEquals(0, RingRotation.wholeSteps(Float.NaN))
        assertEquals(0, RingRotation.wholeSteps(Float.POSITIVE_INFINITY))
    }

    @Test fun shortestAngleDeltaWrapsAcrossPiAndRejectsNonFinite() {
        assertEquals(0.2f, RingRotation.shortestAngleDelta((PI - 0.1).toFloat(), (-PI + 0.1).toFloat()), 1e-4f)
        assertEquals(-0.2f, RingRotation.shortestAngleDelta((-PI + 0.1).toFloat(), (PI - 0.1).toFloat()), 1e-4f)
        assertEquals(0f, RingRotation.shortestAngleDelta(1f, 1f), 0f)
        assertEquals(0f, RingRotation.shortestAngleDelta(Float.NaN, 1f), 0f)
        assertEquals(0f, RingRotation.shortestAngleDelta(0f, Float.POSITIVE_INFINITY), 0f)
        // a huge but finite jump must terminate and stay in range
        val huge = RingRotation.shortestAngleDelta(0f, 1.0e9f)
        assertTrue(huge > -PI.toFloat() - 1e-3f && huge <= PI.toFloat() + 1e-3f)
    }

    @Test fun groupKeyFollowsTheSorterMajorAndMinorOrder() {
        val keys = listOf(
            PinnedZone.START to true, PinnedZone.START to false,
            PinnedZone.NONE to true, PinnedZone.NONE to false,
            PinnedZone.END to true, PinnedZone.END to false
        ).map { (zone, fav) -> RingRotation.groupKeyOf(zone.rank, fav) }
        assertEquals(keys.sorted(), keys)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun aRotatedOrderSurvivesTheRealSorterAfterAReload() {
        fun app(name: String, zone: PinnedZone, fav: Boolean) =
            App(label = name, packageName = "pkg.$name", name = "Main", pinnedZone = zone, isFavorite = fav)
        val displayed = listOf(
            app("a", PinnedZone.NONE, false), app("b", PinnedZone.START, false), app("c", PinnedZone.NONE, true),
            app("d", PinnedZone.NONE, false), app("e", PinnedZone.NONE, true), app("f", PinnedZone.NONE, false)
        )
        for (steps in -7..7) {
            val rotated = RingRotation.rotateByGroup(displayed, steps) { RingRotation.groupKeyOf(it.pinnedZone.rank, it.isFavorite) }
            // what the commit stores: orderNumber = position in the rotated list
            val stored = rotated.mapIndexed { index, a -> a.copyWithOrder(index) }
            // what a reload does: the same rows in any arrival order, then the real sorter
            val reloaded = ArrayList(stored.shuffled(java.util.Random(steps.toLong())))
            UtilAppSorter.sort(reloaded, SortType.LABEL_ASCENDING)
            assertEquals("steps=$steps", stored.map { it.packageName }, reloaded.map { it.packageName })
        }
    }

    @Test fun angleOfMatchesAtan2() {
        assertEquals(0f, RingRotation.angleOf(0f, 0f, 5f, 0f), 1e-6f)
        assertEquals((PI / 2).toFloat(), RingRotation.angleOf(0f, 0f, 0f, 5f), 1e-6f)
    }
}
