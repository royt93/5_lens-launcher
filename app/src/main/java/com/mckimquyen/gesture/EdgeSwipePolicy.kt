package com.mckimquyen.gesture

import com.mckimquyen.model.EdgeSwipeMode
import kotlin.math.abs

enum class EdgeSwipeDirection {
    NONE,
    PREVIOUS,
    NEXT
}

object EdgeSwipePolicy {
    private const val TOP_SEARCH_ZONE_RATIO = 0.20f
    private const val HORIZONTAL_DOMINANCE_RATIO = 1.5f
    private const val TOUCH_SLOP_MULTIPLIER = 2.0f

    @JvmStatic
    fun resolveIntent(
        downX: Float,
        downY: Float,
        currentX: Float,
        currentY: Float,
        viewWidth: Float,
        viewHeight: Float,
        edgeThresholdPx: Float,
        touchSlopPx: Float,
        mode: EdgeSwipeMode,
        isSearchActivationZone: Boolean
    ): EdgeSwipeDirection {
        if (mode == EdgeSwipeMode.OFF ||
            viewWidth <= 0f || viewHeight <= 0f ||
            edgeThresholdPx <= 0f || touchSlopPx < 0f ||
            isSearchActivationZone ||
            downX < 0f || downX > viewWidth ||
            downY < 0f || downY > viewHeight ||
            downY <= viewHeight * TOP_SEARCH_ZONE_RATIO
        ) {
            return EdgeSwipeDirection.NONE
        }

        val fromLeft = downX <= edgeThresholdPx
        val fromRight = downX >= viewWidth - edgeThresholdPx
        if (!fromLeft && !fromRight) return EdgeSwipeDirection.NONE

        val dx = currentX - downX
        val dy = currentY - downY

        // Strict dominance and distance checks
        if (abs(dx) <= touchSlopPx * TOUCH_SLOP_MULTIPLIER ||
            abs(dx) <= abs(dy) * HORIZONTAL_DOMINANCE_RATIO
        ) {
            return EdgeSwipeDirection.NONE
        }

        if (fromLeft && dx > 0f && (mode == EdgeSwipeMode.BOTH || mode == EdgeSwipeMode.LEFT_ONLY)) {
            return EdgeSwipeDirection.PREVIOUS
        }

        if (fromRight && dx < 0f && (mode == EdgeSwipeMode.BOTH || mode == EdgeSwipeMode.RIGHT_ONLY)) {
            return EdgeSwipeDirection.NEXT
        }

        return EdgeSwipeDirection.NONE
    }
}
