package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI-022: pure, Context-free tests for the long-press-and-hold quick-actions gesture's
 * disambiguation logic — every transition the acceptance criteria names (idle→armed→
 * triggered/cancelled, "started as pan must not arm", "held past pan threshold").
 * No Robolectric needed, same pattern as `AppAdapterSelectionStateTest`.
 */
class LensViewGestureStateTest {

    // ==================================================================== exceedsTouchSlop

    @Test
    fun `distance under touch slop is not a pan`() {
        assertFalse(LensView.exceedsTouchSlop(dx = 2f, dy = 2f, touchSlop = 10f))
    }

    @Test
    fun `distance exactly at touch slop is not yet a pan`() {
        assertFalse(LensView.exceedsTouchSlop(dx = 10f, dy = 0f, touchSlop = 10f))
    }

    @Test
    fun `distance beyond touch slop is a pan`() {
        assertTrue(LensView.exceedsTouchSlop(dx = 11f, dy = 0f, touchSlop = 10f))
    }

    @Test
    fun `direction does not matter, only magnitude`() {
        assertTrue(LensView.exceedsTouchSlop(dx = -11f, dy = 0f, touchSlop = 10f))
        assertTrue(LensView.exceedsTouchSlop(dx = 0f, dy = -11f, touchSlop = 10f))
    }

    @Test
    fun `diagonal movement combines both axes`() {
        // 3-4-5 triangle: magnitude 5, must trip a slop of 4 even though neither axis alone does.
        assertFalse(LensView.exceedsTouchSlop(dx = 3f, dy = 4f, touchSlop = 5f))
        assertTrue(LensView.exceedsTouchSlop(dx = 3f, dy = 4f, touchSlop = 4.9f))
    }

    // ==================================================================== shouldTriggerLongPress

    @Test
    fun `armed, stationary, over an icon - triggers (the normal long-press case)`() {
        assertTrue(LensView.shouldTriggerLongPress(armed = true, moving = false, selectIndex = 0))
    }

    @Test
    fun `held past pan threshold - must not trigger even if still armed`() {
        assertFalse(LensView.shouldTriggerLongPress(armed = true, moving = true, selectIndex = 0))
    }

    @Test
    fun `started as pan (never armed) - must not trigger`() {
        assertFalse(LensView.shouldTriggerLongPress(armed = false, moving = false, selectIndex = 0))
    }

    @Test
    fun `already cancelled (up or cancel arrived first) - must not trigger`() {
        assertFalse(LensView.shouldTriggerLongPress(armed = false, moving = true, selectIndex = 0))
    }

    @Test
    fun `no icon under the touch point - must not trigger`() {
        assertFalse(LensView.shouldTriggerLongPress(armed = true, moving = false, selectIndex = -1))
    }

    @Test
    fun `every combination is enumerated and exactly one is true`() {
        val combinations = listOf(true, false).flatMap { armed ->
            listOf(true, false).flatMap { moving ->
                listOf(0, -1).map { selectIndex -> Triple(armed, moving, selectIndex) }
            }
        }
        val trueCount = combinations.count { (armed, moving, selectIndex) ->
            LensView.shouldTriggerLongPress(armed, moving, selectIndex)
        }
        assertEquals(
            "exactly the (armed=true, moving=false, selectIndex>=0) combination must trigger",
            1,
            trueCount
        )
    }
}
