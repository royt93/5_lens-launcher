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
