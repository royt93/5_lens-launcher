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

    // ==================================================================== FISH-009: Pinch Gesture State Machine

    @Test
    fun `resolvePointerDown from IDLE with 1 pointer stays IDLE`() {
        assertEquals(LensGestureState.IDLE, LensView.resolvePointerDown(LensGestureState.IDLE, 1))
    }

    @Test
    fun `resolvePointerDown from IDLE with 2 pointers transitions to PINCHING`() {
        assertEquals(LensGestureState.PINCHING, LensView.resolvePointerDown(LensGestureState.IDLE, 2))
    }

    @Test
    fun `resolvePointerDown from PANNING with 2 pointers stays PANNING (pan precedence)`() {
        assertEquals(LensGestureState.PANNING, LensView.resolvePointerDown(LensGestureState.PANNING, 2))
    }

    @Test
    fun `resolvePointerDown from PINCHING with 2 or more pointers stays PINCHING`() {
        assertEquals(LensGestureState.PINCHING, LensView.resolvePointerDown(LensGestureState.PINCHING, 2))
        assertEquals(LensGestureState.PINCHING, LensView.resolvePointerDown(LensGestureState.PINCHING, 3))
    }

    @Test
    fun `resolvePointerDown from PINCH_RELEASE with 2 pointers re-enters PINCHING`() {
        assertEquals(LensGestureState.PINCHING, LensView.resolvePointerDown(LensGestureState.PINCH_RELEASE, 2))
    }

    @Test
    fun `resolvePointerUp from PINCHING with 1 remaining pointer enters PINCH_RELEASE`() {
        assertEquals(LensGestureState.PINCH_RELEASE, LensView.resolvePointerUp(LensGestureState.PINCHING, 1))
    }

    @Test
    fun `resolvePointerUp from PINCHING with 0 remaining pointers returns to IDLE`() {
        assertEquals(LensGestureState.IDLE, LensView.resolvePointerUp(LensGestureState.PINCHING, 0))
    }

    @Test
    fun `resolvePointerUp from PINCH_RELEASE with 1 remaining pointer stays PINCH_RELEASE`() {
        assertEquals(LensGestureState.PINCH_RELEASE, LensView.resolvePointerUp(LensGestureState.PINCH_RELEASE, 1))
    }

    @Test
    fun `resolvePointerUp from PINCH_RELEASE with 0 remaining pointers returns to IDLE`() {
        assertEquals(LensGestureState.IDLE, LensView.resolvePointerUp(LensGestureState.PINCH_RELEASE, 0))
    }

    @Test
    fun `resolvePointerUp from PANNING with 1 remaining pointer stays PANNING`() {
        assertEquals(LensGestureState.PANNING, LensView.resolvePointerUp(LensGestureState.PANNING, 1))
    }

    @Test
    fun `resolvePointerUp from PANNING with 0 remaining pointers returns to IDLE`() {
        assertEquals(LensGestureState.IDLE, LensView.resolvePointerUp(LensGestureState.PANNING, 0))
    }

    // ==================================================================== FISH-009: Curvature Clamping & Math

    @Test
    fun `calculatePinchDistortion scales curvature proportionally`() {
        val result = LensView.calculatePinchDistortion(current = 2.5f, scaleFactor = 1.2f)
        assertEquals(3.0f, result, 0.001f)
    }

    @Test
    fun `calculatePinchDistortion clamps to minimum bound 0_5f`() {
        val result = LensView.calculatePinchDistortion(current = 0.6f, scaleFactor = 0.5f)
        assertEquals(0.5f, result, 0.001f)
    }

    @Test
    fun `calculatePinchDistortion clamps to maximum bound 5_0f`() {
        val result = LensView.calculatePinchDistortion(current = 4.5f, scaleFactor = 1.5f)
        assertEquals(5.0f, result, 0.001f)
    }

    @Test
    fun `calculatePinchDistortion ignores invalid scale factors`() {
        assertEquals(2.5f, LensView.calculatePinchDistortion(current = 2.5f, scaleFactor = Float.NaN), 0.001f)
        assertEquals(2.5f, LensView.calculatePinchDistortion(current = 2.5f, scaleFactor = -1.0f), 0.001f)
        assertEquals(2.5f, LensView.calculatePinchDistortion(current = 2.5f, scaleFactor = 0.0f), 0.001f)
    }

    // ==================================================================== FISH-009: App Launch Guard

    @Test
    fun `shouldAllowAppLaunch permits IDLE and PANNING, blocks PINCHING and PINCH_RELEASE`() {
        assertTrue(LensView.shouldAllowAppLaunch(LensGestureState.IDLE))
        assertTrue(LensView.shouldAllowAppLaunch(LensGestureState.PANNING))
        assertFalse(LensView.shouldAllowAppLaunch(LensGestureState.PINCHING))
        assertFalse(LensView.shouldAllowAppLaunch(LensGestureState.PINCH_RELEASE))
    }
}
