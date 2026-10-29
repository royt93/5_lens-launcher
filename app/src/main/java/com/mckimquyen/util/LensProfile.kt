package com.mckimquyen.util

/**
 * FISH-027: which axes the lens warps. FISHEYE is the original formula and the default, so a lens
 * with no stored profile behaves exactly as before. A pinned axis returns the item's own base
 * position, which keeps rows (ANAMORPHIC) or columns (CYLINDRICAL) straight.
 */
enum class LensProfile(private val warpsX: Boolean, private val warpsY: Boolean) {
    FISHEYE(warpsX = true, warpsY = true),
    ANAMORPHIC(warpsX = true, warpsY = false),
    CYLINDRICAL(warpsX = false, warpsY = true);

    fun shiftX(lens: Float, item: Float, boundary: Float, multiplier: Float, distortion: Float): Float =
        if (warpsX) UtilCalculator.shiftPoint(lens, item, boundary, multiplier, distortion) else item

    fun shiftY(lens: Float, item: Float, boundary: Float, multiplier: Float, distortion: Float): Float =
        if (warpsY) UtilCalculator.shiftPoint(lens, item, boundary, multiplier, distortion) else item

    fun scaleX(
        lens: Float, item: Float, itemSize: Float, boundary: Float, multiplier: Float, scale: Float, distortion: Float
    ): Float = if (warpsX) UtilCalculator.scalePoint(lens, item, itemSize, boundary, multiplier, scale, distortion) else item

    fun scaleY(
        lens: Float, item: Float, itemSize: Float, boundary: Float, multiplier: Float, scale: Float, distortion: Float
    ): Float = if (warpsY) UtilCalculator.scalePoint(lens, item, itemSize, boundary, multiplier, scale, distortion) else item

    companion object {
        /** Persisted by name; unknown or missing values mean the original behavior. */
        @JvmStatic
        fun fromStored(value: String?): LensProfile = entries.firstOrNull { it.name == value } ?: FISHEYE

        /**
         * Square icon size from the warped axes only. FISHEYE keeps the legacy min-of-both rule; with a
         * pinned axis that rule would read 0 (scaled == shifted), so the warped axis alone decides.
         */
        @JvmStatic
        fun sizeFor(profile: LensProfile, scaledX: Float, shiftedX: Float, scaledY: Float, shiftedY: Float): Float =
            when {
                profile.warpsX && profile.warpsY ->
                    UtilCalculator.calculateSquareScaledSize(scaledX, shiftedX, scaledY, shiftedY)
                profile.warpsX -> 2.0f * Math.abs(scaledX - shiftedX)
                else -> 2.0f * Math.abs(scaledY - shiftedY)
            }
    }
}
