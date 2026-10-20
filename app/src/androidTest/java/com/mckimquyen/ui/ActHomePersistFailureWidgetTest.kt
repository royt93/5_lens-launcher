package com.mckimquyen.ui

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.services.AppEventManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** DB-006: a persist failure event reaches a live Activity and shows the rollback message. */
@RunWith(AndroidJUnit4::class)
class ActHomePersistFailureWidgetTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var originalApps: ArrayList<App>? = null

    @Before
    fun setUp() {
        originalApps = RAppsSingleton.instance.apps
    }

    @After
    fun tearDown() {
        RAppsSingleton.instance.apps = originalApps
    }

    @Test
    fun failureEventAfterTheActivityStartedIsObservedOnceAndEarlierOnesAreNot() {
        // A failure from before this Activity existed must not replay as a toast.
        instrumentation.runOnMainSync { AppEventManager.notifyPersistFailed() }
        instrumentation.waitForIdleSync()

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            var before = 0L
            scenario.onActivity { before = AppEventManager.persistFailureCount.value ?: 0L }

            instrumentation.runOnMainSync { AppEventManager.notifyPersistFailed() }
            val deadline = SystemClock.uptimeMillis() + 3_000L
            var after = before
            while (SystemClock.uptimeMillis() < deadline && after <= before) {
                instrumentation.waitForIdleSync()
                scenario.onActivity { after = AppEventManager.persistFailureCount.value ?: 0L }
                SystemClock.sleep(50)
            }

            assertTrue("the counter must advance for a live observer: $before -> $after", after > before)
            assertEquals("exactly one increment per failure", before + 1L, after)
        }
    }
}
