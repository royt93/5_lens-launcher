package com.mckimquyen.views

import kotlin.math.roundToInt

/**
 * FISH-039: focus peaking for low vision. Pure decision math only. The outline is one circle per icon drawn right after
 * the icon, so it never touches geometry or hit-testing. "Sharp" reuses [DepthOfField.FOCUS_ZONE] so the outlined cluster
 * is exactly the cluster depth-of-field keeps sharp, with depth-of-field on or off.
 */
object FocusPeaking {
    /** Thicker than the FISH-024 halo (2.5 dp) so it reads for low vision. */
    const val STROKE_DP = 4f
    const val OPAQUE_ALPHA = DepthOfField.OPAQUE_ALPHA

    /** Needs the setting, a visual focus centre (live touch or pin; never idle or tilt) and the apps lens. */
    fun shouldApply(enabled: Boolean, hasFocusCentre: Boolean, lensVisible: Boolean): Boolean =
        enabled && hasFocusCentre && lensVisible

    /** True inside the focus zone; non-finite distances fail closed (no outline). */
    fun isSharp(focusDistance: Float): Boolean =
        focusDistance.isFinite() && focusDistance <= DepthOfField.FOCUS_ZONE

    /**
     * A pinned focus has no lens animation, so it is fully opaque. With a live touch the outline fades with the same
     * transition as the lens (which snaps under reduced motion). Non-finite transition hides it.
     */
    fun alpha(pinnedOnly: Boolean, transition: Float): Int {
        if (pinnedOnly) return OPAQUE_ALPHA
        if (!transition.isFinite()) return 0
        return (OPAQUE_ALPHA * transition.coerceIn(0f, 1f)).roundToInt()
    }
}
