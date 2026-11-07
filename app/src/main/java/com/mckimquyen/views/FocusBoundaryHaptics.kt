package com.mckimquyen.views

/**
 * FISH-042: haptic feedback when the currently hovered app crosses the sharp-focus boundary.
 * Pure state machine; LensView owns the actual vibrator and the switch/reduced-motion gate.
 */
object FocusBoundaryHaptics {
    /** Hysteresis width each side of [DepthOfField.FOCUS_ZONE]. */
    const val HYSTERESIS = 0.02f
    /** Enter-sharp threshold (the zone minus hysteresis). */
    const val ENTER_DISTANCE = DepthOfField.FOCUS_ZONE - HYSTERESIS
    /** Exit-sharp threshold (the zone plus hysteresis). */
    const val EXIT_DISTANCE = DepthOfField.FOCUS_ZONE + HYSTERESIS

    /**
     * Per-frame tracker owned by LensView (one instance, allocated once).
     * [observe] returns true exactly when a focus-boundary pulse should fire.
     */
    class Tracker {
        private var previousAppKey: String? = null
        private var insideSharp = false

        /** Reset on gesture end, detach, cancel. Makes the next [observe] a fresh baseline. */
        fun reset() {
            previousAppKey = null
            insideSharp = false
        }

        /**
         * Call once per frame with the selected app's identity and its normalized focus distance.
         * Returns true if a focus-boundary pulse should fire.
         */
        fun observe(appKey: String, focusDistance: Float): Boolean {
            if (!focusDistance.isFinite()) return false
            if (appKey != previousAppKey) {
                previousAppKey = appKey
                insideSharp = focusDistance <= DepthOfField.FOCUS_ZONE
                return false
            }
            val wasInside = insideSharp
            if (wasInside && focusDistance >= EXIT_DISTANCE) {
                insideSharp = false
                return true
            }
            if (!wasInside && focusDistance <= ENTER_DISTANCE) {
                insideSharp = true
                return true
            }
            return false
        }
    }
}
