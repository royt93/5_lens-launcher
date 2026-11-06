package com.mckimquyen.util

import kotlin.math.atan2

/**
 * FISH-035: pure math for the rotate-the-ring gesture. No Android types, so it is fully unit-testable.
 * Thresholds are provisional (tuned only on synthetic input, not on real fingers).
 */
object RingRotation {
    const val MIN_RADIUS_DP = 24f
    /** 30 degrees of sweep per list step. */
    const val STEP_RADIANS = 0.5235988f
    /** 30 degrees of accumulated sweep is the minimum rotation evidence (equals one step). */
    const val SWEEP_THRESHOLD_RAD = 0.5235988f
    /** Classification window after DOWN; a gesture still undecided when it expires is a pan. */
    const val MAX_WINDOW_MS = 300L
    /** Memory bound for the sample window. */
    const val MAX_SAMPLES = 64
    /** Moves (after the DOWN sample) required before an arc can be accepted. */
    const val MIN_SAMPLES = 4
    /** Allowed radius spread inside the window, as a share of the starting radius. */
    const val RADIUS_STABILITY_FRACTION = 0.25f
    /** Heading turn must be at least this share of the sweep, which a straight tangent line never reaches. */
    const val TURN_FRACTION = 0.4f

    private const val TWO_PI = 6.2831855f
    private const val PI_F = 3.1415927f

    enum class Decision { UNDECIDED, PAN, ROTATE }

    fun angleOf(centerX: Float, centerY: Float, x: Float, y: Float): Float =
        atan2(y - centerY, x - centerX)

    /** Signed smallest difference `next - previous` in (-PI, PI]; non-finite input is 0 (never loops). */
    fun shortestAngleDelta(previous: Float, next: Float): Float {
        if (!previous.isFinite() || !next.isFinite()) return 0f
        var d = (next - previous) % TWO_PI
        if (d > PI_F) d -= TWO_PI else if (d <= -PI_F) d += TWO_PI
        return d
    }

    /** Whole list steps in [sweepRad], truncating toward zero; non-finite is 0. */
    fun wholeSteps(sweepRad: Float): Int =
        if (!sweepRad.isFinite()) 0 else (sweepRad / STEP_RADIANS).toInt()

    /** Cyclic shift: positive steps move the first items to the end. Never mutates [items]. */
    fun <T> rotate(items: List<T>, steps: Int): List<T> {
        if (items.size < 2) return ArrayList(items)
        val n = Math.floorMod(steps, items.size)
        if (n == 0) return ArrayList(items)
        return ArrayList(items.subList(n, items.size) + items.subList(0, n))
    }

    /**
     * Rotates every group separately and concatenates the groups in ascending key order. `UtilAppSorter` orders by
     * pinned-zone rank, then favorite, before `orderNumber`, so this is exactly what a reload reproduces.
     */
    fun <T> rotateByGroup(items: List<T>, steps: Int, groupKey: (T) -> Int): List<T> {
        if (items.size < 2) return ArrayList(items)
        return ArrayList(items.groupBy(groupKey).toSortedMap().values.flatMap { rotate(it, steps) })
    }

    /** Same major/minor order as `UtilAppSorter`: pinned-zone rank first, favorites before the rest. */
    fun groupKeyOf(zoneRank: Int, favorite: Boolean): Int = zoneRank * 2 + (if (favorite) 0 else 1)
}
