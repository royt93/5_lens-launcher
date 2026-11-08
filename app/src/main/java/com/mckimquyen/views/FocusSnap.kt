package com.mckimquyen.views

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * FISH-044: focus snap-to-icon. Pure math only. While the finger pans slowly, the optical centre is pulled towards the
 * nearest app icon centre, at most [MAX_PULL_DP]. Hit-testing, selection, launch and haptics keep the real finger
 * centre, so this object is never consulted for them. Nothing here allocates per call.
 */
object FocusSnap {
    /** At or above this finger speed the pull is zero; below it the pull fades linearly up to full at rest. */
    const val SPEED_THRESHOLD_DP_PER_S = 120f

    /** The optical centre never moves further than this towards the nearest icon. */
    const val MAX_PULL_DP = 16f

    /**
     * A paused finger sends no events, yet its speed keeps falling so the pull keeps growing: the lens asks for frames for
     * this long after the last move, then stops (the pull is then at full strength anyway).
     */
    const val REDRAW_WINDOW_MS = 500L

    private const val MS_PER_S = 1000f

    fun shouldApply(enabled: Boolean, panning: Boolean, reduceMotion: Boolean): Boolean =
        enabled && panning && !reduceMotion

    /** 1 at rest, 0 at [SPEED_THRESHOLD_DP_PER_S] and above; a non-finite speed gives 0. */
    fun gain(speedDpPerS: Float): Float {
        if (!speedDpPerS.isFinite()) return 0f
        return (1f - speedDpPerS / SPEED_THRESHOLD_DP_PER_S).coerceIn(0f, 1f)
    }

    /**
     * Index of the app cell whose centre is closest to ([x], [y]), looking only at the first [appCount] cells (the rest
     * of the grid is empty). Ties go to the lowest index. -1 when there is nothing to snap to or the input is invalid.
     */
    fun nearestApp(x: Float, y: Float, rects: Array<RectF>, appCount: Int): Int {
        if (!x.isFinite() || !y.isFinite()) return -1
        var best = -1
        var bestSq = Float.MAX_VALUE
        val n = min(appCount, rects.size)
        for (i in 0 until n) {
            val dx = rects[i].centerX() - x
            val dy = rects[i].centerY() - y
            val sq = dx * dx + dy * dy
            if (sq < bestSq) {
                bestSq = sq
                best = i
            }
        }
        return best
    }

    /**
     * Writes into [out] the offset from ([fromX], [fromY]) towards ([toX], [toY]): its length is the distance capped at
     * [maxPx], times [gain]. Zero for no distance, no gain, or any non-finite input.
     */
    fun pull(fromX: Float, fromY: Float, toX: Float, toY: Float, maxPx: Float, gain: Float, out: FloatArray) {
        out[0] = 0f
        out[1] = 0f
        if (!(fromX.isFinite() && fromY.isFinite() && toX.isFinite() && toY.isFinite())) return
        if (!maxPx.isFinite() || maxPx <= 0f || !gain.isFinite() || gain <= 0f) return
        val dx = toX - fromX
        val dy = toY - fromY
        val distance = sqrt(dx * dx + dy * dy)
        if (distance <= 0f) return
        val magnitude = min(distance, maxPx) * gain
        out[0] = dx / distance * magnitude
        out[1] = dy / distance * magnitude
    }

    /**
     * Finger speed from raw touch samples. A sample that cannot give a trustworthy speed (first sample, time not moving
     * forward, non-finite input, bad density) reports infinite speed, so the pull fails closed instead of misfiring.
     */
    class Tracker {
        private var hasSample = false
        private var lastX = 0f
        private var lastY = 0f
        private var lastMs = 0L
        private var segmentValid = false
        private var segmentDp = 0f
        private var segmentMs = 0L

        fun reset() {
            hasSample = false
            segmentValid = false
        }

        fun onMove(x: Float, y: Float, timeMs: Long, density: Float) {
            if (!x.isFinite() || !y.isFinite() || !density.isFinite() || density <= 0f) {
                reset()
                return
            }
            segmentValid = false
            if (hasSample) {
                val dt = timeMs - lastMs
                if (dt > 0L) {
                    val dx = x - lastX
                    val dy = y - lastY
                    segmentDp = sqrt(dx * dx + dy * dy) / density
                    segmentMs = dt
                    segmentValid = true
                }
            }
            lastX = x
            lastY = y
            lastMs = timeMs
            hasSample = true
        }

        /** True while a pause after the last move can still change the pull. */
        fun needsRedraw(nowMs: Long): Boolean = hasSample && nowMs - lastMs < REDRAW_WINDOW_MS

        /** The last segment's distance over the longer of its own duration and the time since the last sample. */
        fun speedDpPerS(nowMs: Long): Float {
            if (!hasSample || !segmentValid) return Float.POSITIVE_INFINITY
            val elapsed = max(segmentMs, nowMs - lastMs)
            return segmentDp / (elapsed / MS_PER_S)
        }
    }
}
