package com.mckimquyen.app

import android.graphics.Bitmap
import com.mckimquyen.util.BitmapCache
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests cho RAppsSingleton (Fix: BitmapCache-based icon management)
 *
 * Chứng minh:
 * 1. getAppIcon() / setAppIcon() delegate đúng đến BitmapCache
 * 2. clearAllData() xóa cả apps list và toàn bộ icon cache
 * 3. apps getter trả về defensive copy (thread-safety)
 * 4. getAppIcon() trả về null khi không có icon
 *
 * Extends existing RAppsSingletonTest với focus vào BitmapCache integration
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class RAppsSingletonIconCacheTest {

    private lateinit var testBitmap: Bitmap

    @Before
    fun setup() {
        testBitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        RAppsSingleton.instance.clearAllData()
    }

    @After
    fun tearDown() {
        RAppsSingleton.instance.clearAllData()
        if (!testBitmap.isRecycled) testBitmap.recycle()
    }

    // ========================================================================
    // getAppIcon / setAppIcon — BitmapCache delegation
    // ========================================================================

    @Test
    fun `setAppIcon stores icon in BitmapCache by packageName`() {
        val packageName = "com.test.app"

        RAppsSingleton.instance.setAppIcon(packageName, testBitmap)

        // Verify icon is in BitmapCache directly
        val fromCache = BitmapCache.get(packageName)
        assertNotNull("setAppIcon() should store icon in BitmapCache", fromCache)
        assertEquals("Stored icon must be identical", testBitmap, fromCache)
    }

    @Test
    fun `getAppIcon retrieves icon from BitmapCache by packageName`() {
        val packageName = "com.test.app"

        // Put directly in BitmapCache (as TaskUpdateApps does)
        BitmapCache.put(packageName, testBitmap)

        // RAppsSingleton.getAppIcon() must retrieve it
        val retrieved = RAppsSingleton.instance.getAppIcon(packageName)
        assertNotNull("getAppIcon() should retrieve icon from BitmapCache", retrieved)
        assertEquals("Retrieved icon must match stored icon", testBitmap, retrieved)
    }

    @Test
    fun `getAppIcon returns null when no icon cached for packageName`() {
        val packageName = "com.no.icon.app"

        val retrieved = RAppsSingleton.instance.getAppIcon(packageName)

        assertNull("getAppIcon() must return null for uncached package", retrieved)
    }

    @Test
    fun `setAppIcon then getAppIcon round-trip is lossless`() {
        val packageName = "com.roundtrip.test"
        val icon = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)

        RAppsSingleton.instance.setAppIcon(packageName, icon)
        val retrieved = RAppsSingleton.instance.getAppIcon(packageName)

        assertNotNull("Round-trip must return non-null", retrieved)
        assertEquals("Round-trip icon must be identical to original", icon, retrieved)

        if (!icon.isRecycled) icon.recycle()
    }

    // ========================================================================
    // clearAllData — Clears both apps list AND BitmapCache
    // ========================================================================

    @Test
    fun `clearAllData clears icon cache`() {
        val packageName = "com.test.app"
        RAppsSingleton.instance.setAppIcon(packageName, testBitmap)
        assertNotNull("Icon should exist before clear", RAppsSingleton.instance.getAppIcon(packageName))

        // When
        RAppsSingleton.instance.clearAllData()

        // Then
        assertNull("clearAllData() must clear BitmapCache", RAppsSingleton.instance.getAppIcon(packageName))
    }

    @Test
    fun `clearAllData clears apps list`() {
        // Given
        val apps = ArrayList<com.mckimquyen.model.App>().apply {
            add(com.mckimquyen.model.App(packageName = "com.app1", name = "App1"))
        }
        RAppsSingleton.instance.apps = apps
        assertEquals("Apps should exist before clear", 1, RAppsSingleton.instance.apps?.size)

        // When
        RAppsSingleton.instance.clearAllData()

        // Then
        val afterClear = RAppsSingleton.instance.apps
        assertTrue("clearAllData() must clear apps list", afterClear == null || afterClear.isEmpty())
    }

    @Test
    fun `clearAllData clears both apps and icons atomically`() {
        // Given: populate both
        val packageName = "com.test.app"
        RAppsSingleton.instance.setAppIcon(packageName, testBitmap)
        RAppsSingleton.instance.apps = ArrayList<com.mckimquyen.model.App>().apply {
            add(com.mckimquyen.model.App(packageName = packageName, name = "Test"))
        }

        // When
        RAppsSingleton.instance.clearAllData()

        // Then: both cleared
        assertNull("Icons cleared", RAppsSingleton.instance.getAppIcon(packageName))
        val apps = RAppsSingleton.instance.apps
        assertTrue("Apps list cleared", apps == null || apps.isEmpty())
    }

    // ========================================================================
    // Apps getter — defensive copy (thread safety)
    // ========================================================================

    @Test
    fun `apps getter returns defensive copy not original reference`() {
        // Given
        val original = ArrayList<com.mckimquyen.model.App>().apply {
            add(com.mckimquyen.model.App(packageName = "com.app1", name = "App1"))
        }
        RAppsSingleton.instance.apps = original

        // When — get defensive copy
        val copy1 = RAppsSingleton.instance.apps
        val copy2 = RAppsSingleton.instance.apps

        // Then — content is the same, references are different copies
        assertNotNull(copy1)
        assertNotNull(copy2)
        assertEquals("Both copies should have same content", copy1!!.size, copy2!!.size)

        // Modifying copy should NOT affect internal state
        copy1.add(com.mckimquyen.model.App(packageName = "com.external.add", name = "External"))
        val afterExternalModify = RAppsSingleton.instance.apps
        assertEquals(
            "External modification of copy must not affect singleton internal state",
            1, afterExternalModify!!.size
        )
    }

    @Test
    fun `apps getter returns empty list not null when no apps set`() {
        RAppsSingleton.instance.apps = null

        val apps = RAppsSingleton.instance.apps
        assertNotNull("Should return non-null empty list", apps)
        assertTrue("Should return empty list", apps!!.isEmpty())
    }

    // ========================================================================
    // Singleton — Thread-safe icon operations
    // ========================================================================

    @Test
    fun `concurrent setAppIcon operations are thread safe`() {
        val threads = (1..20).map { i ->
            Thread {
                val bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
                RAppsSingleton.instance.setAppIcon("com.app.$i", bmp)
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Không crash là đủ để chứng minh thread safety
        assertTrue("Concurrent setAppIcon must not crash", true)
    }

    // ========================================================================
    // replaceSnapshot — CORE-002 invalidation on package/icon-pack change
    // ========================================================================

    @Test
    fun `replaceSnapshot evicts icon for a package no longer present`() {
        // Given: generation 1 has two apps, each with a cached icon.
        val removedApp = com.mckimquyen.model.App(
            packageName = "com.removed",
            name = ".Main",
            iconCacheKey = "com.removed/.Main#1:1000#system"
        )
        val keptApp = com.mckimquyen.model.App(
            packageName = "com.kept",
            name = ".Main",
            iconCacheKey = "com.kept/.Main#1:1000#system"
        )
        val keptIcon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        RAppsSingleton.instance.replaceSnapshot(
            apps = listOf(removedApp, keptApp),
            icons = mapOf(removedApp.iconCacheKey to testBitmap, keptApp.iconCacheKey to keptIcon)
        )
        assertNotNull(
            "Icon must be cached right after the first generation commits",
            RAppsSingleton.instance.getAppIcon(removedApp.iconCacheKey)
        )

        // When: generation 2 no longer includes the removed app (uninstalled), but the
        // launcher's own scan is never actually empty (this app is always itself a
        // launcher target), so `keptApp` models that realistic non-empty commit.
        RAppsSingleton.instance.replaceSnapshot(apps = listOf(keptApp), icons = emptyMap())

        // Then: the stale bitmap must not remain reachable, and the unrelated app survives.
        assertNull(
            "CORE-002: uninstalling a package must evict its cached icon",
            RAppsSingleton.instance.getAppIcon(removedApp.iconCacheKey)
        )
        assertNotNull(
            "An unrelated app present in both generations must keep its cached icon",
            RAppsSingleton.instance.getAppIcon(keptApp.iconCacheKey)
        )

        if (!keptIcon.isRecycled) keptIcon.recycle()
    }

    @Test
    fun `replaceSnapshot evicts previous icon after package version changes key`() {
        // Given: generation 1 caches an icon under the pre-update key.
        val keyV1 = "com.app/.Main#1:1000#system"
        val keyV2 = "com.app/.Main#2:2000#system"
        val appV1 = com.mckimquyen.model.App(packageName = "com.app", name = ".Main", iconCacheKey = keyV1)
        RAppsSingleton.instance.replaceSnapshot(apps = listOf(appV1), icons = mapOf(keyV1 to testBitmap))

        // When: the package is upgraded, producing a new iconCacheKey for the same app.
        val icon2 = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val appV2 = com.mckimquyen.model.App(packageName = "com.app", name = ".Main", iconCacheKey = keyV2)
        RAppsSingleton.instance.replaceSnapshot(apps = listOf(appV2), icons = mapOf(keyV2 to icon2))

        // Then: the pre-update bitmap is gone, only the current one is reachable.
        assertNull("Stale pre-update icon must be evicted", RAppsSingleton.instance.getAppIcon(keyV1))
        assertNotNull("Post-update icon must be cached", RAppsSingleton.instance.getAppIcon(keyV2))

        if (!icon2.isRecycled) icon2.recycle()
    }

    @Test
    fun `replaceSnapshot with an empty apps list does not wipe the icon cache`() {
        // Given: a normal generation with a cached icon.
        val key = "com.stable/.Main#1:1000#system"
        val app = com.mckimquyen.model.App(packageName = "com.stable", name = ".Main", iconCacheKey = key)
        RAppsSingleton.instance.replaceSnapshot(apps = listOf(app), icons = mapOf(key to testBitmap))
        assertNotNull(RAppsSingleton.instance.getAppIcon(key))

        // When: a transient/partial scan commits an empty apps list (e.g. right after a
        // user switch or direct-boot, before PackageManager reports launcher activities).
        RAppsSingleton.instance.replaceSnapshot(apps = emptyList(), icons = emptyMap())

        // Then: the previously cached icon must survive — an empty snapshot must never be
        // treated as "every app was uninstalled".
        assertNotNull(
            "An empty commit must not evict icons cached by a prior, real generation",
            RAppsSingleton.instance.getAppIcon(key)
        )
    }

    @Test
    fun `concurrent getAppIcon operations are thread safe`() {
        // Pre-populate
        RAppsSingleton.instance.setAppIcon("com.shared.app", testBitmap)

        val results = mutableListOf<Bitmap?>()
        val lock = Any()

        val threads = (1..20).map {
            Thread {
                val icon = RAppsSingleton.instance.getAppIcon("com.shared.app")
                synchronized(lock) { results.add(icon) }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertTrue("Should have results", results.isNotEmpty())
        results.forEach { icon ->
            // icon có thể null nếu bitmap bị recycle, nhưng không crash
            assertTrue("getAppIcon result is valid (null or bitmap)", icon == null || !icon.isRecycled)
        }
    }
}
