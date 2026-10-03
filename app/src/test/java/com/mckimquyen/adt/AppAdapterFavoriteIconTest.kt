package com.mckimquyen.adt

import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The favorite menu item's title flips with state; its icon must flip with it. */
class AppAdapterFavoriteIconTest {

    @Test
    fun `a favorite app shows the filled star and a normal app the outline star`() {
        assertEquals(R.drawable.ic_star_24dp, AppAdapter.favoriteMenuIconRes(true))
        assertEquals(R.drawable.ic_star_border_24dp, AppAdapter.favoriteMenuIconRes(false))
        assertNotEquals(AppAdapter.favoriteMenuIconRes(true), AppAdapter.favoriteMenuIconRes(false))
    }
}
