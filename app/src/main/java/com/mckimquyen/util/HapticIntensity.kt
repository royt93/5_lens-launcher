package com.mckimquyen.util

import android.view.HapticFeedbackConstants

/**
 * FISH-017: one shared strength for the hover and launch haptics. [MEDIUM] is
 * [HapticFeedbackConstants.VIRTUAL_KEY], what `LensView` always used, so it is the default and an
 * install that never touches the setting behaves exactly as before.
 *
 * [STRONG] is LONG_PRESS, not CONFIRM: on TECNO BG6 (Android 13) `dumpsys vibrator_manager` showed
 * CONFIRM and VIRTUAL_KEY both playing CLICK, so "Strong" felt like "Medium", while LONG_PRESS
 * played HEAVY_CLICK. LONG_PRESS also exists on every supported API level, so no version gate.
 */
enum class HapticIntensity {
    LIGHT,
    MEDIUM,
    STRONG;

    val feedbackConstant: Int
        get() = when (this) {
            LIGHT -> HapticFeedbackConstants.CLOCK_TICK
            MEDIUM -> HapticFeedbackConstants.VIRTUAL_KEY
            STRONG -> HapticFeedbackConstants.LONG_PRESS
        }

    companion object {
        val DEFAULT = MEDIUM

        /** Decodes a stored ordinal; anything unknown (corrupt or from a newer build) is [DEFAULT]. */
        fun from(ordinal: Int): HapticIntensity = entries.getOrNull(ordinal) ?: DEFAULT
    }
}
