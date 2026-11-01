package com.mckimquyen.util

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

data class SheetCell(val index: Int, val left: Int, val top: Int, val width: Int, val height: Int)

data class SheetLayout(val width: Int, val height: Int, val columns: Int, val rows: Int, val cells: List<SheetCell>)

/**
 * FISH-037: pure geometry of the multi-lens contact sheet. Cells keep the lens page's aspect ratio and
 * are scaled down (never up) so the final bitmap respects both a longest-side cap and a memory cap.
 * The gaps and the footer are fixed pixels, so they are taken off the budget before the scale is chosen.
 */
object PolaroidContactSheetLayout {
    const val COLUMNS = 2
    const val MAX_LENSES = 6
    const val MAX_SIDE_PX = 4096
    const val MAX_BITMAP_BYTES = 64L * 1024 * 1024
    const val GAP_PX = 24
    const val FOOTER_PX = 96
    private const val BYTES_PER_PIXEL = 4
    private const val MIN_PX = 1

    fun compute(count: Int, pageWidth: Int, pageHeight: Int): SheetLayout {
        val n = count.coerceIn(0, MAX_LENSES)
        if (n == 0) return SheetLayout(MIN_PX, MIN_PX, 1, 0, emptyList())
        val pw = pageWidth.coerceAtLeast(MIN_PX)
        val ph = pageHeight.coerceAtLeast(MIN_PX)
        val columns = min(n, COLUMNS)
        val rows = ceil(n / COLUMNS.toDouble()).toInt()
        val fixedW = (columns + 1) * GAP_PX
        val fixedH = (rows + 1) * GAP_PX + FOOTER_PX

        // Largest cell scale for which the whole sheet still fits the side cap.
        val sideScale = min(
            (MAX_SIDE_PX - fixedW).toDouble() / (columns * pw),
            (MAX_SIDE_PX - fixedH).toDouble() / (rows * ph)
        )
        // Largest cell scale for which width * height * 4 still fits the memory cap (quadratic in scale).
        // ponytail: with MAX_SIDE_PX = 4096 the side cap already keeps the bitmap below ~65.5 MB, so this
        // branch cannot be the limiting one today; it stays as the guard that makes the 64 MB promise
        // independent of MAX_SIDE_PX. Re-check the margin (and add a test that makes it bind) if
        // MAX_SIDE_PX is ever raised, or the 4096 cap and the 64 MB cap will silently disagree.
        val maxPixels = MAX_BITMAP_BYTES.toDouble() / BYTES_PER_PIXEL
        val a = columns.toDouble() * pw * rows * ph
        val b = columns.toDouble() * pw * fixedH + rows.toDouble() * ph * fixedW
        val c = fixedW.toDouble() * fixedH - maxPixels
        val memoryScale = if (c >= 0) 0.0 else (-b + sqrt(b * b - 4 * a * c)) / (2 * a)

        val scale = minOf(1.0, sideScale, memoryScale).coerceAtLeast(0.0)
        val cellW = floor(pw * scale).toInt().coerceAtLeast(MIN_PX)
        val cellH = floor(ph * scale).toInt().coerceAtLeast(MIN_PX)
        val width = columns * cellW + fixedW
        val height = rows * cellH + fixedH
        val cells = (0 until n).map { i ->
            SheetCell(
                index = i,
                left = GAP_PX + (i % columns) * (cellW + GAP_PX),
                top = GAP_PX + (i / columns) * (cellH + GAP_PX),
                width = cellW,
                height = cellH
            )
        }
        return SheetLayout(width, height, columns, rows, cells)
    }
}
