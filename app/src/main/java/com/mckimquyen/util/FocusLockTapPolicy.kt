package com.mckimquyen.util

data class EmptyTapSample(val x: Float, val y: Float, val eventTimeMs: Long)

object FocusLockTapPolicy {
    fun acceptsEmptyTap(
        armed: Boolean,
        moving: Boolean,
        longPressTriggered: Boolean,
        pointerCount: Int,
        selectedIndex: Int,
        x: Float,
        y: Float,
        width: Int,
        height: Int
    ): Boolean = armed && !moving && !longPressTriggered && pointerCount == 1 && selectedIndex < 0 &&
        width > 0 && height > 0 && x.isFinite() && y.isFinite() && x >= 0f && y >= 0f &&
        x < width && y < height

    fun isDoubleTap(
        first: EmptyTapSample?,
        second: EmptyTapSample,
        timeoutMs: Long,
        slopPx: Float
    ): Boolean {
        if (timeoutMs <= 0L || !slopPx.isFinite() || slopPx <= 0f) return false
        first ?: return false
        if (!first.x.isFinite() || !first.y.isFinite() ||
            !second.x.isFinite() || !second.y.isFinite()) return false
        val elapsed = second.eventTimeMs - first.eventTimeMs
        if (elapsed < 0L || elapsed > timeoutMs) return false
        return kotlin.math.hypot(
            (second.x - first.x).toDouble(),
            (second.y - first.y).toDouble()
        ) <= slopPx
    }
}
