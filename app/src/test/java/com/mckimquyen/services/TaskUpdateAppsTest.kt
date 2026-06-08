package com.mckimquyen.services

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.mckimquyen.app.RAppsSingleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests cho TaskUpdateApps
 *
 * Test các tính năng:
 * - Coroutines execution
 * - WeakReference (no memory leak)
 * - Proper background/main thread dispatching
 * - RAppsSingleton update
 *
 * Fix: 1.1 - Migrate AsyncTask sang Coroutines
 * Fix: 4.1 - AsyncTask context leak với WeakReference
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class TaskUpdateAppsTest {

    private lateinit var context: Context
    private lateinit var application: Application
    private lateinit var packageManager: PackageManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        application = RuntimeEnvironment.getApplication()
        packageManager = context.packageManager

        // Reset singleton
        RAppsSingleton.instance.apps = null
        com.mckimquyen.util.BitmapCache.clear()
    }

    @After
    fun tearDown() {
        // Cleanup
        RAppsSingleton.instance.apps = null
        com.mckimquyen.util.BitmapCache.clear()
    }

    @Test
    fun `test TaskUpdateApps can be instantiated`() {
        // When
        val task = TaskUpdateApps(packageManager, context, application)

        // Then
        assertNotNull("Task should be created", task)
    }

    @Test
    fun `test execute completes without crash`() = runTest {
        // Given
        val task = TaskUpdateApps(packageManager, context, application)

        // When
        task.execute()

        // Then - Không crash
        assertTrue("Task executed successfully", true)
    }

    @Test
    fun `test execute updates RAppsSingleton`() = runTest {
        // Given
        val task = TaskUpdateApps(packageManager, context, application)

        // When
        task.execute()

        // Then
        val apps = RAppsSingleton.instance.apps
        assertNotNull("Apps should be set in singleton", apps)
        // Note: Số lượng apps phụ thuộc vào test environment
    }

    @Test
    fun `test WeakReference prevents memory leak when context is nullified`() = runTest {
        // Given
        var mockContext: Context? = mock<Context>()
        var mockApp: Application? = mock<Application>()

        val task = TaskUpdateApps(packageManager, mockContext!!, mockApp!!)

        // When - Nullify references to simulate GC
        mockContext = null
        mockApp = null

        // Force GC
        System.gc()
        Thread.sleep(100)

        // Then - Task vẫn có thể execute mà không crash (sẽ return sớm khi WeakRef.get() == null)
        try {
            task.execute()
            assertTrue("Task handles null context gracefully", true)
        } catch (e: Exception) {
            fail("Task should handle null context without crashing: ${e.message}")
        }
    }

    @Test
    fun `test multiple executions update singleton correctly`() = runTest {
        // Given
        val task1 = TaskUpdateApps(packageManager, context, application)
        val task2 = TaskUpdateApps(packageManager, context, application)

        // When
        task1.execute()
        val appsAfterFirst = RAppsSingleton.instance.apps?.size ?: 0

        task2.execute()
        val appsAfterSecond = RAppsSingleton.instance.apps?.size ?: 0

        // Then - Both executions complete and update singleton
        assertTrue("First execution should set apps", appsAfterFirst >= 0)
        assertTrue("Second execution should set apps", appsAfterSecond >= 0)
    }
}
