package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.services.AppEventManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PERF-004 Integration tests proving the real cold-start pipeline:
 *
 * 1. Process starts and ActHome launches with an empty snapshot.
 * 2. Background TaskUpdateApps completes and posts notifyAppsLoaded().
 * 3. ActHome receives the event, assigns real apps to LensView, and triggers reportFullyDrawn().
 * 4. Subsequent duplicate events (e.g. appsUpdated/re-sort) do NOT re-trigger reportFullyDrawn.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeFullyDrawnIntegrationTest {

    @Before
    fun setup() {
        RAppsSingleton.instance.clearAllData()
    }

    @After
    fun tearDown() {
        RAppsSingleton.instance.clearAllData()
    }

    private fun getHasReportedFullyDrawn(activity: ActHome): Boolean {
        val field = ActHome::class.java.getDeclaredField("hasReportedFullyDrawn")
        field.isAccessible = true
        return field.getBoolean(activity)
    }

    @Test
    fun coldStartPipeline_reportsFullyDrawnOnceAppsLoaded_andIsIdempotentOnSubsequentEvents() {
        // Step 1: Start ActHome with empty snapshot
        RAppsSingleton.instance.apps = ArrayList()
        val scenario = ActivityScenario.launch(ActHome::class.java)

        scenario.onActivity { activity ->
            assertFalse(
                "Initial empty cold frame must not have reported fully drawn",
                getHasReportedFullyDrawn(activity)
            )
            assertEquals(0, activity.reportFullyDrawnCallCount)
        }

        // Step 2: Simulate TaskUpdateApps commit and notifyAppsLoaded() dispatch on main thread
        val loadedApps = ArrayList<App>().apply {
            repeat(8) { i ->
                add(App(packageName = "com.perf004.integration.app$i", name = "Integ App $i", isVisible = true))
            }
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            RAppsSingleton.instance.replaceSnapshot(loadedApps, emptyMap())
            AppEventManager.notifyAppsLoaded()
        }

        // Step 3: Verify reportFullyDrawn was called exactly once after apps loaded
        scenario.onActivity { activity ->
            assertTrue(
                "Must report fully drawn once apps have been loaded and passed to LensView",
                getHasReportedFullyDrawn(activity)
            )
            assertEquals(
                "reportFullyDrawnCallCount should be 1 after appsLoaded event",
                1,
                activity.reportFullyDrawnCallCount
            )
        }

        // Step 4: Simulate subsequent duplicate event (e.g. apps re-sorted or updated)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppEventManager.notifyAppsLoaded()
        }

        scenario.onActivity { activity ->
            assertEquals(
                "Subsequent appsLoaded events must not duplicate reportFullyDrawn call",
                1,
                activity.reportFullyDrawnCallCount
            )
        }

        scenario.close()
    }
}
