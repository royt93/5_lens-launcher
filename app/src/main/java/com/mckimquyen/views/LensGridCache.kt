package com.mckimquyen.views

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import com.mckimquyen.model.Grid
import com.mckimquyen.util.UtilCalculator

/**
 * PERF-001: caches LensView's grid geometry (item counts, size, spacing) and each cell's base
 * (unshifted) position. These only depend on (available size, item count, icon size, insets),
 * not on touch position, but LensView.onDraw ran continuously while the lens was being dragged
 * and used to recompute all of it - and allocate a fresh RectF per cell - on every single frame.
 */
class LensGridCache {
    var grid: Grid? = null
        private set
    var baseRects: Array<RectF> = emptyArray()
        private set
    var recomputeCount = 0
        private set

    private var cachedWidth = -1
    private var cachedHeight = -1
    private var cachedItemCount = -1
    private var cachedIconSizeDp = -1f
    private var cachedInsets = Rect(0, 0, 0, 0)

    fun getOrCompute(
        context: Context,
        availableWidth: Int,
        availableHeight: Int,
        itemCount: Int,
        iconSizeDp: Float,
        insets: Rect
    ): Grid {
        val cached = grid
        if (cached != null &&
            cachedWidth == availableWidth &&
            cachedHeight == availableHeight &&
            cachedItemCount == itemCount &&
            cachedIconSizeDp == iconSizeDp &&
            cachedInsets == insets
        ) {
            return cached
        }
        val newGrid = UtilCalculator.calculateGrid(context, availableWidth, availableHeight, itemCount, iconSizeDp)
        grid = newGrid
        cachedWidth = availableWidth
        cachedHeight = availableHeight
        cachedItemCount = itemCount
        cachedIconSizeDp = iconSizeDp
        cachedInsets = Rect(insets)
        baseRects = buildBaseRects(newGrid, insets)
        recomputeCount++
        return newGrid
    }

    private fun buildBaseRects(grid: Grid, insets: Rect): Array<RectF> {
        val cellCount = grid.itemCountHorizontal * grid.itemCountVertical
        if (cellCount <= 0) return emptyArray()
        return Array(cellCount) { index ->
            val x = (index % grid.itemCountHorizontal).toFloat()
            val y = (index / grid.itemCountHorizontal).toFloat()
            val left = insets.left + (x + 1.0f) * grid.spacingHorizontal + x * grid.itemSize
            val top = insets.top + (y + 1.0f) * grid.spacingVertical + y * grid.itemSize
            RectF(left, top, left + grid.itemSize, top + grid.itemSize)
        }
    }

    fun clear() {
        grid = null
        baseRects = emptyArray()
    }
}
