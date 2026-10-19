package com.mckimquyen.ui

import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
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

    @Test
    fun t9KeypadTyping247_filtersToChrome() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.openSearchFromHome() }
            instrumentation.waitForIdleSync()

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
}
