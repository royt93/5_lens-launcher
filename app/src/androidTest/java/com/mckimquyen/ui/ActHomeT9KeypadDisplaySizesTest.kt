package com.mckimquyen.ui

import android.graphics.Rect
import android.util.Log
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.search.SearchView
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SEARCH-010 on whatever display the device is in: measure the T9 keypad cap, system-bar clearance, and last-key scroll reachability.
 * Logs the geometry (tag F172T9) for phone, landscape and `wm size` tablet-like profiles. Phone landscape is a KNOWN finding:
 * the inset padding is correct, but chips+quick actions+all-apps header take 596 px of 974 content px; the keypad independently uses
 * its full 40% cap (389 px), so the vertical column overflows by 11 px and its bottom sits 11 px under the visible frame. This test
 * intentionally fails that existing product bug there; it does not change production code. Search opens through
 * [ActHome.openSearchFromHome], so no gesture lands on an ad.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeT9KeypadDisplaySizesTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val TAG = "F172T9"
        const val SEARCH_WAIT_MS = 8_000L
        const val SEARCH_POLL_MS = 50L
        const val MAX_PERCENT = 40
        const val PERCENT = 100
        const val ROUNDING_PX = 2
        const val SETTLE_MS = 1_500L
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var hadStoredSetting = false
    private var originalSetting = false
    private var originalApps: ArrayList<App>? = null

    @Before fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        hadStoredSetting = prefs.contains(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD)
        originalSetting = UtilSettings(context).getEnableT9SearchKeypad()
        UtilSettings(context).saveEnableT9SearchKeypad(true)
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(
            App(label = "Chrome", packageName = "com.test.chrome", name = "Main", isVisible = true),
            App(label = "Maps", packageName = "com.test.maps", name = "Main", isVisible = true),
        )
    }

    @After fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
            if (hadStoredSetting) putBoolean(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD, originalSetting)
            else remove(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD)
        }.commit()
        RAppsSingleton.instance.apps = originalApps
    }

    private fun awaitSearchShown(scenario: ActivityScenario<ActHome>) {
        val deadline = android.os.SystemClock.uptimeMillis() + SEARCH_WAIT_MS
        var shown = false
        while (!shown && android.os.SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { shown = it.findViewById<SearchView>(R.id.searchView).currentTransitionState == SearchView.TransitionState.SHOWN }
            if (!shown) Thread.sleep(SEARCH_POLL_MS)
        }
        assertTrue("the SearchView must finish opening", shown)
    }

    @Test fun theKeypadIsCappedAboveTheSystemBarsAndItsLastKeyIsReachableOnThisDisplay() {
        val metrics = context.resources.displayMetrics
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { it.openSearchFromHome() }
            awaitSearchShown(scenario)
            instrumentation.waitForIdleSync()
            // Diagnostics for the one failing profile (phone landscape: keypad bottom 11 px past the visible frame): is the padding
            // the activity applied equal to the system-bar inset, and does the picture change once layout has had time to settle?
            fun diagnose(label: String) = scenario.onActivity { activity ->
                val scroll = activity.findViewById<NestedScrollView>(R.id.scrollT9Keypad)
                val content = activity.findViewById<View>(R.id.searchContent)
                val rootInsets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                val bars = rootInsets?.getInsets(WindowInsetsCompat.Type.systemBars())
                val gestures = rootInsets?.getInsets(WindowInsetsCompat.Type.systemGestures())
                val visible = Rect().also { activity.window.decorView.getWindowVisibleDisplayFrame(it) }
                val viewportBottom = IntArray(2).also { scroll.getLocationOnScreen(it) }[1] + scroll.height
                val contentLoc = IntArray(2).also { content.getLocationOnScreen(it) }
                Log.i(TAG, "$label systemBars=$bars gestures=$gestures searchContentPaddingBottom=${content.paddingBottom} " +
                    "searchContentBottom=${contentLoc[1] + content.height} keypadBottom=$viewportBottom visibleBottom=${visible.bottom} " +
                    "decor=${activity.window.decorView.width}x${activity.window.decorView.height}")
            }
            // Root-cause probe for the 11 px overshoot: where does the keypad's parent start and end, and which of its children take
            // height? A vertical LinearLayout whose weighted list is squeezed to 0 overflows by the sum of its fixed children.
            scenario.onActivity { activity ->
                val scroll = activity.findViewById<NestedScrollView>(R.id.scrollT9Keypad)
                val parent = scroll.parent as android.view.ViewGroup
                val parentLoc = IntArray(2).also { parent.getLocationOnScreen(it) }
                val rows = (0 until parent.childCount).map { parent.getChildAt(it) }.filter { it.visibility != View.GONE }.joinToString(" | ") {
                    val name = runCatching { activity.resources.getResourceEntryName(it.id) }.getOrDefault("noid")
                    "$name h=${it.height} top=${it.top} bottom=${it.bottom}"
                }
                Log.i(TAG, "parentTopOnScreen=${parentLoc[0].let { parentLoc[1] }} parentHeight=${parent.height} parentBottomOnScreen=${parentLoc[1] + parent.height} " +
                    "parentPaddingTop=${parent.paddingTop} parentPaddingBottom=${parent.paddingBottom} visibleChildren: $rows")
            }
            diagnose("immediate")
            Thread.sleep(SETTLE_MS)
            instrumentation.waitForIdleSync()
            diagnose("settled")
            scenario.onActivity { activity ->
                val scroll = activity.findViewById<NestedScrollView>(R.id.scrollT9Keypad)
                assertEquals("positive control: the keypad is visible", View.VISIBLE, scroll.visibility)
                val parent = scroll.parent as View
                val content = scroll.getChildAt(0)
                val cap = parent.height * MAX_PERCENT / PERCENT
                val visible = Rect().also { activity.window.decorView.getWindowVisibleDisplayFrame(it) }
                val viewportBottom = IntArray(2).also { scroll.getLocationOnScreen(it) }[1] + scroll.height
                val overflows = content.height > scroll.height
                Log.i(TAG, "display ${metrics.widthPixels}x${metrics.heightPixels} @${metrics.densityDpi}dpi parent=${parent.width}x${parent.height} " +
                    "keypad viewport=${scroll.width}x${scroll.height} content=${content.height} cap40=$cap overflowsCap=$overflows " +
                    "viewportBottom=$viewportBottom visibleBottom=${visible.bottom}")
                assertTrue("keypad viewport ${scroll.height}px must not exceed the 40% cap ${cap}px of its parent", scroll.height <= cap + ROUNDING_PX)
                assertTrue("keypad bottom $viewportBottom must be above the visible frame bottom ${visible.bottom}", viewportBottom <= visible.bottom)
                scroll.scrollTo(0, content.height)
                val backspace = activity.findViewById<View>(R.id.btnT9_Backspace)
                val backspaceBottom = IntArray(2).also { backspace.getLocationOnScreen(it) }[1] + backspace.height
                Log.i(TAG, "last key bottom after scrolling to the end=$backspaceBottom viewportBottom=$viewportBottom")
                assertTrue("the last key must be reachable by scrolling: bottom=$backspaceBottom viewportBottom=$viewportBottom", backspaceBottom <= viewportBottom + 1)
                scroll.scrollTo(0, 0)
            }
        }
    }
}
