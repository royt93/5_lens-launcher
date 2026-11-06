package com.mckimquyen.views

import kotlin.math.roundToInt

/**
 * FISH-034: edge vignette for the lens grid. Pure math only. The vignette is a per-icon paint alpha, so it
 * never touches geometry or hit-testing: LensView computes each cell's rect first and only picks the alpha
 * the icon is drawn with. Distance, focus zone and transition reuse [DepthOfField] so both effects agree on
 * what "far from the finger" means and on how reduced motion snaps the transition.
 */
object LensVignette {
    /** Far icons keep at least this share of opacity so they stay legible and tappable. */
    const val FLOOR_ALPHA_FRACTION = 0.5f
    const val OPAQUE_ALPHA = DepthOfField.OPAQUE_ALPHA

    fun shouldApply(enabled: Boolean, lensVisible: Boolean): Boolean = enabled && lensVisible

    /** 0 inside the focus zone or when the lens is not magnified; 1 at the farthest point. */
    fun intensity(focusDistance: Float, transition: Float): Float =
        if (!focusDistance.isFinite() || !transition.isFinite()) 0f
        else DepthOfField.intensity(focusDistance, transition)

    /** [OPAQUE_ALPHA] at intensity 0 down to `OPAQUE_ALPHA * FLOOR_ALPHA_FRACTION` at 1. Non-finite is opaque. */
    fun alpha(intensity: Float): Int {
        if (!intensity.isFinite()) return OPAQUE_ALPHA
        val t = intensity.coerceIn(0f, 1f)
        return (OPAQUE_ALPHA * (1f - t * (1f - FLOOR_ALPHA_FRACTION))).roundToInt()
    }

    /** Both effects can lower alpha (DoF fallback below API 31); the icon takes the lower, never the product. */
    fun combine(dofAlpha: Int, vignetteAlpha: Int): Int = minOf(dofAlpha, vignetteAlpha)
}
