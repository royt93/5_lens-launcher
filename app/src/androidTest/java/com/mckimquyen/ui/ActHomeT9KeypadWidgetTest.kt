package com.mckimquyen.ui

import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeT9KeypadWidgetTest {
    private companion object {
        const val SEARCH_WAIT_MS = 6_000L
        const val SEARCH_POLL_MS = 50L
    }

    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()


    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val settings get() = UtilSettings(context)
    private var hadStoredSetting = false
    private var originalSetting = false
    private var originalApps: ArrayList<App>? = null

    private val appChrome = App(label = "Chrome", packageName = "com.test.chrome", name = "Main", isVisible = true)
    private val appMaps = App(label = "Maps", packageName = "com.test.maps", name = "Main", isVisible = true)

    @Before
    fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        hadStoredSetting = prefs.contains(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD)
        originalSetting = settings.getEnableT9SearchKeypad()
        settings.saveEnableT9SearchKeypad(true)
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(appChrome, appMaps)
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
            if (hadStoredSetting) {
                putBoolean(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD, originalSetting)
            } else {
                remove(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD)
            }
        }.commit()
        RAppsSingleton.instance.apps = originalApps
    }

    private fun awaitSearchShown(scenario: ActivityScenario<ActHome>) {
        val deadline = android.os.SystemClock.uptimeMillis() + SEARCH_WAIT_MS
            var shown = false
            while (!shown && android.os.SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    shown = activity.findViewById<com.google.android.material.search.SearchView>(R.id.searchView)
                        .currentTransitionState == com.google.android.material.search.SearchView.TransitionState.SHOWN
                }
                if (!shown) Thread.sleep(SEARCH_POLL_MS)
            }
            assertTrue("SearchView phải mở xong trước khi bấm T9", shown)
    }

    @Test
    fun t9KeypadTyping247_filtersToChrome() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.openSearchFromHome() }
            awaitSearchShown(scenario)

            scenario.onActivity { activity ->
                val search = activity.findViewById<com.google.android.material.search.SearchView>(R.id.searchView)
                assertEquals("T9 phải tắt cả tự mở IME của Material SearchView", false, search.isAutoShowKeyboard)
                val keypad = activity.findViewById<View>(R.id.scrollT9Keypad)
                assertNotNull("T9 keypad layout must exist", keypad)
                assertEquals(View.VISIBLE, keypad.visibility)
                val button2 = activity.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnT9_2)
                val textLayout = requireNotNull(button2.layout)
                assertEquals("Digit and letter hint must render on separate lines", 2, textLayout.lineCount)
                assertTrue(
                    "Second line must fit inside the visible key: bottom=${textLayout.getLineBottom(1)}, height=${button2.height}, paddingBottom=${button2.paddingBottom}",
                    textLayout.getLineBottom(1) <= button2.height - button2.paddingBottom
                )

                // The keypad viewport must sit above the system navigation bar. Large fonts can make the
                // grid taller than the 40% bound, so the last row is reached by scrolling, not by fitting.
                val visibleFrame = android.graphics.Rect().also { activity.window.decorView.getWindowVisibleDisplayFrame(it) }
                val scroll = activity.findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollT9Keypad)
                val viewportBottom = IntArray(2).also { scroll.getLocationOnScreen(it) }[1] + scroll.height
                assertTrue(
                    "Keypad viewport must not be clipped by system bars: bottom=$viewportBottom, visibleBottom=${visibleFrame.bottom}",
                    viewportBottom <= visibleFrame.bottom
                )
                scroll.scrollTo(0, scroll.getChildAt(0).height)
                val backspace = activity.findViewById<View>(R.id.btnT9_Backspace)
                val backspaceBottom = IntArray(2).also { backspace.getLocationOnScreen(it) }[1] + backspace.height
                assertTrue(
                    "Last key must be reachable by scrolling: bottom=$backspaceBottom, viewportBottom=$viewportBottom",
                    backspaceBottom <= viewportBottom + 1
                )
                scroll.scrollTo(0, 0)

                activity.findViewById<View>(R.id.btnT9_Clear).performClick()
                button2.performClick()
                activity.findViewById<View>(R.id.btnT9_4).performClick()
                activity.findViewById<View>(R.id.btnT9_7).performClick()
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val list = activity.searchResultAdapterForTest?.currentListForTest().orEmpty()
                assertEquals(1, list.size)
                assertEquals("Chrome", list[0].label)
            }
        }
    }

    /**
     * SEARCH-012: tapping keypad digits for a non-Latin app matches through Latin package aliases,
     * while tapping digit 1 no longer matches every non-Latin app.
     */
    @Test
    fun tappingKeypadMatchesNonLatinAppViaPackageAliasAndDoesNotMatchOnDigitOne() {
        val nonLatinApp = App(label = "微信", packageName = "com.tencent.mm", name = "Main", isVisible = true)
        RAppsSingleton.instance.apps = arrayListOf(nonLatinApp, appChrome)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.openSearchFromHome()
            }
            awaitSearchShown(scenario)
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.btnT9_Clear).performClick()
                activity.findViewById<View>(R.id.btnT9_1).performClick()
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val list = activity.searchResultAdapterForTest?.currentListForTest().orEmpty()
                assertTrue("Digit 1 must not match WeChat", list.none { it.packageName == "com.tencent.mm" })

                // Clear
                activity.findViewById<View>(R.id.btnT9_Backspace).performClick()
                // Tap "8" "3" "6" (ten -> tencent alias)
                activity.findViewById<View>(R.id.btnT9_8).performClick()
                activity.findViewById<View>(R.id.btnT9_3).performClick()
                activity.findViewById<View>(R.id.btnT9_6).performClick()
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val list = activity.searchResultAdapterForTest?.currentListForTest().orEmpty()
                assertEquals("WeChat must be matched via tencent package alias", 1, list.size)
                assertEquals("com.tencent.mm", list[0].packageName)
            }
        }
    }
}
