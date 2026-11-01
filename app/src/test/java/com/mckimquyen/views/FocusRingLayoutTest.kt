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
    private val OVERLAP_EPSILON = 0.01f
    private val SCAN_STEP = 12f
    private val MIN_POSITIONS = 5000

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

    /**
     * Chips are drawn as circles, so a touch in the corner of a chip's square that lies outside its circle
     * must not select it. With the real dimensions (20dp chips, 10dp gap, 18dp icon x1.25, density 2.0) the
     * squares of neighbouring chips overlap even though the circles do not, so a square hit-test can pick
     * the wrong chip (or one the user is not touching).
     */
    @Test fun realDimensionChipsDoNotStealEachOthersCornerTouches() {
        val density = 2f
        val radius = 20f * density
        val iconRadius = 18f * density * FocusRingLayout.EXTRA_MAGNIFICATION / 2f
        val s = FocusRingLayout.computeSlots(360f, 700f, iconRadius, 3, radius, 10f * density, 720f, 1324f)
        // The corner shared by the squares of chip 0 and chip 1 lies in neither circle.
        val a = s[0].rect
        val b = s[1].rect
        val x = (maxOf(a.left, b.left) + minOf(a.right, b.right)) / 2f
        val y = (maxOf(a.top, b.top) + minOf(a.bottom, b.bottom)) / 2f
        assertTrue("test needs the squares to overlap to mean anything", android.graphics.RectF.intersects(a, b))
        assertEquals("a touch outside both circles selects nothing", -1, FocusRingLayout.chipAt(s, x, y))
    }

    /**
     * Clamping each chip on its own pulls the outer chips of a 3-chip arc toward the middle one when the held
     * icon sits in a side column, so their circles overlap and a touch between them is ambiguous. The whole
     * arc has to move as a group instead. Real dimensions again (BG6: density 2.0, 720x1324 viewport).
     */
    @Test fun threeChipCirclesNeverOverlapAnywhereOnARealViewport() {
        val density = 2f
        val radius = 20f * density
        val iconRadius = 18f * density * FocusRingLayout.EXTRA_MAGNIFICATION / 2f
        val width = 720f
        val height = 1324f
        var checked = 0
        var x = 0f
        while (x <= width) {
            var y = 0f
            while (y <= height) {
                val s = FocusRingLayout.computeSlots(x, y, iconRadius, 3, radius, 10f * density, width, height)
                for (i in s.indices) for (j in i + 1 until s.size) {
                    val dx = s[i].rect.centerX() - s[j].rect.centerX()
                    val dy = s[i].rect.centerY() - s[j].rect.centerY()
                    assertTrue(
                        "chips $i and $j overlap for an icon at ($x, $y): distance ${Math.hypot(dx.toDouble(), dy.toDouble())} < ${2 * radius}",
                        dx * dx + dy * dy >= (2 * radius) * (2 * radius) - OVERLAP_EPSILON
                    )
                }
                for (slot in s) {
                    assertTrue("chip must stay inside the viewport for ($x, $y)",
                        slot.rect.left >= -OVERLAP_EPSILON && slot.rect.right <= width + OVERLAP_EPSILON &&
                            slot.rect.top >= -OVERLAP_EPSILON && slot.rect.bottom <= height + OVERLAP_EPSILON)
                }
                checked++
                y += SCAN_STEP
            }
            x += SCAN_STEP
        }
        assertTrue("the scan must have visited many positions, visited $checked", checked > MIN_POSITIONS)
    }

    /**
     * A short viewport (landscape, split screen) can force the arc below the icon and still run past the
     * bottom edge. The group must slide up as one piece, keeping the spacing, rather than lose chips off-screen.
     */
    @Test fun aShortViewportSlidesTheWholeArcUpAndKeepsItsSpacing() {
        val density = 2f
        val radius = 20f * density
        val iconRadius = 18f * density * FocusRingLayout.EXTRA_MAGNIFICATION / 2f
        val height = 150f
        val s = FocusRingLayout.computeSlots(360f, 75f, iconRadius, 3, radius, 10f * density, 720f, height)
        for (slot in s) {
            assertTrue("chip bottom ${slot.rect.bottom} must stay inside $height", slot.rect.bottom <= height + OVERLAP_EPSILON)
            assertTrue("chip top ${slot.rect.top} must stay on screen", slot.rect.top >= -OVERLAP_EPSILON)
        }
        val orbit = iconRadius + 10f * density + radius
        assertEquals("the middle chip keeps its distance from the outer chips", orbit / 2f,
            kotlin.math.abs(s[1].rect.centerY() - s[0].rect.centerY()), 0.01f)
        assertEquals("outer chips stay level with each other", s[0].rect.centerY(), s[2].rect.centerY(), 0.01f)
    }

    @Test fun aTouchAtTheCentreOfEachChipSelectsThatChip() {
        val s = slots(3)
        for (slot in s) assertEquals(slot.index, FocusRingLayout.chipAt(s, slot.rect.centerX(), slot.rect.centerY()))
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
