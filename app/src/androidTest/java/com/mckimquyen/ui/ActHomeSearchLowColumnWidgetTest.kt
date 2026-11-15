package com.mckimquyen.ui

import android.util.Log
import android.view.View
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.search.SearchView
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.search.SearchQuickActionsRowPolicy
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The empty search on whatever display the device is in, with the T9 keypad on: the all-apps list must keep at least
 * [SearchQuickActionsRowPolicy.MIN_LIST_DP] and the quick-actions row is hidden only when it would not. Search is opened through
 * [ActHome.openSearchFromHome] (no gesture, no ad). Rotation is done by the run script, one test per `am instrument`; a run that asks
 * for an orientation with `-e displayExpectOrientation landscape|portrait` fails when the Activity is in another one. Log tag F297LOW.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeSearchLowColumnWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val TAG = "F297LOW"
        const val EXPECT_ARG = "displayExpectOrientation"
        const val T9_ARG = "lowColumnT9" // on (default) | off: off means the soft keyboard opens with the search
        const val QUERY_ARG = "lowColumnQuery" // typed after the search is open; absent = empty query
        const val T9_OFF = "off"
        const val WAIT_MS = 8_000L
        const val POLL_MS = 50L
        const val SETTLE_MS = 800L
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var hadStored = false
    private var originalT9 = false
    private var originalApps: ArrayList<App>? = null

    @Before fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        hadStored = prefs.contains(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD)
        originalT9 = UtilSettings(context).getEnableT9SearchKeypad()
        UtilSettings(context).saveEnableT9SearchKeypad(InstrumentationRegistry.getArguments().getString(T9_ARG) != T9_OFF)
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = ArrayList((0 until 40).map { App(label = "App$it", packageName = "com.low.a$it", name = "Main", isVisible = true) })
    }

    @After fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
            if (hadStored) putBoolean(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD, originalT9) else remove(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD)
        }.commit()
        RAppsSingleton.instance.apps = originalApps
    }

    private fun awaitShown(scenario: ActivityScenario<ActHome>) {
        val deadline = android.os.SystemClock.uptimeMillis() + WAIT_MS
        var shown = false
        while (!shown && android.os.SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { shown = it.findViewById<SearchView>(R.id.searchView).currentTransitionState == SearchView.TransitionState.SHOWN }
            if (!shown) Thread.sleep(POLL_MS)
        }
        assertTrue("the SearchView must finish opening", shown)
        android.os.SystemClock.sleep(SETTLE_MS)
        instrumentation.waitForIdleSync()
    }

    @Test fun theListKeepsItsMinimumHeightAndTheQuickActionsRowYieldsOnlyWhenNeeded() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var portrait = true
            var dims = ""
            scenario.onActivity {
                val dm = it.resources.displayMetrics
                portrait = dm.heightPixels >= dm.widthPixels
                dims = "${dm.widthPixels}x${dm.heightPixels}"
            }
            val expected = InstrumentationRegistry.getArguments().getString(EXPECT_ARG)
            if (expected != null) assertEquals("the Activity is not in the orientation this run asked for (rotate from the script first)", expected, if (portrait) "portrait" else "landscape")
            // What this display must do: phone landscape hides the row, everything else keeps it (-e lowColumnExpectRow shown|hidden).
            val expectedRowShown = InstrumentationRegistry.getArguments().getString("lowColumnExpectRow")?.let { it == "shown" }
            scenario.onActivity { it.openSearchFromHome() }
            awaitShown(scenario)
            val query = InstrumentationRegistry.getArguments().getString(QUERY_ARG)
            if (query != null) {
                scenario.onActivity { it.findViewById<SearchView>(R.id.searchView).editText.setText(query) }
                instrumentation.waitForIdleSync()
            }
            // Let the layout passes that follow the search opening (and the one the controller posts when it hides the row) finish.
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(SETTLE_MS)
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val dm = activity.resources.displayMetrics
                val list = activity.findViewById<RecyclerView>(R.id.rvSearchResults)
                val row = activity.findViewById<View>(R.id.llEmptyQuickActions)
                val column = activity.findViewById<View>(R.id.searchContent)
                val minListPx = (SearchQuickActionsRowPolicy.MIN_LIST_DP * dm.density).toInt()
                val rowShown = row.visibility == View.VISIBLE
                val imeInsets = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                val imeVisible = imeInsets?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                val imeHeight = imeInsets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0
                // Where the list really ends on screen versus where the soft keyboard starts (the list is clipped by the keyboard when its
                // bottom is below that line). Logged, not asserted: whether the layout should shrink for the keyboard is a product decision.
                val loc = IntArray(2)
                list.getLocationOnScreen(loc)
                val windowHeight = activity.window.decorView.height
                val imeTop = windowHeight - imeHeight
                val listBottom = loc[1] + list.height
                Log.i(TAG, "onScreen listTop=${loc[1]} listBottom=$listBottom windowHeight=$windowHeight imeTop=$imeTop hiddenUnderIme=${if (imeVisible) (listBottom - imeTop).coerceAtLeast(0) else 0}px visibleList=${(minOf(listBottom, imeTop) - loc[1]).coerceAtLeast(0)}px")
                val keypad = activity.findViewById<View>(R.id.scrollT9Keypad)
                Log.i(TAG, "keypad=${if (keypad.visibility == View.VISIBLE) "VISIBLE h=${keypad.height}" else "GONE"} ime=${if (imeVisible) "VISIBLE" else "HIDDEN"} imeHeight=$imeHeight query=${query ?: "<empty>"} listItems=${list.adapter?.itemCount}")
                Log.i(TAG, "display $dims @${dm.densityDpi}dpi column=${column.height} list=${list.height}px (${"%.0f".format(list.height / dm.density)}dp) top=${list.top} " +
                    "minList=${minListPx}px quickActionsRow=${if (rowShown) "VISIBLE" else "GONE"} listVisible=${list.visibility == View.VISIBLE}")
                assertTrue("positive control: the empty search shows the all-apps list", list.visibility == View.VISIBLE && (list.adapter?.itemCount ?: 0) > 0)
                // The row yields only when it has to, and when it yields the list really gets the room (the first version of the controller
                // hid the row but left the list at 0 px: the layout pass that should follow was swallowed).
                assertTrue("the list must keep at least $minListPx px (row ${if (rowShown) "VISIBLE" else "GONE"}), had ${list.height}", list.height >= minListPx)
                if (expectedRowShown != null) assertEquals("quick-actions row visibility for this display", expectedRowShown, rowShown)
            }
        }
    }
}
