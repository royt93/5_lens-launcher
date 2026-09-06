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
 * Unit tests cho CORE-002 — icon cache identity and invalidation.
 *
 * Chứng minh:
 * 1. buildKey() phân biệt component, package version và icon-pack identity/version.
 * 2. buildKey() ổn định (deterministic) cho cùng input.
 * 3. retainKeys() evict đúng entry không còn hợp lệ, giữ lại entry hợp lệ.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class BitmapCacheKeyTest {

    @Before
    fun setup() {
        BitmapCache.clear()
    }

    @After
    fun tearDown() {
        BitmapCache.clear()
    }

    // ========================================================================
    // buildKey — identity composition
    // ========================================================================

    @Test
    fun `buildKey is stable for identical inputs`() {
        val key1 = BitmapCache.buildKey("com.app", ".MainActivity", "1:1000", "system")
        val key2 = BitmapCache.buildKey("com.app", ".MainActivity", "1:1000", "system")

        assertEquals("Same inputs must produce the same key", key1, key2)
    }

    @Test
    fun `buildKey differs for different component in same package`() {
        val key1 = BitmapCache.buildKey("com.app", ".MainActivity", "1:1000", "system")
        val key2 = BitmapCache.buildKey("com.app", ".SecondActivity", "1:1000", "system")

        assertNotEquals(
            "Two launcher activities in the same package must not collide",
            key1, key2
        )
    }

    @Test
    fun `buildKey differs after package version token changes`() {
        val keyBeforeUpdate = BitmapCache.buildKey("com.app", ".MainActivity", "1:1000", "system")
        val keyAfterUpdate = BitmapCache.buildKey("com.app", ".MainActivity", "2:2000", "system")

        assertNotEquals(
            "A package upgrade/update token change must invalidate the old key",
            keyBeforeUpdate, keyAfterUpdate
        )
    }

    @Test
    fun `buildKey differs after icon pack changes`() {
        val keyWithSystemIcons = BitmapCache.buildKey("com.app", ".MainActivity", "1:1000", "system")
        val keyWithIconPack = BitmapCache.buildKey("com.app", ".MainActivity", "1:1000", "com.iconpack@1:500")

        assertNotEquals(
            "Switching icon pack must invalidate the old key",
            keyWithSystemIcons, keyWithIconPack
        )
    }

    // ========================================================================
    // retainKeys — invalidation on package changed/removed/icon-pack switch
    // ========================================================================

    @Test
    fun `retainKeys evicts entries not in the valid set`() {
        val staleKey = BitmapCache.buildKey("com.removed", ".Main", "1:1000", "system")
        val keptKey = BitmapCache.buildKey("com.kept", ".Main", "1:1000", "system")
        BitmapCache.put(staleKey, Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
        BitmapCache.put(keptKey, Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))

        BitmapCache.retainKeys(setOf(keptKey))

        assertNull("Key no longer produced by the current app list must be evicted", BitmapCache.get(staleKey))
        assertNotNull("Key still produced by the current app list must survive", BitmapCache.get(keptKey))
    }

    @Test
    fun `retainKeys evicts stale key after a simulated package upgrade`() {
        val keyV1 = BitmapCache.buildKey("com.app", ".Main", "1:1000", "system")
        val keyV2 = BitmapCache.buildKey("com.app", ".Main", "2:2000", "system")
        BitmapCache.put(keyV1, Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))

        // New refresh generation only knows about the post-upgrade key.
        BitmapCache.put(keyV2, Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
        BitmapCache.retainKeys(setOf(keyV2))

        assertNull("Pre-upgrade bitmap must not remain reachable after retainKeys", BitmapCache.get(keyV1))
        assertNotNull("Post-upgrade bitmap must remain cached", BitmapCache.get(keyV2))
    }

    @Test
    fun `retainKeys with empty valid set clears the cache`() {
        val key = BitmapCache.buildKey("com.app", ".Main", "1:1000", "system")
        BitmapCache.put(key, Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))

        BitmapCache.retainKeys(emptySet())

        assertNull("Empty valid set means no current app owns any cached bitmap", BitmapCache.get(key))
    }

    @Test
    fun `retainKeys is a no-op when cache is empty`() {
        // Should not throw even though the cache has nothing to evict.
        BitmapCache.retainKeys(setOf("anything"))
        assertNotNull("Cache must remain queryable after retaining on an empty cache", BitmapCache.getCacheInfo())
    }
}
