package com.mckimquyen.services

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.times

/**
 * Unit tests cho Observable wrapper classes
 *
 * Test rằng các wrapper classes vẫn hoạt động và delegate đúng sang AppEventManager
 * Đảm bảo backward compatibility với code cũ
 *
 * Fix: 1.2 - Migrate Observable/Observer sang LiveData với backward compatibility
 */
class ObservableWrappersTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun `test LoadedObservable is singleton`() {
        // When
        val instance1 = LoadedObservable.instance
        val instance2 = LoadedObservable.instance

        // Then
        assertNotNull("Instance should not be null", instance1)
        assertSame("Should return same instance", instance1, instance2)
    }

    @Test
    fun `test LoadedObservable update delegates to AppEventManager`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.appsLoaded.observeForever(observer)

        // When
        LoadedObservable.instance.update()

        // Then - AppEventManager nhận notification
        verify(observer, times(1)).onChanged(null)

        // Cleanup
        AppEventManager.appsLoaded.removeObserver(observer)
    }

    @Test
    fun `test LoadedObservable updateValue with data delegates correctly`() {
        // Given
        val observer = mock<Observer<Any?>>()
        val testData = "test data"
        AppEventManager.appsLoaded.observeForever(observer)

        // When
        LoadedObservable.instance.updateValue(testData)

        // Then
        verify(observer, times(1)).onChanged(testData)

        // Cleanup
        AppEventManager.appsLoaded.removeObserver(observer)
    }

    @Test
    fun `test BackgroundChangedObservable delegates correctly`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.backgroundChanged.observeForever(observer)

        // When
        BackgroundChangedObservable.instance.update()

        // Then
        verify(observer, times(1)).onChanged(null)

        // Cleanup
        AppEventManager.backgroundChanged.removeObserver(observer)
    }

    @Test
    fun `test VisibilityChangedObservable delegates correctly`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.visibilityChanged.observeForever(observer)

        // When
        VisibilityChangedObservable.instance.update()

        // Then
        verify(observer, times(1)).onChanged(null)

        // Cleanup
        AppEventManager.visibilityChanged.removeObserver(observer)
    }

    @Test
    fun `test NightModeObservable delegates correctly`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.nightModeChanged.observeForever(observer)

        // When
        NightModeObservable.instance.update()

        // Then
        verify(observer, times(1)).onChanged(null)

        // Cleanup
        AppEventManager.nightModeChanged.removeObserver(observer)
    }

    @Test
    fun `test UpdatedObservable delegates correctly`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.appsUpdated.observeForever(observer)

        // When
        UpdatedObservable.instance.update()

        // Then
        verify(observer, times(1)).onChanged(null)

        // Cleanup
        AppEventManager.appsUpdated.removeObserver(observer)
    }

    @Test
    fun `test EditedObservable delegates correctly`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.appsEdited.observeForever(observer)

        // When
        EditedObservable.instance.update()

        // Then
        verify(observer, times(1)).onChanged(null)

        // Cleanup
        AppEventManager.appsEdited.removeObserver(observer)
    }

    @Test
    fun `test LockChangedObservable delegates correctly`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.lockChanged.observeForever(observer)

        // When
        LockChangedObservable.instance.update()

        // Then
        verify(observer, times(1)).onChanged(null)

        // Cleanup
        AppEventManager.lockChanged.removeObserver(observer)
    }

    @Test
    fun `test all Observable wrappers are thread-safe singletons`() {
        // Given
        val wrappers = listOf(
            { LoadedObservable.instance },
            { BackgroundChangedObservable.instance },
            { VisibilityChangedObservable.instance },
            { NightModeObservable.instance },
            { UpdatedObservable.instance },
            { EditedObservable.instance },
            { LockChangedObservable.instance }
        )

        // When/Then - Test each wrapper
        wrappers.forEach { getWrapper ->
            val threads = mutableListOf<Thread>()
            val instances = mutableSetOf<Any>()

            repeat(50) {
                val thread = Thread {
                    val instance = getWrapper()
                    synchronized(instances) {
                        instances.add(instance)
                    }
                }
                threads.add(thread)
                thread.start()
            }

            threads.forEach { it.join() }

            assertEquals("Each wrapper should only have one instance", 1, instances.size)
        }
    }

    @Test
    fun `test synchronized update is thread-safe`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.appsLoaded.observeForever(observer)

        val threads = mutableListOf<Thread>()

        // When - Multiple threads call update simultaneously
        repeat(20) { i ->
            val thread = Thread {
                LoadedObservable.instance.updateValue("thread_$i")
            }
            threads.add(thread)
            thread.start()
        }

        threads.forEach { it.join() }

        // Then - All updates are received
        verify(observer, times(20)).onChanged(org.mockito.kotlin.any())

        // Cleanup
        AppEventManager.appsLoaded.removeObserver(observer)
    }

    @Test
    fun `test wrapper and direct AppEventManager usage work together`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.appsLoaded.observeForever(observer)

        // When - Mix wrapper và direct AppEventManager calls
        LoadedObservable.instance.update()
        AppEventManager.notifyAppsLoaded("direct")
        LoadedObservable.instance.updateValue("wrapper")

        // Then - Tất cả đều trigger observer
        verify(observer, times(1)).onChanged(null) // từ update()
        verify(observer, times(1)).onChanged("direct") // từ AppEventManager
        verify(observer, times(1)).onChanged("wrapper") // từ updateValue()

        // Cleanup
        AppEventManager.appsLoaded.removeObserver(observer)
    }

    @Test
    fun `test all wrappers getInstance methods are thread-safe`() {
        // Test LoadedObservable
        testSingletonThreadSafety { LoadedObservable.instance }

        // Test BackgroundChangedObservable
        testSingletonThreadSafety { BackgroundChangedObservable.instance }

        // Test VisibilityChangedObservable
        testSingletonThreadSafety { VisibilityChangedObservable.instance }

        // Test NightModeObservable
        testSingletonThreadSafety { NightModeObservable.instance }

        // Test UpdatedObservable
        testSingletonThreadSafety { UpdatedObservable.instance }

        // Test EditedObservable
        testSingletonThreadSafety { EditedObservable.instance }

        // Test LockChangedObservable
        testSingletonThreadSafety { LockChangedObservable.instance }
    }

    private fun testSingletonThreadSafety(getInstance: () -> Any) {
        val instances = mutableSetOf<Any>()
        val threads = mutableListOf<Thread>()

        repeat(100) {
            val thread = Thread {
                val instance = getInstance()
                synchronized(instances) {
                    instances.add(instance)
                }
            }
            threads.add(thread)
            thread.start()
        }

        threads.forEach { it.join() }

        assertEquals("Should only create one instance", 1, instances.size)
    }
}
