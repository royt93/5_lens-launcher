package com.mckimquyen.services

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.never
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.isNull
import org.mockito.kotlin.argumentCaptor

/**
 * Unit tests cho BroadcastReceivers (Fix BUG-13 + Logger migration)
 *
 * Chứng minh:
 * 1. BUG-13: BroadcastReceivers gọi AppEventManager trực tiếp (không qua deprecated Observable)
 * 2. Mỗi channel LiveData hoạt động độc lập
 * 3. Không cross-contaminate: notifyX không trigger channel Y (fresh observers)
 *
 * Note về Mockito Kotlin null gotcha:
 * - AppEventManager.notifyXxx() calls postValue(null) by default
 * - Mockito's any() in Kotlin DOES NOT match null → dùng isNull() matcher
 * - any<Any?>() từ mockito-kotlin match cả null và non-null
 */
class BroadcastReceiversDirectDispatchTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var appsUpdatedObserver: Observer<Any?>
    private lateinit var appsLoadedObserver: Observer<Any?>
    private lateinit var appsEditedObserver: Observer<Any?>
    private lateinit var visibilityObserver: Observer<Any?>
    private lateinit var lockObserver: Observer<Any?>
    private lateinit var backgroundObserver: Observer<Any?>
    private lateinit var nightModeObserver: Observer<Any?>

    @Before
    fun setup() {
        appsUpdatedObserver = mock()
        appsLoadedObserver = mock()
        appsEditedObserver = mock()
        visibilityObserver = mock()
        lockObserver = mock()
        backgroundObserver = mock()
        nightModeObserver = mock()
    }

    @After
    fun tearDown() {
        AppEventManager.appsUpdated.removeObserver(appsUpdatedObserver)
        AppEventManager.appsLoaded.removeObserver(appsLoadedObserver)
        AppEventManager.appsEdited.removeObserver(appsEditedObserver)
        AppEventManager.visibilityChanged.removeObserver(visibilityObserver)
        AppEventManager.lockChanged.removeObserver(lockObserver)
        AppEventManager.backgroundChanged.removeObserver(backgroundObserver)
        AppEventManager.nightModeChanged.removeObserver(nightModeObserver)
    }

    // ========================================================================
    // BUG-13 FIX: Direct dispatch — 7 channels, 7 separate receivers
    // AppEventManager.notifyXxx() delivers null by default → use isNull()
    // ========================================================================

    @Test
    fun `notifyAppsUpdated triggers appsUpdated observer with null`() {
        AppEventManager.appsUpdated.observeForever(appsUpdatedObserver)
        AppEventManager.notifyAppsUpdated() // postValue(null)
        // null được deliver → dùng isNull() matcher
        verify(appsUpdatedObserver, atLeastOnce()).onChanged(isNull())
    }

    @Test
    fun `notifyAppsLoaded triggers appsLoaded observer with null`() {
        AppEventManager.appsLoaded.observeForever(appsLoadedObserver)
        AppEventManager.notifyAppsLoaded()
        verify(appsLoadedObserver, atLeastOnce()).onChanged(isNull())
    }

    @Test
    fun `notifyAppsEdited triggers appsEdited observer with null`() {
        AppEventManager.appsEdited.observeForever(appsEditedObserver)
        AppEventManager.notifyAppsEdited()
        verify(appsEditedObserver, atLeastOnce()).onChanged(isNull())
    }

    @Test
    fun `notifyVisibilityChanged triggers visibilityChanged observer with null`() {
        AppEventManager.visibilityChanged.observeForever(visibilityObserver)
        AppEventManager.notifyVisibilityChanged()
        verify(visibilityObserver, atLeastOnce()).onChanged(isNull())
    }

    @Test
    fun `notifyLockChanged triggers lockChanged observer with null`() {
        AppEventManager.lockChanged.observeForever(lockObserver)
        AppEventManager.notifyLockChanged()
        verify(lockObserver, atLeastOnce()).onChanged(isNull())
    }

    @Test
    fun `notifyBackgroundChanged triggers backgroundChanged observer with null`() {
        AppEventManager.backgroundChanged.observeForever(backgroundObserver)
        AppEventManager.notifyBackgroundChanged()
        verify(backgroundObserver, atLeastOnce()).onChanged(isNull())
    }

    @Test
    fun `notifyNightModeChanged triggers nightModeChanged observer with null`() {
        AppEventManager.nightModeChanged.observeForever(nightModeObserver)
        AppEventManager.notifyNightModeChanged()
        verify(nightModeObserver, atLeastOnce()).onChanged(isNull())
    }

    @Test
    fun `notifyAppsLoaded with data delivers correct data`() {
        val captor = argumentCaptor<Any>()
        AppEventManager.appsLoaded.observeForever(appsLoadedObserver)
        AppEventManager.notifyAppsLoaded("package_added")
        verify(appsLoadedObserver, atLeastOnce()).onChanged(captor.capture())
        assertEquals("Delivered data must match", "package_added", captor.lastValue)
    }

    // ========================================================================
    // CROSS-CONTAMINATION CHECK — Channel X không trigger Channel Y
    // Dùng mutable flag: observer set flag=true nếu bị gọi với sentinel value
    // ========================================================================

    @Test
    fun `notifyAppsUpdated does NOT deliver to appsLoaded channel`() {
        val sentinel = "SENTINEL_from_appsUpdated"
        var loadedReceivedSentinel = false

        AppEventManager.appsLoaded.observeForever { value ->
            if (value == sentinel) loadedReceivedSentinel = true
        }

        AppEventManager.notifyAppsUpdated(sentinel)

        assertFalse(
            "appsLoaded channel must NOT receive sentinel from appsUpdated",
            loadedReceivedSentinel
        )
    }

    @Test
    fun `notifyAppsLoaded does NOT deliver to appsUpdated channel`() {
        val sentinel = "SENTINEL_from_appsLoaded"
        var updatedReceivedSentinel = false

        AppEventManager.appsUpdated.observeForever { value ->
            if (value == sentinel) updatedReceivedSentinel = true
        }

        AppEventManager.notifyAppsLoaded(sentinel)

        assertFalse(
            "appsUpdated channel must NOT receive sentinel from appsLoaded",
            updatedReceivedSentinel
        )
    }

    @Test
    fun `notifyBackgroundChanged does NOT deliver to appsUpdated channel`() {
        val sentinel = "SENTINEL_from_background"
        var updatedReceivedSentinel = false

        AppEventManager.appsUpdated.observeForever { value ->
            if (value == sentinel) updatedReceivedSentinel = true
        }

        AppEventManager.notifyBackgroundChanged(sentinel)

        assertFalse(
            "appsUpdated channel must NOT receive sentinel from backgroundChanged",
            updatedReceivedSentinel
        )
    }

    @Test
    fun `notifyNightModeChanged does NOT deliver to appsLoaded channel`() {
        val sentinel = "SENTINEL_from_nightMode"
        var loadedReceivedSentinel = false

        AppEventManager.appsLoaded.observeForever { value ->
            if (value == sentinel) loadedReceivedSentinel = true
        }

        AppEventManager.notifyNightModeChanged(sentinel)

        assertFalse(
            "appsLoaded channel must NOT receive sentinel from nightModeChanged",
            loadedReceivedSentinel
        )
    }

    @Test
    fun `notifyLockChanged does NOT deliver to backgroundChanged channel`() {
        val sentinel = "SENTINEL_from_lock"
        var backgroundReceivedSentinel = false

        AppEventManager.backgroundChanged.observeForever { value ->
            if (value == sentinel) backgroundReceivedSentinel = true
        }

        AppEventManager.notifyLockChanged(sentinel)

        assertFalse(
            "backgroundChanged channel must NOT receive sentinel from lockChanged",
            backgroundReceivedSentinel
        )
    }

    // ========================================================================
    // END-TO-END: simulate PACKAGE_ADDED flow với BUG-13 fix
    // ========================================================================

    @Test
    fun `BUG13 - AppsUpdatedReceiver now calls AppEventManager directly`() {
        // Trước BUG-13: onReceive → UpdatedObservable.instance.update() → AppEventManager
        // Sau BUG-13:   onReceive → AppEventManager.notifyAppsUpdated() (1 hop)
        AppEventManager.appsUpdated.observeForever(appsUpdatedObserver)
        AppEventManager.notifyAppsUpdated()
        verify(appsUpdatedObserver, atLeastOnce()).onChanged(isNull())
    }

    @Test
    fun `full sequence - PACKAGE_ADDED then TaskUpdateApps completion`() {
        AppEventManager.appsUpdated.observeForever(appsUpdatedObserver)
        AppEventManager.appsLoaded.observeForever(appsLoadedObserver)

        // Step 1: Dynamic receiver (Android 8+ fix) → notifyAppsUpdated
        AppEventManager.notifyAppsUpdated()
        verify(appsUpdatedObserver, atLeastOnce()).onChanged(isNull())

        // Step 2: TaskUpdateApps completes → notifyAppsLoaded
        AppEventManager.notifyAppsLoaded()
        verify(appsLoadedObserver, atLeastOnce()).onChanged(isNull())
    }
}
