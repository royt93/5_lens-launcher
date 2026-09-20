package com.mckimquyen.launcher

import android.content.res.Configuration
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.util.WindowSizeHelper
import com.mckimquyen.views.LensGridCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WindowSizeHelperTest {

    @Test
    fun testWidthSizeClassCompact() {
        assertEquals(WindowSizeHelper.WindowWidthSizeClass.COMPACT, WindowSizeHelper.computeWidthSizeClass(0))
        assertEquals(WindowSizeHelper.WindowWidthSizeClass.COMPACT, WindowSizeHelper.computeWidthSizeClass(360))
        assertEquals(WindowSizeHelper.WindowWidthSizeClass.COMPACT, WindowSizeHelper.computeWidthSizeClass(599))
    }

    @Test
    fun testWidthSizeClassMedium() {
        assertEquals(WindowSizeHelper.WindowWidthSizeClass.MEDIUM, WindowSizeHelper.computeWidthSizeClass(600))
        assertEquals(WindowSizeHelper.WindowWidthSizeClass.MEDIUM, WindowSizeHelper.computeWidthSizeClass(720))
        assertEquals(WindowSizeHelper.WindowWidthSizeClass.MEDIUM, WindowSizeHelper.computeWidthSizeClass(839))
    }

    @Test
    fun testWidthSizeClassExpanded() {
        assertEquals(WindowSizeHelper.WindowWidthSizeClass.EXPANDED, WindowSizeHelper.computeWidthSizeClass(840))
        assertEquals(WindowSizeHelper.WindowWidthSizeClass.EXPANDED, WindowSizeHelper.computeWidthSizeClass(1024))
        assertEquals(WindowSizeHelper.WindowWidthSizeClass.EXPANDED, WindowSizeHelper.computeWidthSizeClass(1920))
    }

    @Test
    fun testIsLandscape() {
        assertTrue(WindowSizeHelper.isLandscape(Configuration.ORIENTATION_LANDSCAPE))
        assertFalse(WindowSizeHelper.isLandscape(Configuration.ORIENTATION_PORTRAIT))
        assertFalse(WindowSizeHelper.isLandscape(Configuration.ORIENTATION_UNDEFINED))
    }

    @Test
    fun testIsFoldableOrTablet() {
        // Standard phone: 360 x 800 -> false
        assertFalse(WindowSizeHelper.isFoldableOrTablet(360, 800))
        assertFalse(WindowSizeHelper.isFoldableOrTablet(800, 360))

        // Boundary: 599 x 800 -> false
        assertFalse(WindowSizeHelper.isFoldableOrTablet(599, 800))

        // Tablet / Foldable unfolded: 600 x 960 -> true
        assertTrue(WindowSizeHelper.isFoldableOrTablet(600, 960))
        assertTrue(WindowSizeHelper.isFoldableOrTablet(960, 600))

        // Large tablet: 800 x 1280 -> true
        assertTrue(WindowSizeHelper.isFoldableOrTablet(800, 1280))
    }

    @Test
    fun testCalculateConstrainedContentWidth() {
        // Compact widths pass through unchanged
        assertEquals(360, WindowSizeHelper.calculateConstrainedContentWidth(360))
        assertEquals(600, WindowSizeHelper.calculateConstrainedContentWidth(600))
        assertEquals(720, WindowSizeHelper.calculateConstrainedContentWidth(720))

        // Expanded widths clamped to MAX_CONTENT_WIDTH_DP (720dp)
        assertEquals(720, WindowSizeHelper.calculateConstrainedContentWidth(840))
        assertEquals(720, WindowSizeHelper.calculateConstrainedContentWidth(1200))
    }

    @Test
    fun testCalculateAdaptiveColumns() {
        assertEquals(0, WindowSizeHelper.calculateAdaptiveColumns(360, 640, 0))
        assertEquals(1, WindowSizeHelper.calculateAdaptiveColumns(360, 640, 1))

        // Phone portrait: 360 x 640, 50 items -> columns should be fewer than landscape
        val portraitCols = WindowSizeHelper.calculateAdaptiveColumns(360, 640, 50)
        // Phone landscape: 640 x 360, 50 items -> columns should be greater than portrait
        val landscapeCols = WindowSizeHelper.calculateAdaptiveColumns(640, 360, 50)

        assertTrue("Landscape should have more columns than portrait", landscapeCols > portraitCols)
        assertTrue("Portrait columns should be positive", portraitCols > 0)
        assertTrue("Landscape columns should be positive", landscapeCols > 0)
    }

    @Test
    fun testLensGridCacheRecomputesOnOrientationChange() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cache = LensGridCache()
        val insets = Rect(0, 0, 0, 0)
        val itemCount = 60
        val iconSizeDp = 18f

        // Initial computation in portrait: 720 x 1600
        val portraitGrid = cache.getOrCompute(context, 720, 1600, itemCount, iconSizeDp, insets)
        assertNotNull(portraitGrid)
        assertEquals(1, cache.recomputeCount)
        val portraitHCount = portraitGrid.itemCountHorizontal

        // Rotate to landscape: 1600 x 720
        val landscapeGrid = cache.getOrCompute(context, 1600, 720, itemCount, iconSizeDp, insets)
        assertNotNull(landscapeGrid)
        assertEquals(2, cache.recomputeCount)
        val landscapeHCount = landscapeGrid.itemCountHorizontal

        // Landscape should have significantly more horizontal items
        assertTrue(
            "Landscape itemCountHorizontal ($landscapeHCount) should exceed portrait ($portraitHCount)",
            landscapeHCount > portraitHCount
        )

        // Same dimensions hit cache without recompute
        val cachedGrid = cache.getOrCompute(context, 1600, 720, itemCount, iconSizeDp, insets)
        assertEquals(2, cache.recomputeCount)
        assertEquals(landscapeGrid, cachedGrid)
    }
}
