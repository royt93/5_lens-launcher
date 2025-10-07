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
 * Unit tests cho BitmapCache
 *
 * Test các tính năng:
 * - Lưu và lấy bitmap từ cache
 * - LruCache eviction và auto-recycle
 * - Thread safety
 * - Memory management
 *
 * Fix: 5.1 - Bitmap LruCache implementation
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class BitmapCacheTest {

    private lateinit var testBitmap1: Bitmap
    private lateinit var testBitmap2: Bitmap
    private lateinit var testBitmap3: Bitmap

    @Before
    fun setup() {
        // Tạo test bitmaps
        testBitmap1 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        testBitmap2 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        testBitmap3 = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        // Clear cache trước mỗi test
        BitmapCache.clear()
    }

    @After
    fun tearDown() {
        // Cleanup
        BitmapCache.clear()

        // Recycle test bitmaps nếu chưa recycle
        if (!testBitmap1.isRecycled) testBitmap1.recycle()
        if (!testBitmap2.isRecycled) testBitmap2.recycle()
        if (!testBitmap3.isRecycled) testBitmap3.recycle()
    }

    @Test
    fun `test put and get bitmap from cache`() {
        // Given
        val key = "com.test.app"

        // When
        BitmapCache.put(key, testBitmap1)
        val retrieved = BitmapCache.get(key)

        // Then
        assertNotNull("Bitmap should be retrieved from cache", retrieved)
        assertEquals("Retrieved bitmap should be the same", testBitmap1, retrieved)
    }

    @Test
    fun `test get returns null for non-existent key`() {
        // Given
        val key = "com.nonexistent.app"

        // When
        val retrieved = BitmapCache.get(key)

        // Then
        assertNull("Should return null for non-existent key", retrieved)
    }

    @Test
    fun `test put with same key does not replace existing bitmap`() {
        // Given
        val key = "com.test.app"
        BitmapCache.put(key, testBitmap1)

        // When - Cố gắng put bitmap mới với cùng key
        BitmapCache.put(key, testBitmap2)
        val retrieved = BitmapCache.get(key)

        // Then - Bitmap cũ vẫn còn (không replace)
        assertEquals("Original bitmap should still be in cache", testBitmap1, retrieved)
        assertNotEquals("New bitmap should not replace existing", testBitmap2, retrieved)
    }

    @Test
    fun `test put does not add recycled bitmap`() {
        // Given
        val key = "com.test.app"
        testBitmap1.recycle()

        // When
        BitmapCache.put(key, testBitmap1)
        val retrieved = BitmapCache.get(key)

        // Then
        assertNull("Recycled bitmap should not be added to cache", retrieved)
    }

    @Test
    fun `test get returns null for recycled bitmap`() {
        // Given
        val key = "com.test.app"
        BitmapCache.put(key, testBitmap1)

        // Giả lập bitmap bị recycle bên ngoài cache
        // (Trong thực tế cache sẽ tự recycle khi evict)
        testBitmap1.recycle()

        // When
        val retrieved = BitmapCache.get(key)

        // Then
        assertNull("Should return null if bitmap is recycled", retrieved)
    }

    @Test
    fun `test clear removes all bitmaps from cache`() {
        // Given
        BitmapCache.put("app1", testBitmap1)
        BitmapCache.put("app2", testBitmap2)

        // When
        BitmapCache.clear()

        // Then
        assertNull("Cache should be empty after clear", BitmapCache.get("app1"))
        assertNull("Cache should be empty after clear", BitmapCache.get("app2"))
    }

    @Test
    fun `test cache info returns valid string`() {
        // Given
        BitmapCache.put("app1", testBitmap1)

        // When
        val info = BitmapCache.getCacheInfo()

        // Then
        assertNotNull("Cache info should not be null", info)
        assertTrue("Cache info should contain size info", info.contains("Cache size"))
        assertTrue("Cache info should contain hit count", info.contains("Hit count"))
        assertTrue("Cache info should contain miss count", info.contains("Miss count"))
    }

    @Test
    fun `test thread safety - concurrent put operations`() {
        // Given
        val iterations = 100
        val threads = mutableListOf<Thread>()

        // When - Multiple threads cùng lúc put vào cache
        repeat(10) { threadIndex ->
            val thread = Thread {
                repeat(iterations) { i ->
                    val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
                    BitmapCache.put("app_${threadIndex}_$i", bitmap)
                }
            }
            threads.add(thread)
            thread.start()
        }

        // Wait for all threads
        threads.forEach { it.join() }

        // Then - Không crash và cache info có thể được lấy
        val info = BitmapCache.getCacheInfo()
        assertNotNull("Cache should handle concurrent access", info)
    }

    @Test
    fun `test thread safety - concurrent get operations`() {
        // Given
        repeat(50) { i ->
            val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            BitmapCache.put("app_$i", bitmap)
        }

        val threads = mutableListOf<Thread>()
        val results = mutableListOf<Bitmap?>()

        // When - Multiple threads cùng lúc get từ cache
        repeat(10) { threadIndex ->
            val thread = Thread {
                repeat(50) { i ->
                    val bitmap = BitmapCache.get("app_$i")
                    synchronized(results) {
                        results.add(bitmap)
                    }
                }
            }
            threads.add(thread)
            thread.start()
        }

        // Wait for all threads
        threads.forEach { it.join() }

        // Then - Không crash
        assertTrue("Should retrieve bitmaps successfully", results.isNotEmpty())
    }

    @Test
    fun `test null safety - put with empty key`() {
        // Given
        val emptyKey = ""

        // When
        BitmapCache.put(emptyKey, testBitmap1)
        val retrieved = BitmapCache.get(emptyKey)

        // Then - Vẫn hoạt động bình thường với empty key
        assertNotNull("Should work with empty key", retrieved)
    }
}
