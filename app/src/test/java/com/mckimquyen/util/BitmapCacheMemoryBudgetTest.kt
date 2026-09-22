package com.mckimquyen.util

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * PERF-002: proves BitmapCache's budget is derived from the device's standard memory class
 * and a measured icon-size ceiling, not from Runtime.maxMemory() (which reflects the
 * manifest's largeHeap flag and would otherwise let an inflated heap silently inflate the
 * cache budget), and that trimToFraction only ever shrinks the cache, never grows it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class BitmapCacheMemoryBudgetTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private fun setMemoryClass(mb: Int) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        Shadows.shadowOf(activityManager).setMemoryClass(mb)
    }

    @Test
    fun `init sizes the cache as one-eighth of the standard memory class`() {
        setMemoryClass(128)
        BitmapCache.init(context)

        // heapBudget = 128MB*1024/8 = 16384KB; icon ceiling (1000 * 64KB = 64000KB) is larger,
        // so the heap-derived budget wins here.
        assertEquals(128 * 1024 / 8, BitmapCache.maxSizeKb())
    }

    @Test
    fun `init caps the budget at the icon-based ceiling regardless of an implausibly large memory class`() {
        setMemoryClass(4096) // simulates what a largeHeap-inflated Runtime.maxMemory() looked like

        BitmapCache.init(context)

        val iconSizeKb = (128 * 128 * 4) / 1024
        val expectedCeiling = iconSizeKb * 1000
        assertEquals(expectedCeiling, BitmapCache.maxSizeKb())
        assertTrue(
            "budget must not scale with an inflated heap",
            BitmapCache.maxSizeKb() < (4096 * 1024) / 8
        )
    }

    @Test
    fun `init floors the budget on a very low memory-class device instead of near-zero`() {
        setMemoryClass(8)

        BitmapCache.init(context)

        assertEquals(4 * 1024, BitmapCache.maxSizeKb())
    }

    @Test
    fun `trimToFraction reduces occupancy proportionally`() {
        setMemoryClass(256)
        BitmapCache.init(context)
        BitmapCache.clear()
        repeat(20) { i ->
            BitmapCache.put("app$i", Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888))
        }
        val before = BitmapCache.sizeKb()
        assertTrue("fixture must actually populate the cache", before > 0)

        BitmapCache.trimToFraction(0.5f)

        assertTrue(
            "trimToFraction(0.5) must not leave more than ~half the prior occupancy",
            BitmapCache.sizeKb() <= (before * 0.5f).toInt() + 1
        )
    }

    @Test
    fun `trimToFraction of 1 is a no-op and never grows the cache`() {
        setMemoryClass(256)
        BitmapCache.init(context)
        BitmapCache.clear()
        BitmapCache.put("app", Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888))
        val before = BitmapCache.sizeKb()

        BitmapCache.trimToFraction(1f)

        assertEquals(before, BitmapCache.sizeKb())
    }

    @Test
    fun `trimToFraction of 0 evicts everything, same as clear`() {
        setMemoryClass(256)
        BitmapCache.init(context)
        BitmapCache.clear()
        BitmapCache.put("app", Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888))

        BitmapCache.trimToFraction(0f)

        assertEquals(0, BitmapCache.sizeKb())
    }

    // UI-020 regression: this launcher renders every installed app on one unpaged grid (no
    // paging/scrolling), so the cache must hold as many icons as a real device can have
    // installed - the old fixed ceiling (300 icons at 192px = ~144KB each = ~42MB) was smaller
    // than a real 346-app device's needs on a flagship-class phone, causing a permanent
    // evict/reload/invalidate loop (visible as continuous flicker) for the overflow apps.
    @Test
    fun `a 346-app device on a flagship memory class caches every icon with none evicted`() {
        setMemoryClass(256) // realistic flagship-class standard memory class
        BitmapCache.init(context)
        BitmapCache.clear()

        val appCount = 346
        repeat(appCount) { i ->
            // 192x192 input (bigger than the new 128px target) exercises the real scale-down path.
            BitmapCache.put("app$i", Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888))
        }

        val missing = (0 until appCount).filter { BitmapCache.get("app$it") == null }
        assertTrue(
            "expected every one of $appCount icons to still be cached, but evicted: $missing",
            missing.isEmpty()
        )
    }
}
