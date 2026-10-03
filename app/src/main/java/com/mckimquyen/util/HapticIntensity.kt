package com.mckimquyen.util

import android.os.Build
import android.view.HapticFeedbackConstants

/**
 * FISH-017: one shared strength for the hover and launch haptics. [MEDIUM] is
 * [HapticFeedbackConstants.VIRTUAL_KEY], what `LensView` always used, so it is the default and an
 * install that never touches the setting behaves exactly as before.
 */
enum class HapticIntensity {
    LIGHT,
    MEDIUM,
    STRONG;

    fun feedbackConstant(sdkInt: Int): Int = when (this) {
        LIGHT -> HapticFeedbackConstants.CLOCK_TICK
        MEDIUM -> HapticFeedbackConstants.VIRTUAL_KEY
        STRONG ->
            if (sdkInt >= CONFIRM_MIN_SDK) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.LONG_PRESS
    }

    companion object {
        val DEFAULT = MEDIUM

        /** [HapticFeedbackConstants.CONFIRM] was added in API 30. */
        const val CONFIRM_MIN_SDK = Build.VERSION_CODES.R

        /** Decodes a stored ordinal; anything unknown (corrupt or from a newer build) is [DEFAULT]. */
        fun from(ordinal: Int): HapticIntensity = entries.getOrNull(ordinal) ?: DEFAULT
    }
}
