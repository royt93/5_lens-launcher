package com.mckimquyen.views

import android.graphics.Rect
import com.mckimquyen.util.UtilCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * PERF-001: proves LensGridCache actually reuses the computed grid/base-rects across frames with
 * unchanged inputs, recomputes on every kind of real change, and produces geometry numerically
 * identical to the un-cached UtilCalculator.calculateGrid call it replaces in LensView's hot path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LensGridCacheTest {

    private lateinit var cache: LensGridCache
    private val noInsets = Rect(0, 0, 0, 0)

    @Before
    fun setup() {
        cache = LensGridCache()
    }

    @Test
    fun recomputesOnceThenReusesForIdenticalInputs() {
        val context = RuntimeEnvironment.getApplication()
        val first = cache.getOrCompute(context, 1080, 1920, 300, 18.0f, noInsets)
        val second = cache.getOrCompute(context, 1080, 1920, 300, 18.0f, noInsets)
        val third = cache.getOrCompute(context, 1080, 1920, 300, 18.0f, noInsets)

        assertSame(first, second)
        assertSame(second, third)
        assertEquals(1, cache.recomputeCount)
    }

    @Test
    fun recomputesWhenWidthHeightItemCountIconSizeOrInsetsChange() {
        val context = RuntimeEnvironment.getApplication()
        cache.getOrCompute(context, 1080, 1920, 300, 18.0f, noInsets)
        assertEquals(1, cache.recomputeCount)

        cache.getOrCompute(context, 1440, 1920, 300, 18.0f, noInsets)
        assertEquals(2, cache.recomputeCount)

        cache.getOrCompute(context, 1440, 2560, 300, 18.0f, noInsets)
        assertEquals(3, cache.recomputeCount)

        cache.getOrCompute(context, 1440, 2560, 150, 18.0f, noInsets)
        assertEquals(4, cache.recomputeCount)

        cache.getOrCompute(context, 1440, 2560, 150, 24.0f, noInsets)
        assertEquals(5, cache.recomputeCount)

        cache.getOrCompute(context, 1440, 2560, 150, 24.0f, Rect(0, 100, 0, 0))
        assertEquals(6, cache.recomputeCount)

        // Repeating the last combination must not trigger another recompute.
        cache.getOrCompute(context, 1440, 2560, 150, 24.0f, Rect(0, 100, 0, 0))
        assertEquals(6, cache.recomputeCount)
    }

    @Test
    fun cachedGridMatchesUncachedCalculationExactly() {
        val context = RuntimeEnvironment.getApplication()
        val cached = cache.getOrCompute(context, 1080, 1920, 237, 20.0f, noInsets)
        val expected = UtilCalculator.calculateGrid(context, 1080, 1920, 237, 20.0f)

        assertEquals(expected.itemCount, cached.itemCount)
        assertEquals(expected.itemCountHorizontal, cached.itemCountHorizontal)
        assertEquals(expected.itemCountVertical, cached.itemCountVertical)
        assertEquals(expected.itemSize, cached.itemSize, 0.0001f)
        assertEquals(expected.spacingHorizontal, cached.spacingHorizontal, 0.0001f)
        assertEquals(expected.spacingVertical, cached.spacingVertical, 0.0001f)
    }

    @Test
    fun baseRectsCoverTheFullGridAndRespectInsets() {
        val context = RuntimeEnvironment.getApplication()
        val insets = Rect(10, 20, 0, 0)
        val grid = cache.getOrCompute(context, 1000, 1000, 50, 18.0f, insets)

        assertEquals(grid.itemCountHorizontal * grid.itemCountVertical, cache.baseRects.size)
        val firstCell = cache.baseRects[0]
        // First cell's top-left must be offset by exactly one spacing unit plus the insets.
        assertEquals(insets.left + grid.spacingHorizontal, firstCell.left, 0.0001f)
        assertEquals(insets.top + grid.spacingVertical, firstCell.top, 0.0001f)
        assertEquals(grid.itemSize, firstCell.width(), 0.0001f)
        assertEquals(grid.itemSize, firstCell.height(), 0.0001f)
    }

    @Test
    fun clearDropsTheCachedGridAndBaseRects() {
        val context = RuntimeEnvironment.getApplication()
        cache.getOrCompute(context, 1080, 1920, 300, 18.0f, noInsets)
        cache.clear()

        assertEquals(null, cache.grid)
        assertEquals(0, cache.baseRects.size)

        // A subsequent call must recompute (not silently reuse pre-clear state).
        cache.getOrCompute(context, 1080, 1920, 300, 18.0f, noInsets)
        assertEquals(2, cache.recomputeCount)
    }

    @Test
    fun frameBudgetsAreOrderedFastestFirstAndPositive() {
        assert(LensView.FRAME_BUDGET_120HZ_MS < LensView.FRAME_BUDGET_90HZ_MS)
        assert(LensView.FRAME_BUDGET_90HZ_MS < LensView.FRAME_BUDGET_60HZ_MS)
        assert(LensView.FRAME_BUDGET_120HZ_MS > 0f)
        assert(LensView.LARGE_APP_LIST_BENCHMARK_SIZE > 0)
    }
}
