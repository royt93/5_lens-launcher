package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** FISH-037: pure geometry of the contact sheet, including the size and memory limits. */
class PolaroidContactSheetLayoutTest {
    private fun sheet(count: Int, w: Int = 1080, h: Int = 1944) = PolaroidContactSheetLayout.compute(count, w, h)

    @Test fun twoColumnsAndCeilRows() {
        for ((n, rows) in listOf(1 to 1, 2 to 1, 3 to 2, 4 to 2, 5 to 3, 6 to 3)) {
            val s = sheet(n)
            assertEquals("columns for $n", minOf(n, PolaroidContactSheetLayout.COLUMNS), s.columns)
            assertEquals("rows for $n", rows, s.rows)
            assertEquals("cells for $n", n, s.cells.size)
        }
    }

    @Test fun longestSideNeverExceedsTheCap() {
        for (n in 1..6) for ((w, h) in listOf(1080 to 1944, 3000 to 3000, 600 to 4000, 4000 to 600, 8000 to 8000)) {
            val s = sheet(n, w, h)
            assertTrue("n=$n ${w}x$h -> ${s.width}x${s.height}", maxOf(s.width, s.height) <= PolaroidContactSheetLayout.MAX_SIDE_PX)
        }
    }

    @Test fun bitmapBytesNeverExceedTheMemoryCap() {
        for (n in 1..6) for ((w, h) in listOf(1080 to 1944, 3000 to 3000, 600 to 4000, 4000 to 600, 8000 to 8000)) {
            val s = sheet(n, w, h)
            val bytes = s.width.toLong() * s.height * 4
            assertTrue("n=$n ${w}x$h -> $bytes bytes", bytes <= PolaroidContactSheetLayout.MAX_BITMAP_BYTES)
        }
    }

    @Test fun cellsStayInsideTheSheetAboveTheFooterAndDoNotOverlap() {
        val s = sheet(6)
        assertEquals("first cell starts after the outer top/left gutter", PolaroidContactSheetLayout.GAP_PX, s.cells.first().left)
        assertEquals(PolaroidContactSheetLayout.GAP_PX, s.cells.first().top)
        for (c in s.cells) {
            assertTrue(c.left >= 0 && c.top >= 0)
            assertTrue("cell ${c.index} right edge", c.left + c.width <= s.width)
            assertTrue("cell ${c.index} bottom edge", c.top + c.height <= s.height - PolaroidContactSheetLayout.FOOTER_PX)
        }
        for (i in s.cells.indices) for (j in i + 1 until s.cells.size) {
            val a = s.cells[i]
            val b = s.cells[j]
            val apart = a.left + a.width <= b.left || b.left + b.width <= a.left ||
                a.top + a.height <= b.top || b.top + b.height <= a.top
            assertTrue("cells $i and $j overlap", apart)
        }
    }

    @Test fun cellsAreInReadingOrder() {
        val s = sheet(5)
        for (i in 1 until s.cells.size) {
            val prev = s.cells[i - 1]
            val cur = s.cells[i]
            assertTrue("cell $i must come after cell ${i - 1}", cur.top > prev.top || (cur.top == prev.top && cur.left > prev.left))
        }
    }

    @Test fun cellsKeepThePageAspectRatioWithinRounding() {
        val s = sheet(4, 1080, 1944)
        for (c in s.cells) assertEquals(1944f / 1080f, c.height.toFloat() / c.width, 0.05f)
    }

    @Test fun aSmallPageIsNotUpscaled() {
        val s = sheet(2, 200, 300)
        for (c in s.cells) {
            assertEquals(200, c.width)
            assertEquals(300, c.height)
        }
    }

    @Test fun degenerateInputIsClampedNotCrashing() {
        val empty = PolaroidContactSheetLayout.compute(0, 0, 0)
        assertTrue(empty.width >= 1 && empty.height >= 1)
        assertEquals(0, empty.cells.size)
        assertEquals(6, PolaroidContactSheetLayout.compute(99, 1080, 1944).cells.size)
        assertEquals(0, PolaroidContactSheetLayout.compute(-3, 1080, 1944).cells.size)
        assertTrue(PolaroidContactSheetLayout.compute(2, 0, 0).cells.all { it.width >= 1 && it.height >= 1 })
    }
}
