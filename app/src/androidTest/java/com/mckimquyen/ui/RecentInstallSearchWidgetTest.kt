package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.search.SearchView
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FEAT-013: typing "new" in the real Home search lists recently installed apps, not old or hidden ones. */
@RunWith(AndroidJUnit4::class)
class RecentInstallSearchWidgetTest {
    private companion object {
        const val SEARCH_WAIT_MS = 6_000L
        const val SEARCH_POLL_MS = 50L
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val OLD_DAYS = 30
        const val MID_DAYS = 3
        const val NEW_DAYS = 1
        const val RECENT_KEYWORD = "new"
    }

    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var originalApps: ArrayList<App>? = null

    @Before
    fun setUp() {
        (instrumentation.targetContext.applicationContext as RApplication).appRefreshPipeline.cancel()
        originalApps = RAppsSingleton.instance.apps
    }

    @After
    fun tearDown() {
        RAppsSingleton.instance.apps = originalApps
    }

    @Test
    fun typingNewListsOnlyVisibleRecentInstallsNewestFirst() {
        val now = System.currentTimeMillis()
        RAppsSingleton.instance.apps = arrayListOf(
            App(label = "Old Tool", packageName = "com.test.old", name = "Main", isVisible = true, installDate = now - OLD_DAYS * DAY_MS),
            App(label = "Fresh B", packageName = "com.test.freshb", name = "Main", isVisible = true, installDate = now - MID_DAYS * DAY_MS),
            App(label = "Fresh A", packageName = "com.test.fresha", name = "Main", isVisible = true, installDate = now - NEW_DAYS * DAY_MS),
            App(label = "Hidden New", packageName = "com.test.hidden", name = "Main", isVisible = false, installDate = now - NEW_DAYS * DAY_MS)
        )

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.openSearchFromHome() }
            awaitSearchShown(scenario)
            scenario.onActivity { it.findViewById<SearchView>(R.id.searchView).setText(RECENT_KEYWORD) }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.searchResultAdapterForTest?.currentListForTest().orEmpty()
                assertEquals(listOf("com.test.fresha", "com.test.freshb"), list.map { it.packageName.toString() })
            }
        }
    }

    private fun awaitSearchShown(scenario: ActivityScenario<ActHome>) {
        val deadline = android.os.SystemClock.uptimeMillis() + SEARCH_WAIT_MS
        var shown = false
        while (!shown && android.os.SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                shown = activity.findViewById<SearchView>(R.id.searchView).currentTransitionState == SearchView.TransitionState.SHOWN
            }
            if (!shown) Thread.sleep(SEARCH_POLL_MS)
        }
        assertTrue("SearchView must finish opening", shown)
    }
}
