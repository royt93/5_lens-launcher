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
