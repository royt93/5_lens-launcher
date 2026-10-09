package com.mckimquyen.views

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FISH-021: pure gating for Smart Focus on a lens that may be frozen. A frozen lens keeps the
 * positions the user froze, so Smart Focus must stand down for it regardless of its own toggle.
 */
class LensViewFreezeTest {

    @Test
    fun `smart focus runs when enabled and the lens is not frozen`() {
        assertTrue(LensView.shouldArrangeBySmartFocus(enabled = true, frozen = false, cols = 4, rows = 6))
    }

    @Test
    fun `a frozen lens is never rearranged even with smart focus on`() {
        assertFalse(LensView.shouldArrangeBySmartFocus(enabled = true, frozen = true, cols = 4, rows = 6))
    }

    @Test
    fun `smart focus off never arranges`() {
        assertFalse(LensView.shouldArrangeBySmartFocus(enabled = false, frozen = false, cols = 4, rows = 6))
    }

    @Test
    fun `an unmeasured grid never arranges`() {
        assertFalse(LensView.shouldArrangeBySmartFocus(enabled = true, frozen = false, cols = -1, rows = -1))
        assertFalse(LensView.shouldArrangeBySmartFocus(enabled = true, frozen = false, cols = 4, rows = 0))
        assertFalse(LensView.shouldArrangeBySmartFocus(enabled = true, frozen = false, cols = 0, rows = 6))
    }
}
