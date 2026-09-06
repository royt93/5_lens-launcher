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

        // heapBudget = 128MB*1024/8 = 16384KB; icon ceiling (300 * 144KB = 43200KB) is larger,
        // so the heap-derived budget wins here.
        assertEquals(128 * 1024 / 8, BitmapCache.maxSizeKb())
    }

    @Test
    fun `init caps the budget at the icon-based ceiling regardless of an implausibly large memory class`() {
        setMemoryClass(4096) // simulates what a largeHeap-inflated Runtime.maxMemory() looked like

        BitmapCache.init(context)

        val iconSizeKb = (192 * 192 * 4) / 1024
        val expectedCeiling = iconSizeKb * 300
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
}
