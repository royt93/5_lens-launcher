package com.mckimquyen.views

/**
 * FISH-042 rev2: haptic feedback when the size of the sharp cluster changes. The sharp cluster is the set of icons whose
 * base-rect centre lies within [DepthOfField.FOCUS_ZONE] of the lens centre. Pure state machine; LensView owns the
 * vibrator, the switch / reduced-motion / real-pan gate and the one-haptic-per-frame rule.
 *
 * Why not "the hovered icon crosses the zone": an on-device probe found the hovered icon is always within 0.054 of the
 * finger (zone 0.2), so that event can never fire. The cluster size does change as the finger moves.
 */
object SharpCluster {
    /** Hysteresis width each side of [DepthOfField.FOCUS_ZONE]. */
    const val HYSTERESIS = 0.02f

    /** A cell joins the cluster at or below this distance. */
    const val ENTER_DISTANCE = DepthOfField.FOCUS_ZONE - HYSTERESIS

    /** A cell leaves the cluster at or above this distance; in between it keeps its previous state. */
    const val EXIT_DISTANCE = DepthOfField.FOCUS_ZONE + HYSTERESIS

    /** Two pulses are never closer than this; a change inside the window is dropped, not deferred. */
    const val MIN_PULSE_GAP_MS = 120L

    /** The boundary pulse is always LIGHT; the user's ordinary hover/launch level never applies to it. */
    @Suppress("UNUSED_PARAMETER")
    fun pulseIntensity(userChosen: com.mckimquyen.util.HapticIntensity): com.mckimquyen.util.HapticIntensity =
        com.mckimquyen.util.HapticIntensity.LIGHT

    /**
     * One instance per [LensView]. Per frame: [beginFrame], one [markCell] per cell, then [endFrame]. Fixed arrays, so a
     * frame allocates nothing; only [resize] (called outside the draw loop when the cell count changes) allocates.
     */
    class Tracker(cellCount: Int) {
        private var inside = BooleanArray(cellCount.coerceAtLeast(0))
        private var seen = BooleanArray(cellCount.coerceAtLeast(0))
        private var previousCount = -1
        private var lastPulseMs = NEVER

        /** Cells in the cluster as of the last [endFrame]. */
        var count = 0
            private set

        /** Gesture end, cancel, detach: the next frame is a fresh baseline and the pulse clock restarts. */
        fun reset() {
            inside.fill(false)
            seen.fill(false)
            count = 0
            previousCount = -1
            lastPulseMs = NEVER
        }

        /** Grows or shrinks the per-cell state, keeping the state of cells that still exist. */
        fun resize(cellCount: Int) {
            val n = cellCount.coerceAtLeast(0)
            if (n == inside.size) return
            inside = inside.copyOf(n)
            seen = seen.copyOf(n)
        }

        fun beginFrame() {
            seen.fill(false)
        }

        /** Non-finite distances and indices outside the grid are ignored (the cell then leaves the cluster). */
        fun markCell(index: Int, focusDistance: Float) {
            if (index < 0 || index >= inside.size) return
            if (!focusDistance.isFinite()) return
            seen[index] = true
            inside[index] = when {
                focusDistance <= ENTER_DISTANCE -> true
                focusDistance >= EXIT_DISTANCE -> false
                else -> inside[index]
            }
        }

        /**
         * Counts the cluster and returns true when a pulse should fire at [nowMs]: the size differs from the previous
         * frame's, it is not the first frame since [reset], and the last pulse was at least [MIN_PULSE_GAP_MS] ago.
         * The baseline always moves to the new size, so a dropped change is never pulsed late.
         */
        fun endFrame(nowMs: Long): Boolean {
            var n = 0
            for (i in inside.indices) {
                if (!seen[i]) inside[i] = false
                if (inside[i]) n++
            }
            count = n
            val changed = previousCount != -1 && n != previousCount
            previousCount = n
            if (!changed) return false
            if (lastPulseMs != NEVER && nowMs - lastPulseMs < MIN_PULSE_GAP_MS) return false
            lastPulseMs = nowMs
            return true
        }
    }

    private const val NEVER = Long.MIN_VALUE
}
