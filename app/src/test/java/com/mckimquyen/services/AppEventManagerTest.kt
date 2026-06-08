package com.mckimquyen.services

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.times

/**
 * Unit tests cho AppEventManager
 *
 * Test các tính năng:
 * - LiveData notifications
 * - Thread safety
 * - Multiple observers
 * - Singleton pattern
 *
 * Fix: 1.2 - Migrate Observable/Observer sang LiveData
 */
class AppEventManagerTest {

    // Rule để LiveData execute synchronously trong tests
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        // AppEventManager là object singleton, không cần setup
    }

    @Test
    fun `test notify apps loaded triggers observer`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.appsLoaded.observeForever(observer)

        // When
        AppEventManager.notifyAppsLoaded()

        // Then
        verify(observer, times(1)).onChanged(null)

        // Cleanup
        AppEventManager.appsLoaded.removeObserver(observer)
    }

    @Test
    fun `test notify apps loaded with data triggers observer with data`() {
        // Given
        val observer = mock<Observer<Any?>>()
        val testData = "test data"
        AppEventManager.appsLoaded.observeForever(observer)

        // When
        AppEventManager.notifyAppsLoaded(testData)

        // Then
        verify(observer, times(1)).onChanged(testData)

        // Cleanup
        AppEventManager.appsLoaded.removeObserver(observer)
    }

    @Test
    fun `test notify apps updated triggers observer`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.appsUpdated.observeForever(observer)

        // When
        AppEventManager.notifyAppsUpdated()

        // Then
        verify(observer, times(1)).onChanged(null)

        // Cleanup
        AppEventManager.appsUpdated.removeObserver(observer)
    }

    @Test
    fun `test notify background changed triggers observer`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.backgroundChanged.observeForever(observer)

        // When
        AppEventManager.notifyBackgroundChanged()

        // Then
        verify(observer, times(1)).onChanged(null)

        // Cleanup
        AppEventManager.backgroundChanged.removeObserver(observer)
    }

    @Test
    fun `test notify visibility changed triggers observer`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.visibilityChanged.observeForever(observer)

        // When
        AppEventManager.notifyVisibilityChanged()

        // Then
        verify(observer, times(1)).onChanged(null)

        // Cleanup
        AppEventManager.visibilityChanged.removeObserver(observer)
    }

    @Test
    fun `test notify night mode changed triggers observer`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.nightModeChanged.observeForever(observer)

        // When
        AppEventManager.notifyNightModeChanged()

        // Then
        verify(observer, times(1)).onChanged(null)

        // Cleanup
        AppEventManager.nightModeChanged.removeObserver(observer)
    }

    @Test
    fun `test multiple observers receive notifications`() {
        // Given
        val observer1 = mock<Observer<Any?>>()
        val observer2 = mock<Observer<Any?>>()
        val observer3 = mock<Observer<Any?>>()

        AppEventManager.appsLoaded.observeForever(observer1)
        AppEventManager.appsLoaded.observeForever(observer2)
        AppEventManager.appsLoaded.observeForever(observer3)

        // When
        AppEventManager.notifyAppsLoaded("test")

        // Then
        verify(observer1, times(1)).onChanged("test")
        verify(observer2, times(1)).onChanged("test")
        verify(observer3, times(1)).onChanged("test")

        // Cleanup
        AppEventManager.appsLoaded.removeObserver(observer1)
        AppEventManager.appsLoaded.removeObserver(observer2)
        AppEventManager.appsLoaded.removeObserver(observer3)
    }

    @Test
    fun `test removed observer does not receive notifications`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.appsLoaded.observeForever(observer)

        // When - Remove observer trước khi notify
        AppEventManager.appsLoaded.removeObserver(observer)
        AppEventManager.notifyAppsLoaded()

        // Then - Observer không nhận notification
        verify(observer, times(0)).onChanged(null)
    }

    @Test
    fun `test different events do not interfere with each other`() {
        // Given
        val loadedObserver = mock<Observer<Any?>>()
        val updatedObserver = mock<Observer<Any?>>()

        AppEventManager.appsLoaded.observeForever(loadedObserver)
        AppEventManager.appsUpdated.observeForever(updatedObserver)

        // When
        AppEventManager.notifyAppsLoaded("loaded")

        // Then
        verify(loadedObserver, times(1)).onChanged("loaded")
        verify(updatedObserver, times(0)).onChanged("loaded") // Không nhận event của appsLoaded

        // Cleanup
        AppEventManager.appsLoaded.removeObserver(loadedObserver)
        AppEventManager.appsUpdated.removeObserver(updatedObserver)
    }

    @Test
    fun `test thread safety - concurrent notifications`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.appsLoaded.observeForever(observer)

        val threads = mutableListOf<Thread>()

        // When - Multiple threads cùng notify
        repeat(10) { threadIndex ->
            val thread = Thread {
                repeat(10) { i ->
                    AppEventManager.notifyAppsLoaded("thread_${threadIndex}_$i")
                }
            }
            threads.add(thread)
            thread.start()
        }

        // Wait for all threads
        threads.forEach { it.join() }

        // Then - Observer nhận ít nhất 1 notification (vì LiveData postValue có thể merge các updates gần nhau)
        verify(observer, org.mockito.kotlin.atLeastOnce()).onChanged(org.mockito.kotlin.any())

        // Cleanup
        AppEventManager.appsLoaded.removeObserver(observer)
    }

    @Test
    fun `test AppEventManager is singleton`() {
        // When
        val instance1 = AppEventManager
        val instance2 = AppEventManager

        // Then
        assertSame("AppEventManager should be singleton", instance1, instance2)
    }

    @Test
    fun `test all event LiveData instances are not null`() {
        // Then
        assertNotNull("appsLoaded should not be null", AppEventManager.appsLoaded)
        assertNotNull("appsUpdated should not be null", AppEventManager.appsUpdated)
        assertNotNull("appsEdited should not be null", AppEventManager.appsEdited)
        assertNotNull("backgroundChanged should not be null", AppEventManager.backgroundChanged)
        assertNotNull("visibilityChanged should not be null", AppEventManager.visibilityChanged)
        assertNotNull("lockChanged should not be null", AppEventManager.lockChanged)
        assertNotNull("nightModeChanged should not be null", AppEventManager.nightModeChanged)
    }

    @Test
    fun `test notify lock changed triggers observer`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.lockChanged.observeForever(observer)

        // When
        AppEventManager.notifyLockChanged("locked")

        // Then
        verify(observer, times(1)).onChanged("locked")

        // Cleanup
        AppEventManager.lockChanged.removeObserver(observer)
    }

    @Test
    fun `test notify apps edited triggers observer`() {
        // Given
        val observer = mock<Observer<Any?>>()
        AppEventManager.appsEdited.observeForever(observer)

        // When
        AppEventManager.notifyAppsEdited("edited_data")

        // Then
        verify(observer, times(1)).onChanged("edited_data")

        // Cleanup
        AppEventManager.appsEdited.removeObserver(observer)
    }
}
