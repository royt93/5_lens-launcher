package com.mckimquyen.views

/**
 * FISH-046: hover magnify. Pure decision math only. When the finger rests on one icon during a real pan, that icon is drawn
 * a little larger. Only the drawn rect is scaled: hit-testing and selection keep the unscaled rect, so this object is never
 * consulted for them. Nothing here allocates per call.
 */
object HoverMagnify {
    /** The finger must rest on the same icon this long before the icon starts to grow. */
    const val DWELL_MS = 200L

    /** The growth from 1 to [SCALE] takes this long. */
    const val RAMP_MS = 150L

    /** At or above this finger speed the finger is not resting. */
    const val STILL_SPEED_DP_PER_S = 15f

    /** Final scale of the dwelling icon. */
    const val SCALE = 1.2f

    private const val NONE = -1
    private const val NOT_STILL = Long.MIN_VALUE

    fun shouldApply(enabled: Boolean, panning: Boolean, reduceMotion: Boolean): Boolean =
        enabled && panning && !reduceMotion

    /** One instance per LensView. Per frame call [onFrame] once, then [scaleFor] for the icon about to be drawn. */
    class Tracker {
        private var index = NONE
        private var stillSinceMs = NOT_STILL

        /**
         * [selectedIndex] is the icon under the finger this frame (-1 for none). The dwell starts at the first frame the
         * finger is on the icon and below [STILL_SPEED_DP_PER_S]; a different icon, a faster or unknown speed, or no icon
         * stops it, so the next still frame starts a new dwell.
         */
        fun onFrame(selectedIndex: Int, speedDpPerS: Float, nowMs: Long) {
            val still = selectedIndex >= 0 && speedDpPerS.isFinite() && speedDpPerS < STILL_SPEED_DP_PER_S
            if (!still) {
                index = NONE
                stillSinceMs = NOT_STILL
                return
            }
            if (selectedIndex != index || stillSinceMs == NOT_STILL) {
                index = selectedIndex
                stillSinceMs = nowMs
            }
        }

        /** 1 until the dwell has passed, then a linear ramp to [SCALE]; always 1 for any icon but the dwelling one. */
        fun scaleFor(cell: Int, nowMs: Long): Float {
            if (cell < 0 || cell != index || stillSinceMs == NOT_STILL) return 1f
            val grown = nowMs - stillSinceMs - DWELL_MS
            if (grown <= 0L) return 1f
            if (grown >= RAMP_MS) return SCALE
            return 1f + (SCALE - 1f) * grown.toFloat() / RAMP_MS
        }

        /** True while an icon is waiting out the dwell or growing, so the lens keeps asking for frames with a still finger. */
        fun needsRedraw(nowMs: Long): Boolean =
            stillSinceMs != NOT_STILL && nowMs - stillSinceMs < DWELL_MS + RAMP_MS

        /** Gesture end, cancel, detach: nothing is dwelling afterwards. */
        fun reset() {
            index = NONE
            stillSinceMs = NOT_STILL
        }
    }
}
