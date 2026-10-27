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

/** SEARCH-018: typing pinyin initials in the real Home search finds a Han-labelled app. */
@RunWith(AndroidJUnit4::class)
class PinyinSearchWidgetTest {
    private companion object {
        const val SEARCH_WAIT_MS = 6_000L
        const val SEARCH_POLL_MS = 50L
        const val PINYIN_INITIALS = "wx"
    }

    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var originalApps: ArrayList<App>? = null
    private var originalDisableScan = false

    @Before
    fun setUp() {
        (instrumentation.targetContext.applicationContext as RApplication).appRefreshPipeline.cancel()
        originalApps = RAppsSingleton.instance.apps
        // Opening ActHome would otherwise start a real PackageManager scan that replaces the seeded apps.
        originalDisableScan = RApplication.sDisableAutoAppRefresh
        RApplication.sDisableAutoAppRefresh = true
    }

    @After
    fun tearDown() {
        RApplication.sDisableAutoAppRefresh = originalDisableScan
        RAppsSingleton.instance.apps = originalApps
    }

    @Test
    fun typingPinyinInitialsFindsTheHanLabelledApp() {
        RAppsSingleton.instance.apps = arrayListOf(
            App(label = "微信", packageName = "com.test.hanapp", name = "Main", isVisible = true),
            App(label = "Camera", packageName = "com.test.camera", name = "Main", isVisible = true)
        )

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.openSearchFromHome() }
            awaitSearchShown(scenario)
            scenario.onActivity { it.findViewById<SearchView>(R.id.searchView).setText(PINYIN_INITIALS) }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val list = activity.searchResultAdapterForTest?.currentListForTest().orEmpty()
                assertEquals(listOf("com.test.hanapp"), list.map { it.packageName.toString() })
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
