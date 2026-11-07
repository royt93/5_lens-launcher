package com.mckimquyen.views

import kotlin.math.hypot

/**
 * FISH-040: chromatic aberration at the lens rim. Pure math only. The fringe is two extra copies of an icon (red-only,
 * shifted outward; blue-only, shifted inward) drawn after the icons, so it never touches geometry or hit-testing.
 * Distance, focus zone and transition reuse [DepthOfField] so every optical effect agrees on what "far from the finger"
 * means and on how reduced motion snaps the transition.
 */
object ChromaticAberration {
    /** At most this many icons per frame get fringes: the farthest ones. Worst case 2 * MAX_ICONS extra bitmap draws. */
    const val MAX_ICONS = 8
    const val MIN_OFFSET_DP = 1f
    const val MAX_OFFSET_DP = 3f
    const val OPAQUE_ALPHA = DepthOfField.OPAQUE_ALPHA

    /** Blurred DoF layers (band 2) are downsampled, so a 1 to 3 dp fringe there is invisible and wasted. */
    const val MAX_FRINGED_BAND = 1

    fun shouldApply(enabled: Boolean, lensVisible: Boolean): Boolean {
        return enabled && lensVisible
    }

    /**
     * Offset in px: 0 inside the sharp zone, [MIN_OFFSET_DP] just outside it, rising linearly to [MAX_OFFSET_DP] at the
     * farthest point, scaled by the lens [transition]. Any non-finite or non-positive input yields 0 (no fringe).
     */
    fun offsetPx(focusDistance: Float, transition: Float, density: Float): Float {
        if (!focusDistance.isFinite() || !transition.isFinite() || !density.isFinite() || density <= 0f) return 0f
        if (focusDistance <= DepthOfField.FOCUS_ZONE) return 0f
        val ramp = ((focusDistance - DepthOfField.FOCUS_ZONE) / (1f - DepthOfField.FOCUS_ZONE)).coerceIn(0f, 1f)
        val dp = MIN_OFFSET_DP + (MAX_OFFSET_DP - MIN_OFFSET_DP) * ramp
        return density * dp * transition.coerceIn(0f, 1f)
    }

    /** Unit vector from the touch to the cell centre, written into [out]; zero when they coincide or input is not finite. */
    fun direction(touchX: Float, touchY: Float, cellX: Float, cellY: Float, out: FloatArray) {
        val dx = cellX - touchX
        val dy = cellY - touchY
        val length = hypot(dx, dy)
        if (!length.isFinite() || length <= 0f) {
            out[0] = 0f
            out[1] = 0f
            return
        }
        out[0] = dx / length
        out[1] = dy / length
    }

    /** True for a finite distance outside the sharp zone, in a DoF band that still shows detail. */
    fun isFringed(focusDistance: Float, dofBand: Int): Boolean {
        return focusDistance.isFinite() && focusDistance > DepthOfField.FOCUS_ZONE && dofBand in 0..MAX_FRINGED_BAND
    }

    /**
     * Keeps the [capacity] largest distances seen since [reset], with the cell index of each. Fixed arrays, so offering
     * during the draw loop allocates nothing. Equal distances never displace an earlier entry.
     */
    class TopFarthest(val capacity: Int) {
        init {
            require(capacity >= 1) { "capacity must be at least 1" }
        }

        private val distances = FloatArray(capacity)
        private val indices = IntArray(capacity)

        var count = 0
            private set

        fun reset() {
            count = 0
        }

        fun indexAt(slot: Int): Int = indices[slot]

        fun distanceAt(slot: Int): Float = distances[slot]

        /** Returns the slot now holding the entry (0 until [capacity]), or -1 when it did not make the cut. */
        fun offer(distance: Float, index: Int): Int {
            if (!distance.isFinite()) return -1
            if (count < capacity) {
                distances[count] = distance
                indices[count] = index
                return count++
            }
            var nearest = 0
            for (slot in 1 until capacity) if (distances[slot] < distances[nearest]) nearest = slot
            if (distance <= distances[nearest]) return -1
            distances[nearest] = distance
            indices[nearest] = index
            return nearest
        }
    }
}
