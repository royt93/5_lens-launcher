package com.mckimquyen.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * FISH-028: the lens hood limits how far the warp reaches. The value is a fraction of the legacy reach
 * (the distance from the finger to the farther screen edge). [FULL] is the original behavior, so a lens
 * with no stored value draws exactly as before; a smaller hood leaves everything beyond it still.
 */
object LensHood {
    const val FULL = 1.0f
    const val MIN = 0.2f
    const val STEP = 0.05f

    /** Non-finite or out-of-range stored values never reach the draw loop. */
    @JvmStatic
    fun clamp(value: Float): Float = if (value.isFinite()) value.coerceIn(MIN, FULL) else FULL

    /** Slider values must sit on [STEP] multiples or the Slider throws; prefs may hold any float. */
    @JvmStatic
    fun snap(value: Float): Float {
        val safe = clamp(value)
        return clamp((safe / STEP).roundToInt() * STEP)
    }

    /**
     * Smooth falloff that is 1 at the lens, 0 at the hood edge and flat beyond it, so the warp has no
     * visible jump where the hood ends. [FULL] returns 1 everywhere, which keeps the legacy formula.
     */
    @JvmStatic
    fun falloff(distance: Float, reach: Float, hood: Float): Float {
        if (hood >= FULL) return 1f
        val radius = reach * clamp(hood)
        if (radius <= 0f || distance >= radius) return 0f
        val t = 1f - distance / radius
        return t * t * (3f - 2f * t)
    }

    @JvmStatic
    fun reach(lensPosition: Float, boundary: Float): Float = max(lensPosition, boundary - lensPosition)

    @JvmStatic
    fun distance(lensPosition: Float, itemPosition: Float): Float = abs(lensPosition - itemPosition)
}
