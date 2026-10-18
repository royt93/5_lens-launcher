package com.mckimquyen.util

object LensOverscrollPhysics {

    const val DEFAULT_DAMPING_FACTOR = 0.55f

    fun isEnabled(settingOn: Boolean, reduceMotion: Boolean): Boolean = settingOn && !reduceMotion

    @JvmStatic
    fun damp(delta: Float, maxOverscroll: Float, dampingFactor: Float = DEFAULT_DAMPING_FACTOR): Float {
        if (delta <= 0f || maxOverscroll <= 0f) return 0f
        return maxOverscroll * (1f - 1f / (1f + (delta * dampingFactor) / maxOverscroll))
    }

    @JvmStatic
    fun calculateEffectiveCoordinate(
        raw: Float,
        min: Float,
        max: Float,
        maxOverscroll: Float,
        enabled: Boolean
    ): Float {
        if (!enabled || min >= max || maxOverscroll <= 0f) {
            return raw.coerceIn(min, max)
        }
        return when {
            raw < min -> min - damp(min - raw, maxOverscroll)
            raw > max -> max + damp(raw - max, maxOverscroll)
            else -> raw
        }
    }
}
