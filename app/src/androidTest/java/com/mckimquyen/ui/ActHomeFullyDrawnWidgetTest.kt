package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PERF-004 Widget tests for ActHome's reportFullyDrawn gate.
 *
 * Verifies that:
 * 1. An empty cold start does not report fully drawn prematurely.
 * 2. When apps are present at launch (warm start), reportFullyDrawn is triggered exactly once.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeFullyDrawnWidgetTest {

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
    fun coldStartWithEmptyApps_doesNotReportFullyDrawn() {
        // Given: Empty apps snapshot (true cold start before async scan completes)
        RAppsSingleton.instance.apps = ArrayList()

        // When
        val scenario = ActivityScenario.launch(ActHome::class.java)

        // Then
        scenario.onActivity { activity ->
            assertFalse(
                "Cold start with empty app list must not report fully drawn yet",
                getHasReportedFullyDrawn(activity)
            )
            assertEquals(0, activity.reportFullyDrawnCallCount)
        }
        scenario.close()
    }

    @Test
    fun warmStartWithPreloadedApps_reportsFullyDrawnExactlyOnce() {
        // Given: Non-empty snapshot available synchronously in onCreate (warm start)
        val seedApps = ArrayList<App>().apply {
            repeat(4) { i ->
                add(App(packageName = "com.perf004.widget.app$i", name = "Widget App $i", isVisible = true))
            }
        }
        RAppsSingleton.instance.apps = seedApps

        // When
        val scenario = ActivityScenario.launch(ActHome::class.java)

        // Then
        scenario.onActivity { activity ->
            assertTrue(
                "Warm start with apps must report fully drawn",
                getHasReportedFullyDrawn(activity)
            )
            assertEquals(
                "reportFullyDrawn must be called exactly once",
                1,
                activity.reportFullyDrawnCallCount
            )
        }
        scenario.close()
    }
}
