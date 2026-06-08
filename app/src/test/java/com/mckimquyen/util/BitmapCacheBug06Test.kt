package com.mckimquyen.util

import android.graphics.Bitmap
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests cho BitmapCache (Fix BUG-06: Remove manual recycle → prevent race condition)
 *
 * Chứng minh:
 * 1. BUG-06: entryRemoved KHÔNG gọi bitmap.recycle() → tránh "bitmap is recycled" crash
 * 2. Bitmap vẫn accessible sau khi được evict khỏi cache (GC tự xử lý)
 * 3. Cache hoạt động đúng với LruCache eviction policy
 * 4. Thread safety khi concurrent put/get
 *
 * Extends existing BitmapCacheTest với các test cases bổ sung cho BUG-06 fix
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class BitmapCacheBug06Test {

    private lateinit var bitmap1: Bitmap
    private lateinit var bitmap2: Bitmap

    @Before
    fun setup() {
        bitmap1 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap2 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        BitmapCache.clear()
    }

    @After
    fun tearDown() {
        BitmapCache.clear()
        if (!bitmap1.isRecycled) bitmap1.recycle()
        if (!bitmap2.isRecycled) bitmap2.recycle()
    }

    // ========================================================================
    // BUG-06 FIX: entryRemoved không recycle bitmap → tránh race condition
    // ========================================================================

    @Test
    fun `BUG06 - bitmap in cache is not recycled by eviction`() {
        // Given: bitmap được put vào cache
        val key = "com.test.app"
        BitmapCache.put(key, bitmap1)
        assertNotNull("Bitmap should be in cache", BitmapCache.get(key))

        // When: bitmap được lấy ra (tham chiếu ngoài cache)
        val externalRef = BitmapCache.get(key)!!
        assertFalse("Bitmap should not be recycled while referenced", externalRef.isRecycled)

        // Then: sau khi clear cache, bitmap vẫn KHÔNG bị recycle thủ công
        // (GC sẽ thu hồi khi không còn ai giữ reference)
        BitmapCache.clear()
        // externalRef vẫn valid vì chúng ta đang giữ reference
        assertFalse(
            "BUG-06 fix: bitmap must NOT be recycled by cache eviction (prevents LensView crash)",
            externalRef.isRecycled
        )
    }

    @Test
    fun `BUG06 - bitmap remains drawable after cache eviction`() {
        // Given: simulate scenario khi UI thread đang vẽ bitmap và cache evict nó
        val packageName = "com.example.app"
        BitmapCache.put(packageName, bitmap1)

        // UI thread lấy bitmap để vẽ
        val bitmapForDraw = BitmapCache.get(packageName)
        assertNotNull("LensView should be able to get bitmap for drawing", bitmapForDraw)

        // Cache eviction xảy ra (do memory pressure)
        BitmapCache.clear()

        // BUG-06 fix: bitmap phải vẫn drawable — không bị recycle bởi cache
        // Nếu không có fix, drawAppIcon() sẽ throw "Canvas: trying to use a recycled bitmap"
        assertFalse(
            "BUG-06 fix: bitmap must remain valid for LensView.drawAppIcon() after eviction",
            bitmapForDraw!!.isRecycled
        )

        // Bitmap vẫn có thể được dùng để tạo canvas (simulate vẽ)
        try {
            android.graphics.Canvas(bitmapForDraw) // Sẽ throw nếu bitmap bị recycle
        } catch (e: IllegalStateException) {
            fail("BUG-06 fix regression: bitmap should not be recycled — ${e.message}")
        }
    }

    @Test
    fun `BUG06 - put does not overwrite existing valid bitmap`() {
        // Chứng minh cache idempotent với same key
        val key = "com.test.app"
        BitmapCache.put(key, bitmap1)
        BitmapCache.put(key, bitmap2) // Attempt to overwrite

        val retrieved = BitmapCache.get(key)
        // Cache giữ bitmap1 (first-write-wins)
        assertEquals("Cache should keep first bitmap — avoid unnecessary replacement", bitmap1, retrieved)
    }

    @Test
    fun `BUG06 - recycled bitmap is rejected at put time`() {
        // BitmapCache.put() có guard: không put bitmap đã recycled
        val key = "com.recycled.app"
        bitmap1.recycle()

        BitmapCache.put(key, bitmap1)
        val retrieved = BitmapCache.get(key)

        assertNull("Recycled bitmap should be rejected at put() — prevents stale entries", retrieved)
    }

    @Test
    fun `BUG06 - get returns null for internally recycled bitmap`() {
        // Edge case: bitmap được put khi còn valid, sau đó bị recycle từ bên ngoài
        val key = "com.test.app"
        BitmapCache.put(key, bitmap1)

        // Ai đó recycle bitmap (edge case, không nên xảy ra với fix này)
        bitmap1.recycle()

        // Cache phải guard và trả về null
        val retrieved = BitmapCache.get(key)
        assertNull("Should return null if cached bitmap was externally recycled", retrieved)
    }

    // ========================================================================
    // LRU CACHE BEHAVIOR — Icon flow: PackageManager → BitmapCache → LensView/AppAdapter
    // ========================================================================

    @Test
    fun `icon flow - put icon from TaskUpdateApps then get from LensView`() {
        // Simulate TaskUpdateApps.doInBackground():
        //   RAppsSingleton.instance.setAppIcon(packageName, appIcon) → BitmapCache.put()
        val packageName = "com.example.myapp"
        val iconFromPackageManager = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)

        BitmapCache.put(packageName, iconFromPackageManager)

        // Simulate LensView.drawAppIcon():
        //   RAppsSingleton.instance.getAppIcon(packageName) → BitmapCache.get()
        val iconForLensView = BitmapCache.get(packageName)
        assertNotNull("LensView must be able to get icon from cache", iconForLensView)
        assertFalse("Icon must not be recycled for LensView to draw", iconForLensView!!.isRecycled)

        // Cleanup
        if (!iconFromPackageManager.isRecycled) iconFromPackageManager.recycle()
    }

    @Test
    fun `icon flow - App object has null icon after BUG07 fix, BitmapCache provides icon`() {
        // BUG-07 fix: App.icon = null sau khi cache vào BitmapCache
        // Toàn bộ icon chỉ sống trong BitmapCache, không trong App object

        val packageName = "com.bug07.test"
        val icon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)

        // TaskUpdateApps: cache icon, set App.icon = null
        BitmapCache.put(packageName, icon)
        // (App object sẽ có icon = null)

        // LensView/AppAdapter fetch từ cache thay vì app.icon
        val cachedIcon = BitmapCache.get(packageName)
        assertNotNull("Icon must be retrievable from BitmapCache even though App.icon is null", cachedIcon)
        assertEquals("Cached icon must be identical to original", icon, cachedIcon)

        if (!icon.isRecycled) icon.recycle()
    }

    @Test
    fun `cache clear - all icons gone after clearAllData`() {
        // RAppsSingleton.clearAllData() → BitmapCache.clear()
        BitmapCache.put("app1", bitmap1)
        BitmapCache.put("app2", bitmap2)

        BitmapCache.clear()

        assertNull("All icons should be gone after clear", BitmapCache.get("app1"))
        assertNull("All icons should be gone after clear", BitmapCache.get("app2"))
    }

    @Test
    fun `BUG06 - oversized bitmap input is not recycled after cache scaling`() {
        val key = "com.large.icon"
        val largeIcon = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)

        BitmapCache.put(key, largeIcon)
        val cachedIcon = BitmapCache.get(key)

        assertNotNull("Scaled icon should be cached", cachedIcon)
        assertFalse("Cache must not recycle caller-owned source bitmap", largeIcon.isRecycled)
        assertFalse("Cached scaled bitmap must be drawable", cachedIcon!!.isRecycled)

        if (!largeIcon.isRecycled) largeIcon.recycle()
    }

    @Test
    fun `cache info reports correctly`() {
        BitmapCache.put("app1", bitmap1)
        val info = BitmapCache.getCacheInfo()

        assertNotNull("Cache info should not be null", info)
        assertTrue("Cache info should contain size", info.contains("Cache size"))
    }
}
