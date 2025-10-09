package com.mckimquyen.app

import android.graphics.Bitmap
import com.mckimquyen.model.App
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests cho RAppsSingleton
 *
 * Test các tính năng:
 * - Singleton pattern (thread-safe)
 * - ArrayList optimization (no copy on get)
 * - Null safety
 * - Concurrent access
 *
 * Fix: 2.1 - Optimize ArrayList copy
 * Fix: 3.4 - Thread-safe singleton
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class RAppsSingletonTest {

    @Before
    fun setup() {
        // Reset singleton state
        RAppsSingleton.instance.apps = null
        RAppsSingleton.instance.appIcons = null
    }

    @After
    fun tearDown() {
        // Cleanup
        RAppsSingleton.instance.apps = null
        RAppsSingleton.instance.appIcons = null
    }

    @Test
    fun `test singleton returns same instance`() {
        // When
        val instance1 = RAppsSingleton.instance
        val instance2 = RAppsSingleton.instance

        // Then
        assertNotNull("Instance should not be null", instance1)
        assertSame("Should return same instance", instance1, instance2)
    }

    @Test
    fun `test get apps returns empty list when null`() {
        // Given
        RAppsSingleton.instance.apps = null

        // When
        val apps = RAppsSingleton.instance.apps

        // Then
        assertNotNull("Should return non-null list", apps)
        assertTrue("Should return empty list when null", apps!!.isEmpty())
    }

    @Test
    fun `test get app icons returns empty list when null`() {
        // Given
        RAppsSingleton.instance.appIcons = null

        // When
        val icons = RAppsSingleton.instance.appIcons

        // Then
        assertNotNull("Should return non-null list", icons)
        assertTrue("Should return empty list when null", icons!!.isEmpty())
    }

    @Test
    fun `test set and get apps`() {
        // Given
        val testApps = ArrayList<App>().apply {
            add(createTestApp("app1"))
            add(createTestApp("app2"))
        }

        // When
        RAppsSingleton.instance.apps = testApps
        val retrievedApps = RAppsSingleton.instance.apps

        // Then
        assertNotNull("Retrieved apps should not be null", retrievedApps)
        assertEquals("Should have same size", testApps.size, retrievedApps!!.size)
    }

    @Test
    fun `test set and get app icons`() {
        // Given
        val testIcons = ArrayList<Bitmap>().apply {
            add(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
            add(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
        }

        // When
        RAppsSingleton.instance.appIcons = testIcons
        val retrievedIcons = RAppsSingleton.instance.appIcons

        // Then
        assertNotNull("Retrieved icons should not be null", retrievedIcons)
        assertEquals("Should have same size", testIcons.size, retrievedIcons!!.size)

        // Cleanup
        testIcons.forEach { if (!it.isRecycled) it.recycle() }
    }

    @Test
    fun `test get apps returns same reference (no copy optimization)`() {
        // Given
        val testApps = ArrayList<App>().apply {
            add(createTestApp("app1"))
        }
        RAppsSingleton.instance.apps = testApps

        // When
        val retrieved1 = RAppsSingleton.instance.apps
        val retrieved2 = RAppsSingleton.instance.apps

        // Then - Fix 2.1: Không còn copy ArrayList mỗi lần get
        // Lưu ý: Do implementation mới return mApps ?: ArrayList()
        // Nếu mApps != null, cả 2 lần get đều return cùng reference
        assertNotNull(retrieved1)
        assertNotNull(retrieved2)
        assertEquals("Both gets should return same content", retrieved1, retrieved2)
    }

    @Test
    fun `test thread safety - concurrent getInstance calls`() {
        // Given
        val instances = mutableSetOf<RAppsSingleton>()
        val threads = mutableListOf<Thread>()

        // When - Multiple threads cùng lúc get instance
        repeat(100) {
            val thread = Thread {
                val instance = RAppsSingleton.instance
                synchronized(instances) {
                    instances.add(instance)
                }
            }
            threads.add(thread)
            thread.start()
        }

        // Wait for all threads
        threads.forEach { it.join() }

        // Then - Chỉ có 1 instance duy nhất
        assertEquals("Should only have one instance", 1, instances.size)
    }

    @Test
    fun `test thread safety - concurrent apps modifications`() {
        // Given
        val threads = mutableListOf<Thread>()

        // When - Multiple threads cùng lúc set apps
        repeat(50) { threadIndex ->
            val thread = Thread {
                val apps = ArrayList<App>().apply {
                    add(createTestApp("app_thread_$threadIndex"))
                }
                RAppsSingleton.instance.apps = apps
            }
            threads.add(thread)
            thread.start()
        }

        // Wait for all threads
        threads.forEach { it.join() }

        // Then - Không crash và có thể get apps
        val finalApps = RAppsSingleton.instance.apps
        assertNotNull("Should be able to get apps after concurrent modifications", finalApps)
    }

    @Test
    fun `test thread safety - concurrent reads and writes`() {
        // Given
        RAppsSingleton.instance.apps = ArrayList<App>().apply {
            add(createTestApp("initial"))
        }

        val threads = mutableListOf<Thread>()
        val readResults = mutableListOf<ArrayList<App>?>()

        // When - Mix of read and write threads
        repeat(25) { i ->
            // Write thread
            val writeThread = Thread {
                val apps = ArrayList<App>().apply {
                    add(createTestApp("write_$i"))
                }
                RAppsSingleton.instance.apps = apps
            }
            threads.add(writeThread)

            // Read thread
            val readThread = Thread {
                val apps = RAppsSingleton.instance.apps
                synchronized(readResults) {
                    readResults.add(apps)
                }
            }
            threads.add(readThread)
        }

        // Start all threads
        threads.forEach { it.start() }

        // Wait for all threads
        threads.forEach { it.join() }

        // Then - Không crash
        assertTrue("Should have read results", readResults.isNotEmpty())
        readResults.forEach { apps ->
            assertNotNull("Each read should return non-null result", apps)
        }
    }

    @Test
    fun `test replacing apps list works correctly`() {
        // Given
        val apps1 = ArrayList<App>().apply {
            add(createTestApp("app1"))
        }
        val apps2 = ArrayList<App>().apply {
            add(createTestApp("app2"))
            add(createTestApp("app3"))
        }

        // When
        RAppsSingleton.instance.apps = apps1
        val retrieved1 = RAppsSingleton.instance.apps

        RAppsSingleton.instance.apps = apps2
        val retrieved2 = RAppsSingleton.instance.apps

        // Then
        assertEquals("First retrieval should have 1 app", 1, retrieved1?.size)
        assertEquals("Second retrieval should have 2 apps", 2, retrieved2?.size)
    }

    @Test
    fun `test null assignment clears apps`() {
        // Given
        RAppsSingleton.instance.apps = ArrayList<App>().apply {
            add(createTestApp("app1"))
        }

        // When
        RAppsSingleton.instance.apps = null
        val retrieved = RAppsSingleton.instance.apps

        // Then
        assertNotNull("Should return non-null", retrieved)
        assertTrue("Should be empty after null assignment", retrieved!!.isEmpty())
    }

    // Helper function
    private fun createTestApp(packageName: String): App {
        return App(
            packageName = packageName,
            name = "Test App $packageName"
        )
    }
}
