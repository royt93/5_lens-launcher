package com.mckimquyen.views

import kotlin.math.abs
import kotlin.math.exp

/**
 * FISH-041: lens centre inertia. Pure math only. While the finger pans, the optical effects (warp, depth of field,
 * vignette, focus peaking, fringe direction) follow the finger through a short exponential lag. Hit-testing, icon
 * selection and launch keep the real finger centre, so this object is never consulted for them.
 */
object LensInertia {
    /** Lag of the visual centre: it covers about 63% of the gap to the finger in this time. */
    const val TIME_CONSTANT_MS = 100f

    /** The redraw loop stops once the visual centre is within this many px of the finger on both axes. */
    const val EPSILON_PX = 1f

    /** Below this gap the visual and finger centres count as one, so only one rect per cell is computed. */
    const val SAME_CENTRE_PX = 0.5f

    /** A frame hitch longer than this is clamped so the centre cannot teleport. */
    const val MAX_DT_MS = 50f

    fun shouldApply(enabled: Boolean, panning: Boolean, reduceMotion: Boolean): Boolean =
        enabled && panning && !reduceMotion

    /**
     * One exponential step of [visual] towards [target] over [dtMs] (clamped to [MAX_DT_MS]). Frame-rate independent,
     * never overshoots. A non-positive or non-finite [dtMs] or [target] keeps [visual]; a non-finite [visual] adopts
     * [target].
     */
    fun step(visual: Float, target: Float, dtMs: Float): Float {
        if (!target.isFinite()) return visual
        if (!visual.isFinite()) return target
        if (!dtMs.isFinite() || dtMs <= 0f) return visual
        val alpha = 1f - exp(-dtMs.coerceAtMost(MAX_DT_MS) / TIME_CONSTANT_MS)
        return visual + (target - visual) * alpha
    }

    fun isSettled(visualX: Float, visualY: Float, targetX: Float, targetY: Float): Boolean =
        within(visualX, visualY, targetX, targetY, EPSILON_PX)

    fun sameCentre(visualX: Float, visualY: Float, targetX: Float, targetY: Float): Boolean =
        within(visualX, visualY, targetX, targetY, SAME_CENTRE_PX)

    private fun within(ax: Float, ay: Float, bx: Float, by: Float, limit: Float): Boolean {
        if (!ax.isFinite() || !ay.isFinite() || !bx.isFinite() || !by.isFinite()) return false
        return abs(ax - bx) <= limit && abs(ay - by) <= limit
    }
}
