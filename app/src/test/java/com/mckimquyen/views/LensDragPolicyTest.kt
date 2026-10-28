package com.mckimquyen.views

import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** FISH-038b: the pure decisions behind "hold, then drag onto another lens". */
class LensDragPolicyTest {
    private fun lens(id: String) = LensWorkspace(id, id.uppercase(), 0, 0L, null)

    @Test
    fun `drag starts only for a held icon on a multi lens install that moved past slop`() {
        assertTrue(LensDragPolicy.shouldStartDrag(holdTriggered = true, hasOtherLenses = true, movedPastSlop = true))
    }

    @Test
    fun `drag does not start when any one condition is missing`() {
        assertFalse(LensDragPolicy.shouldStartDrag(holdTriggered = false, hasOtherLenses = true, movedPastSlop = true))
        assertFalse(LensDragPolicy.shouldStartDrag(holdTriggered = true, hasOtherLenses = false, movedPastSlop = true))
        assertFalse(LensDragPolicy.shouldStartDrag(holdTriggered = true, hasOtherLenses = true, movedPastSlop = false))
    }

    @Test
    fun `releasing a held icon without a drag opens the menu on a multi lens install`() {
        assertTrue(LensDragPolicy.shouldOpenMenuOnRelease(holdTriggered = true, dragStarted = false, hasOtherLenses = true))
    }

    @Test
    fun `release opens no menu after a drag, without a hold, or on a single lens`() {
        assertFalse(LensDragPolicy.shouldOpenMenuOnRelease(holdTriggered = true, dragStarted = true, hasOtherLenses = true))
        assertFalse(LensDragPolicy.shouldOpenMenuOnRelease(holdTriggered = false, dragStarted = false, hasOtherLenses = true))
        assertFalse(LensDragPolicy.shouldOpenMenuOnRelease(holdTriggered = true, dragStarted = false, hasOtherLenses = false))
    }

    @Test
    fun `timeout opens the menu only on a single lens install`() {
        assertTrue(LensDragPolicy.shouldOpenMenuOnTimeout(hasOtherLenses = false))
        assertFalse(LensDragPolicy.shouldOpenMenuOnTimeout(hasOtherLenses = true))
    }

    @Test
    fun `a pointer in the left edge zone scrolls left`() {
        assertEquals(-20, LensDragPolicy.edgeScrollDelta(10f, 1000, 100, 20))
        assertEquals(-20, LensDragPolicy.edgeScrollDelta(100f, 1000, 100, 20))
    }

    @Test
    fun `a pointer in the right edge zone scrolls right`() {
        assertEquals(20, LensDragPolicy.edgeScrollDelta(990f, 1000, 100, 20))
        assertEquals(20, LensDragPolicy.edgeScrollDelta(900f, 1000, 100, 20))
    }

    @Test
    fun `a pointer away from both edges does not scroll`() {
        assertEquals(0, LensDragPolicy.edgeScrollDelta(500f, 1000, 100, 20))
        assertEquals(0, LensDragPolicy.edgeScrollDelta(101f, 1000, 100, 20))
        assertEquals(0, LensDragPolicy.edgeScrollDelta(899f, 1000, 100, 20))
    }

    @Test
    fun `a viewport narrower than both edge zones still scrolls toward the nearer edge`() {
        assertEquals(-20, LensDragPolicy.edgeScrollDelta(10f, 100, 80, 20))
        assertEquals(20, LensDragPolicy.edgeScrollDelta(90f, 100, 80, 20))
    }

    @Test
    fun `invalid scroll input produces no movement`() {
        assertEquals(0, LensDragPolicy.edgeScrollDelta(Float.NaN, 1000, 100, 20))
        assertEquals(0, LensDragPolicy.edgeScrollDelta(10f, 1000, 0, 20))
        assertEquals(0, LensDragPolicy.edgeScrollDelta(10f, 1000, 100, 0))
    }

    @Test
    fun `an empty viewport never scrolls`() {
        assertEquals(0, LensDragPolicy.edgeScrollDelta(0f, 0, 100, 20))
    }

    @Test
    fun `targets exclude the source lens and keep order`() {
        val all = listOf(lens("a"), lens("b"), lens("c"))
        assertEquals(listOf("a", "c"), LensDragPolicy.targets(all, "b").map { it.id })
    }

    @Test
    fun `targets list every lens when the source is unknown and nothing when it is the only one`() {
        val all = listOf(lens("a"), lens("b"))
        assertEquals(listOf("a", "b"), LensDragPolicy.targets(all, "gone").map { it.id })
        assertTrue(LensDragPolicy.targets(listOf(lens("a")), "a").isEmpty())
        assertTrue(LensDragPolicy.targets(emptyList(), "a").isEmpty())
    }
}
