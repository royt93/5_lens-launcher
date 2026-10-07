package com.mckimquyen.util

/**
 * FISH-020: Haptic intensity with explicit duration (ms) and amplitude (0–255) for each level.
 * Values tuned on TECNO KJ7 to ensure clear differentiation across devices.
 * API 25 (minSdk) falls back to performHapticFeedback(VIRTUAL_KEY) for all levels.
 */
enum class HapticIntensity {
    LIGHT,      // 20 ms, amplitude 80
    MEDIUM,     // 40 ms, amplitude 128
    STRONG;     // 70 ms, amplitude 200

    /** Duration in milliseconds. */
    val duration: Long
        get() = when (this) {
            LIGHT -> 20L
            MEDIUM -> 40L
            STRONG -> 70L
        }

    /** Vibration amplitude (0–255; ignored on motors without amplitude control). */
    val amplitude: Int
        get() = when (this) {
            LIGHT -> 80
            MEDIUM -> 128
            STRONG -> 200
        }

    companion object {
        val DEFAULT = MEDIUM

        /** Decodes a stored ordinal; anything unknown (corrupt or from a newer build) is [DEFAULT]. */
        fun from(ordinal: Int): HapticIntensity = entries.getOrNull(ordinal) ?: DEFAULT
    }
}
