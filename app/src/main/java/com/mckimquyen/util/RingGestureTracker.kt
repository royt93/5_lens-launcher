package com.mckimquyen.util

import com.mckimquyen.util.RingRotation.Decision
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * FISH-035: decides, from a bounded window of MOVE samples, whether a one-finger drag around a pinned centre is a
 * rotation. ROTATE needs: enough signed sweep, a radius that stays inside a band around the starting radius, and a
 * heading that actually turns in the sweep direction (a straight line never turns). Anything else is PAN. The decision
 * is sticky; after ROTATE the sweep keeps accumulating. Allocation-free after construction.
 */
class RingGestureTracker(private val density: Float) {
    private val xs = FloatArray(RingRotation.MAX_SAMPLES)
    private val ys = FloatArray(RingRotation.MAX_SAMPLES)
    private var count = 0
    private var centerX = 0f
    private var centerY = 0f
    private var startRadius = 0f
    private var maxRadiusDeviation = 0f
    private var startTimeMs = 0L
    private var lastAngle = 0f
    private var begun = false

    var sweepRadians = 0f
        private set
    var decision = Decision.UNDECIDED
        private set

    fun begin(centerX: Float, centerY: Float, x: Float, y: Float, timeMs: Long) {
        reset()
        begun = true
        this.centerX = centerX
        this.centerY = centerY
        startTimeMs = timeMs
        val valid = density.isFinite() && density > 0f && centerX.isFinite() && centerY.isFinite() && x.isFinite() && y.isFinite()
        startRadius = if (valid) hypot(x - centerX, y - centerY) else 0f
        if (!valid || startRadius < RingRotation.MIN_RADIUS_DP * density) {
            decision = Decision.PAN
            return
        }
        lastAngle = atan2(y - centerY, x - centerX)
        xs[0] = x
        ys[0] = y
        count = 1
    }

    fun onMove(x: Float, y: Float, timeMs: Long): Decision {
        if (!begun || !x.isFinite() || !y.isFinite()) return decision
        if (decision == Decision.PAN) return decision
        val angle = atan2(y - centerY, x - centerX)
        sweepRadians += RingRotation.shortestAngleDelta(lastAngle, angle)
        lastAngle = angle
        if (decision == Decision.ROTATE) return decision

        if (count < RingRotation.MAX_SAMPLES) {
            xs[count] = x
            ys[count] = y
            count++
        }
        maxRadiusDeviation = maxOf(maxRadiusDeviation, abs(hypot(x - centerX, y - centerY) - startRadius))
        if (maxRadiusDeviation > RingRotation.RADIUS_STABILITY_FRACTION * startRadius) return decide(Decision.PAN)

        val moves = count - 1
        if (moves >= RingRotation.MIN_SAMPLES && abs(sweepRadians) >= RingRotation.SWEEP_THRESHOLD_RAD) {
            val turn = headingTurn(moves)
            if (abs(turn) >= RingRotation.TURN_FRACTION * abs(sweepRadians) && turn * sweepRadians > 0f) {
                return decide(Decision.ROTATE)
            }
        }
        if (timeMs - startTimeMs >= RingRotation.MAX_WINDOW_MS || count >= RingRotation.MAX_SAMPLES) {
            return decide(Decision.PAN)
        }
        return decision
    }

    fun reset() {
        count = 0
        begun = false
        sweepRadians = 0f
        maxRadiusDeviation = 0f
        startRadius = 0f
        decision = Decision.UNDECIDED
    }

    private fun decide(result: Decision): Decision {
        decision = result
        return result
    }

    /** Signed change of heading between the first and second half of the window, in (-PI, PI]. */
    private fun headingTurn(moves: Int): Float {
        val mid = moves / 2
        val first = atan2(ys[mid] - ys[0], xs[mid] - xs[0])
        val second = atan2(ys[moves] - ys[mid], xs[moves] - xs[mid])
        return RingRotation.shortestAngleDelta(first, second)
    }
}
