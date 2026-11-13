package com.mckimquyen.views

/**
 * FISH-048: pure decision math for the small dot that marks a recently opened app while the finger drags over the lens. Nothing
 * here allocates, and the dot is drawn after the icon from an already warped rect, so it never touches geometry or hit-testing.
 */
object RecentDots {
    /** At most this many of the newest launches carry a dot. */
    const val MAX_DOTS = 3

    /** A launch older than this no longer counts as recent. */
    const val WINDOW_MS = 24L * 60L * 60L * 1000L

    /** Dot radius as a share of the warped icon width. */
    const val FRACTION = 0.12f
    const val MIN_DP = 1.5f
    const val MAX_DP = 4f

    /**
     * Needs the switch, not Clean the Lens, a finger really dragging, and the apps lens on screen. The switch term is redundant
     * in the real path (LensView clears the marked cells on touch down whenever the switch is off, so nothing can be drawn),
     * and is kept so this decision stays correct if it is ever called from elsewhere; a device mutation without it is
     * therefore NOT caught, by design.
     */
    fun shouldApply(enabled: Boolean, cleanMode: Boolean, dragging: Boolean, lensVisible: Boolean): Boolean =
        enabled && !cleanMode && dragging && lensVisible

    /**
     * `clamp(FRACTION * iconWidth, MIN_DP, MAX_DP)` in pixels, never more than half the icon so a tiny icon is not covered.
     * Non-finite or non-positive input gives 0 (no dot).
     */
    fun radiusPx(iconWidthPx: Float, density: Float): Float {
        // A width of exactly 0 needs no guard of its own: the half-width cap below already gives 0 for it.
        if (!iconWidthPx.isFinite() || iconWidthPx < 0f || !density.isFinite() || density <= 0f) return 0f
        val scaled = (FRACTION * iconWidthPx).coerceIn(MIN_DP * density, MAX_DP * density)
        return minOf(scaled, iconWidthPx / 2f)
    }
}
