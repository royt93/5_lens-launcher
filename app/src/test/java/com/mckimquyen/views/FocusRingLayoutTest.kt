package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** FISH-031: pure ring geometry. Robolectric only because [android.graphics.RectF] needs the framework. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class FocusRingLayoutTest {
    private val chipRadius = 20f
    private val gap = 10f
    private val iconRadius = 40f
    private val viewW = 1000f
    private val viewH = 2000f

    private fun slots(count: Int, cx: Float = 500f, cy: Float = 1000f) =
        FocusRingLayout.computeSlots(cx, cy, iconRadius, count, chipRadius, gap, viewW, viewH)

    @Test fun zeroChipsGiveNoSlots() {
        assertTrue(slots(0).isEmpty())
    }

    @Test fun oneTwoAndThreeChipsGiveThatManySlotsWithSequentialIndexes() {
        for (n in 1..3) assertEquals((0 until n).toList(), slots(n).map { it.index })
    }

    @Test fun everyChipIsASquareOfTwiceTheRadius() {
        for (slot in slots(3)) {
            assertEquals(2 * chipRadius, slot.rect.width(), 0.001f)
            assertEquals(2 * chipRadius, slot.rect.height(), 0.001f)
        }
    }

    @Test fun threeChipsDoNotOverlapEachOther() {
        val s = slots(3)
        for (i in s.indices) for (j in i + 1 until s.size) {
            assertFalse("chip $i overlaps chip $j", android.graphics.RectF.intersects(s[i].rect, s[j].rect))
        }
    }

    @Test fun chipsSitAboveTheIconWhenThereIsRoom() {
        for (slot in slots(3, cy = 1000f)) assertTrue(slot.rect.centerY() < 1000f)
    }

    @Test fun threeChipAnglesAreOrderedLeftToRightAcrossUpperArc() {
        val s = slots(3)
        assertTrue(s[0].rect.centerX() < s[1].rect.centerX())
        assertTrue(s[1].rect.centerX() < s[2].rect.centerX())
        assertEquals(-Math.PI.toFloat() * 5f / 6f, s[0].centerAngleRad, 0.001f)
        assertEquals(-Math.PI.toFloat() / 2f, s[1].centerAngleRad, 0.001f)
        assertEquals(-Math.PI.toFloat() / 6f, s[2].centerAngleRad, 0.001f)
    }

    @Test fun narrowViewportKeepsChipRectsValidAndClamped() {
        val s = FocusRingLayout.computeSlots(50f, 300f, 35f, 3, 24f, 8f, 80f, 500f)
        assertEquals(3, s.size)
        for (slot in s) {
            assertTrue(slot.rect.left >= 0f)
            assertTrue(slot.rect.right <= 80f)
            assertTrue(slot.rect.width() > 0f)
            assertTrue(slot.rect.height() > 0f)
        }
    }

    @Test fun nearTheTopEdgeTheArcFlipsBelowTheIconAndStaysOnScreen() {
        val s = slots(3, cy = 40f)
        for (slot in s) {
            assertTrue("chip must be below the icon centre", slot.rect.centerY() > 40f)
            assertTrue(slot.rect.top >= 0f)
        }
    }

    @Test fun nearSideEdgesChipsAreClampedInsideTheViewport() {
        for (cx in listOf(5f, viewW - 5f)) {
            for (slot in slots(3, cx = cx)) {
                assertTrue(slot.rect.left >= 0f)
                assertTrue(slot.rect.right <= viewW)
            }
        }
    }

    @Test fun chipAtReturnsTheSlotUnderTheFingerOrMinusOne() {
        val s = slots(3)
        for (slot in s) assertEquals(slot.index, FocusRingLayout.chipAt(s, slot.rect.centerX(), slot.rect.centerY()))
        assertEquals(-1, FocusRingLayout.chipAt(s, 500f, 1000f)) // the icon centre is not a chip
        assertEquals(-1, FocusRingLayout.chipAt(emptyList(), 500f, 1000f))
    }

    @Test fun slotsHaveFiniteCoordinates() {
        for (slot in slots(3)) {
            assertTrue(slot.rect.left.isFinite() && slot.rect.top.isFinite())
            assertTrue(slot.centerAngleRad.isFinite())
        }
    }
}
