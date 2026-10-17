package com.mckimquyen.gesture

import com.mckimquyen.model.EdgeSwipeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeSwipePolicyTest {

    private companion object {
        const val WIDTH = 1080f
        const val HEIGHT = 2400f
        const val EDGE_THRESHOLD = 96f // 32dp * 3 on 480dpi
        const val TOUCH_SLOP = 24f     // 8dp * 3
    }

    @Test
    fun leftEdgeSwipeRight_returnsPrevious_inBothAndLeftOnlyModes() {
        val resultBoth = EdgeSwipePolicy.resolveIntent(
            downX = 40f, downY = 800f, currentX = 140f, currentY = 805f,
            viewWidth = WIDTH, viewHeight = HEIGHT,
            edgeThresholdPx = EDGE_THRESHOLD, touchSlopPx = TOUCH_SLOP,
            mode = EdgeSwipeMode.BOTH, isSearchActivationZone = false
        )
        assertEquals(EdgeSwipeDirection.PREVIOUS, resultBoth)

        val resultLeftOnly = EdgeSwipePolicy.resolveIntent(
            downX = 40f, downY = 800f, currentX = 140f, currentY = 805f,
            viewWidth = WIDTH, viewHeight = HEIGHT,
            edgeThresholdPx = EDGE_THRESHOLD, touchSlopPx = TOUCH_SLOP,
            mode = EdgeSwipeMode.LEFT_ONLY, isSearchActivationZone = false
        )
        assertEquals(EdgeSwipeDirection.PREVIOUS, resultLeftOnly)
    }

    @Test
    fun rightEdgeSwipeLeft_returnsNext_inBothAndRightOnlyModes() {
        val resultBoth = EdgeSwipePolicy.resolveIntent(
            downX = 1040f, downY = 800f, currentX = 940f, currentY = 805f,
            viewWidth = WIDTH, viewHeight = HEIGHT,
            edgeThresholdPx = EDGE_THRESHOLD, touchSlopPx = TOUCH_SLOP,
            mode = EdgeSwipeMode.BOTH, isSearchActivationZone = false
        )
        assertEquals(EdgeSwipeDirection.NEXT, resultBoth)

        val resultRightOnly = EdgeSwipePolicy.resolveIntent(
            downX = 1040f, downY = 800f, currentX = 940f, currentY = 805f,
            viewWidth = WIDTH, viewHeight = HEIGHT,
            edgeThresholdPx = EDGE_THRESHOLD, touchSlopPx = TOUCH_SLOP,
            mode = EdgeSwipeMode.RIGHT_ONLY, isSearchActivationZone = false
        )
        assertEquals(EdgeSwipeDirection.NEXT, resultRightOnly)
    }

    @Test
    fun centerSwipe_returnsNone() {
        val result = EdgeSwipePolicy.resolveIntent(
            downX = 540f, downY = 800f, currentX = 640f, currentY = 800f,
            viewWidth = WIDTH, viewHeight = HEIGHT,
            edgeThresholdPx = EDGE_THRESHOLD, touchSlopPx = TOUCH_SLOP,
            mode = EdgeSwipeMode.BOTH, isSearchActivationZone = false
        )
        assertEquals(EdgeSwipeDirection.NONE, result)
    }

    @Test
    fun verticalOrDiagonalSwipe_returnsNone() {
        val vertical = EdgeSwipePolicy.resolveIntent(
            downX = 40f, downY = 800f, currentX = 140f, currentY = 950f,
            viewWidth = WIDTH, viewHeight = HEIGHT,
            edgeThresholdPx = EDGE_THRESHOLD, touchSlopPx = TOUCH_SLOP,
            mode = EdgeSwipeMode.BOTH, isSearchActivationZone = false
        )
        assertEquals(EdgeSwipeDirection.NONE, vertical)
    }

    @Test
    fun movementAtThreshold_returnsNone() {
        val belowSlop = EdgeSwipePolicy.resolveIntent(
            downX = 40f, downY = 800f, currentX = 40f + TOUCH_SLOP * 2f, currentY = 800f,
            viewWidth = WIDTH, viewHeight = HEIGHT,
            edgeThresholdPx = EDGE_THRESHOLD, touchSlopPx = TOUCH_SLOP,
            mode = EdgeSwipeMode.BOTH, isSearchActivationZone = false
        )
        assertEquals(EdgeSwipeDirection.NONE, belowSlop)
    }

    @Test
    fun disabledOrWrongEdgeMode_returnsNone() {
        val off = EdgeSwipePolicy.resolveIntent(
            downX = 40f, downY = 800f, currentX = 140f, currentY = 800f,
            viewWidth = WIDTH, viewHeight = HEIGHT,
            edgeThresholdPx = EDGE_THRESHOLD, touchSlopPx = TOUCH_SLOP,
            mode = EdgeSwipeMode.OFF, isSearchActivationZone = false
        )
        assertEquals(EdgeSwipeDirection.NONE, off)

        val leftSwipeInRightOnly = EdgeSwipePolicy.resolveIntent(
            downX = 40f, downY = 800f, currentX = 140f, currentY = 800f,
            viewWidth = WIDTH, viewHeight = HEIGHT,
            edgeThresholdPx = EDGE_THRESHOLD, touchSlopPx = TOUCH_SLOP,
            mode = EdgeSwipeMode.RIGHT_ONLY, isSearchActivationZone = false
        )
        assertEquals(EdgeSwipeDirection.NONE, leftSwipeInRightOnly)

        val rightSwipeInLeftOnly = EdgeSwipePolicy.resolveIntent(
            downX = 1040f, downY = 800f, currentX = 940f, currentY = 800f,
            viewWidth = WIDTH, viewHeight = HEIGHT,
            edgeThresholdPx = EDGE_THRESHOLD, touchSlopPx = TOUCH_SLOP,
            mode = EdgeSwipeMode.LEFT_ONLY, isSearchActivationZone = false
        )
        assertEquals(EdgeSwipeDirection.NONE, rightSwipeInLeftOnly)
    }

    @Test
    fun topSearchActivationZone_returnsNone() {
        val inTopSlice = EdgeSwipePolicy.resolveIntent(
            downX = 40f, downY = 100f, currentX = 140f, currentY = 100f,
            viewWidth = WIDTH, viewHeight = HEIGHT,
            edgeThresholdPx = EDGE_THRESHOLD, touchSlopPx = TOUCH_SLOP,
            mode = EdgeSwipeMode.BOTH, isSearchActivationZone = true
        )
        assertEquals(EdgeSwipeDirection.NONE, inTopSlice)
    }

    @Test
    fun outOfBoundsAndZeroSize_returnsNone() {
        val zeroWidth = EdgeSwipePolicy.resolveIntent(
            downX = 40f, downY = 800f, currentX = 140f, currentY = 800f,
            viewWidth = 0f, viewHeight = HEIGHT,
            edgeThresholdPx = EDGE_THRESHOLD, touchSlopPx = TOUCH_SLOP,
            mode = EdgeSwipeMode.BOTH, isSearchActivationZone = false
        )
        assertEquals(EdgeSwipeDirection.NONE, zeroWidth)

        val negativeX = EdgeSwipePolicy.resolveIntent(
            downX = -10f, downY = 800f, currentX = 140f, currentY = 800f,
            viewWidth = WIDTH, viewHeight = HEIGHT,
            edgeThresholdPx = EDGE_THRESHOLD, touchSlopPx = TOUCH_SLOP,
            mode = EdgeSwipeMode.BOTH, isSearchActivationZone = false
        )
        assertEquals(EdgeSwipeDirection.NONE, negativeX)
    }
}
