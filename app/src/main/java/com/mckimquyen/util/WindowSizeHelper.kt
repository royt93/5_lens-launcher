package com.mckimquyen.util

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.VisibleForTesting
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * FEAT-004: Helper utility for adaptive window sizing, responsive grid layout,
 * orientation handling, and large-screen / foldable layout adaptations.
 */
object WindowSizeHelper {

    enum class WindowWidthSizeClass {
        COMPACT,
        MEDIUM,
        EXPANDED
    }

    const val COMPACT_WIDTH_CUTOFF_DP = 600
    const val MEDIUM_WIDTH_CUTOFF_DP = 840
    const val TABLET_MIN_SW_DP = 600
    const val MAX_CONTENT_WIDTH_DP = 720

    /**
     * Compute WindowWidthSizeClass following official Android Material 3 breakpoints.
     */
    @JvmStatic
    fun computeWidthSizeClass(widthDp: Int): WindowWidthSizeClass {
        return when {
            widthDp < COMPACT_WIDTH_CUTOFF_DP -> WindowWidthSizeClass.COMPACT
            widthDp < MEDIUM_WIDTH_CUTOFF_DP -> WindowWidthSizeClass.MEDIUM
            else -> WindowWidthSizeClass.EXPANDED
        }
    }

    /**
     * Determine if current configuration is in landscape orientation.
     */
    @JvmStatic
    fun isLandscape(orientation: Int): Boolean {
        return orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    /**
     * Determine if the device is a tablet or large foldable based on smallest screen width dp.
     */
    @JvmStatic
    fun isFoldableOrTablet(widthDp: Int, heightDp: Int): Boolean {
        val smallestWidthDp = min(widthDp, heightDp)
        return smallestWidthDp >= TABLET_MIN_SW_DP
    }

    /**
     * Determine optimal content width constraint for large displays to avoid stretching.
     */
    @JvmStatic
    fun calculateConstrainedContentWidth(availableWidthDp: Int): Int {
        return if (availableWidthDp > MAX_CONTENT_WIDTH_DP) {
            MAX_CONTENT_WIDTH_DP
        } else {
            availableWidthDp
        }
    }

    /**
     * Calculate optimal column count for adaptive grid layouts across portrait, landscape,
     * tablets, and foldables while ensuring all items fit comfortably.
     */
    @JvmStatic
    fun calculateAdaptiveColumns(widthDp: Int, heightDp: Int, itemCount: Int): Int {
        if (itemCount <= 0) return 0
        if (itemCount == 1) return 1

        val px = ceil(sqrt(itemCount.toDouble() * widthDp / heightDp)).toInt()
        val columns = max(1, px)
        return columns
    }

    /**
     * Helper to retrieve current configuration width and height in DP from Context.
     */
    @JvmStatic
    fun getScreenDimensionsDp(context: Context): Pair<Int, Int> {
        val config = context.resources.configuration
        return Pair(config.screenWidthDp, config.screenHeightDp)
    }
}
