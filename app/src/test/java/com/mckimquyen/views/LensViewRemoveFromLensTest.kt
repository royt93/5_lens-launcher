package com.mckimquyen.views

import com.mckimquyen.util.LensAppScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FISH-021: pure gating for the grid's "Remove from this lens" entry. Same convention as the other
 * LensView companion rules (`shouldDrawAppNameLabel`): one function shared by the menu so the rule
 * cannot drift from what the test pins.
 */
class LensViewRemoveFromLensTest {

    @Test
    fun `offered on a lens that shows only selected apps`() {
        assertTrue(LensView.shouldOfferRemoveFromLens(LensAppScope.SELECTED))
    }

    @Test
    fun `not offered on a lens that shows every app`() {
        assertFalse(LensView.shouldOfferRemoveFromLens(LensAppScope.ALL))
    }

    @Test
    fun `not offered when the scope is unknown`() {
        assertFalse(LensView.shouldOfferRemoveFromLens(null))
    }
}
