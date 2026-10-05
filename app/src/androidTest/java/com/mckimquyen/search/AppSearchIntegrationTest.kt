package com.mckimquyen.search

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.search.SearchView
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.services.AppEventManager
import com.mckimquyen.ui.ActHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-001: updated for the real Material3 SearchBar/SearchView (was a plain EditText that was
 * always inflated and driven purely by View focus). The panel must be shown() first - its
 * content, including the EditText, is not reliably focusable/interactable while hidden - and
 * updateSearchResults() in ActHome now gates on SearchView#isShowing() instead of View#hasFocus().
 */
@RunWith(AndroidJUnit4::class)
class AppSearchIntegrationTest {

    private fun waitUntilShowing(searchView: SearchView, showing: Boolean, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (searchView.isShowing == showing) return
            Thread.sleep(50)
        }
    }

    @Test
    fun packageAndAppStateEventsRefreshAnActiveSearch() {
        val singleton = RAppsSingleton.instance
        val originalApps = singleton.apps
        val camera = app("Camera", visible = true)
        singleton.apps = arrayListOf(camera)

        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                var searchView: SearchView? = null
                scenario.onActivity { activity ->
                    searchView = activity.findViewById(R.id.searchView)
                    searchView!!.show()
                }
                waitUntilShowing(searchView!!, true)

                scenario.onActivity { activity ->
                    searchView!!.editText.setText("camera")
                    assertSearch(activity, 1, "Camera")
                }

                singleton.apps = arrayListOf(app("Gallery", visible = true))
                AppEventManager.notifyAppsUpdated()
                waitForMainThread()
                scenario.onActivity { assertSearch(it, 0) }

                singleton.apps = arrayListOf(app("Camera Pro", visible = true, openCount = 7))
                AppEventManager.notifyAppsEdited()
                waitForMainThread()
                scenario.onActivity { assertSearch(it, 1, "Camera Pro") }

                singleton.apps = arrayListOf(app("Camera Pro", visible = false, openCount = 7))
                AppEventManager.notifyVisibilityChanged()
                waitForMainThread()
                scenario.onActivity { activity ->
                    assertSearch(activity, 0)
                    // FISH-008 left this checking the per-page LensView's own `lensViews` id,
                    // whose visibility is never explicitly toggled - visibility now lives on the
                    // ViewPager2 (`lensPager`) that wraps it (see item_lens_page.xml's own comment
                    // on this exact migration). Confirmed via direct log: lensPager was already
                    // correctly INVISIBLE here while `lensViews` stayed VISIBLE by coincidence.
                    assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.lensPager).visibility)
                }

                singleton.apps = arrayListOf()
                AppEventManager.notifyAppsUpdated()
                waitForMainThread()
                scenario.onActivity { assertSearch(it, 0) }
            }
        } finally {
            singleton.apps = originalApps
        }
    }

    @Test
    fun recentHeaderRestoresAndClearActionRemovesHistory() {
        val singleton = RAppsSingleton.instance
        val originalApps = singleton.apps
        val recent = app("Recent Camera", visible = true)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SearchHistoryStore(context)
        store.clear()
        store.recordLaunch(AppSearchEngine.componentKey(recent))
        singleton.apps = arrayListOf(recent)

        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                var searchView: SearchView? = null
                scenario.onActivity { activity ->
                    searchView = activity.findViewById(R.id.searchView)
                    searchView!!.show()
                }
                waitUntilShowing(searchView!!, true)
                // Same race TEST-003 found and fixed in the sibling test below (RApplication's
                // one-time real background PackageManager scan can land any time and overwrite
                // this fake single-app list with the real device's apps - 68 on TECNO KJ7,
                // matching the exact "expected:<1> but was:<68>" failure this re-set closes).
                // This test's longer duration (a full ActivityScenario.launch + up to 5s poll,
                // twice, plus a recreate() in between) gives that race a much wider window than
                // the sibling test ever had, and unlike it, was never hardened - re-set right
                // before each point the count is actually asserted, closing the window to zero
                // the same way, confirmed by the failure moving between this assertion and the
                // one after recreate() across different runs (not one fixed line).
                singleton.apps = arrayListOf(recent)
                AppEventManager.notifyAppsUpdated()
                waitForMainThread()
                scenario.onActivity { activity ->
                    assertSearch(activity, 1, "Recent Camera")
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.recentHeader).visibility)
                }

                scenario.recreate()
                scenario.onActivity { activity ->
                    searchView = activity.findViewById(R.id.searchView)
                    searchView!!.show()
                }
                waitUntilShowing(searchView!!, true)
                singleton.apps = arrayListOf(recent)
                AppEventManager.notifyAppsUpdated()
                waitForMainThread()
                scenario.onActivity { activity ->
                    assertSearch(activity, 1, "Recent Camera")
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.recentHeader).visibility)
                    activity.findViewById<View>(R.id.btClearSearchHistory).performClick()
                    // UI-011: blank query with no recent/favorite apps now falls back to the
                    // full app list (still just "Recent Camera" here, singleton.apps' only entry)
                    // instead of showing zero results.
                    assertSearch(activity, 1, "Recent Camera")
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.recentHeader).visibility)
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.allAppsHeader).visibility)
                    assertTrue(store.recentKeys().isEmpty())
                }
            }
        } finally {
            store.clear()
            singleton.apps = originalApps
        }
    }

    @Test
    fun supportedImeActionsAndPhysicalEnterLaunchFirstResultOnly() {
        val singleton = RAppsSingleton.instance
        val originalApps = singleton.apps
        val launchable = app("Launchable", visible = true)
        val store = SearchHistoryStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.clear()
        singleton.apps = arrayListOf(launchable)

        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                var searchView: SearchView? = null
                scenario.onActivity { activity ->
                    searchView = activity.findViewById(R.id.searchView)
                    searchView!!.show()
                }
                waitUntilShowing(searchView!!, true)

                // Closes a real race against RApplication's background PackageManager scan
                // (kicked off once at process start, outside this test's control - see
                // RApplication.onCreate()'s unconditional updateApps() call). waitUntilShowing
                // above polls for up to 5s, plenty of time on a slow device for that scan to
                // complete and overwrite this fake single-app list with real device apps before
                // the assertions below run - confirmed via direct logging (adapterCount=0 at the
                // point of failure). Re-asserting immediately before use, the same pattern this
                // file's other test method already uses before each of its own checks, closes
                // the window to zero (everything from here to the end of the onActivity block
                // below runs synchronously on the main thread with no yield back to the message
                // queue, so nothing else can interleave and clobber it mid-block).
                singleton.apps = arrayListOf(launchable)
                AppEventManager.notifyAppsUpdated()
                waitForMainThread()

                scenario.onActivity {
                    val search: EditText = searchView!!.editText
                    for (action in listOf(
                        EditorInfo.IME_ACTION_SEARCH,
                        EditorInfo.IME_ACTION_GO,
                        EditorInfo.IME_ACTION_DONE
                    )) {
                        search.requestFocus()
                        search.setText("launchable")
                        search.onEditorAction(action)
                        assertEquals("", search.text.toString())
                    }

                    search.requestFocus()
                    search.setText("launchable")
                    search.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    search.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    assertEquals("", search.text.toString())

                    search.requestFocus()
                    search.setText("launchable")
                    search.onEditorAction(EditorInfo.IME_ACTION_NEXT)
                    assertEquals("launchable", search.text.toString())

                    search.setText("unmatched-query")
                    search.onEditorAction(EditorInfo.IME_ACTION_SEARCH)
                    assertEquals("unmatched-query", search.text.toString())
                    assertEquals(listOf(AppSearchEngine.componentKey(launchable)), store.recentKeys())
                }
            }
        } finally {
            store.clear()
            singleton.apps = originalApps
        }
    }

    @Test
    fun unmatchedEnterDoesNotStickTheDownFlagForTheNextSearch() {
        // I1/M5 regression: a physical Enter DOWN on a query with no results used to leave
        // searchEnterDownHandled stuck true (ActHome returned false without resetting it),
        // so TextView never replays the matching UP (DOWN returned false), and the next
        // gesture's accounting started from a stale flag. Proves a no-match Enter DOWN+UP is
        // fully inert and a following real match still launches on its own DOWN+UP.
        val singleton = RAppsSingleton.instance
        val originalApps = singleton.apps
        val launchable = app("Launchable", visible = true)
        val store = SearchHistoryStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.clear()
        singleton.apps = arrayListOf(launchable)

        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                var searchView: SearchView? = null
                scenario.onActivity { activity ->
                    searchView = activity.findViewById(R.id.searchView)
                    requireNotNull(searchView) { "R.id.searchView not found" }.show()
                }
                waitUntilShowing(requireNotNull(searchView) { "R.id.searchView not found" }, true)

                singleton.apps = arrayListOf(launchable)
                AppEventManager.notifyAppsUpdated()
                waitForMainThread()

                scenario.onActivity { activity ->
                    val search: EditText = requireNotNull(searchView) { "searchView not bound yet" }.editText

                    search.requestFocus()
                    search.setText("unmatched-query")
                    search.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    search.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    assertEquals("unmatched-query", search.text.toString())
                    // I1 regression check: the pre-fix code left this flag stuck true (ActHome
                    // returned false for the no-first-result DOWN without resetting it), which a
                    // test only reading ACTION_UP's branch can't observe - TextView never replays
                    // the matching UP once DOWN returns false, so that branch is unreachable here.
                    val activityHome = activity as ActHome
                    assertFalse(
                        "searchEnterDownHandled must be reset after a no-match Enter DOWN",
                        activityHome.isSearchEnterDownHandledForTest()
                    )

                    search.setText("launchable")
                    search.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    search.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    assertEquals("", search.text.toString())
                    assertEquals(listOf(AppSearchEngine.componentKey(launchable)), store.recentKeys())
                }
            }
        } finally {
            store.clear()
            singleton.apps = originalApps
        }
    }

    private fun assertSearch(activity: ActHome, count: Int, firstLabel: String? = null) {
        val adapter = activity.findViewById<RecyclerView>(R.id.rvSearchResults).adapter as SearchResultAdapter
        assertEquals(count, adapter.itemCount)
        if (firstLabel != null) assertEquals(firstLabel, adapter.firstOrNull()?.label?.toString())
    }

    private fun waitForMainThread() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun app(label: String, visible: Boolean, openCount: Long = 0) = App(
        label = label,
        packageName = "com.example.${label.lowercase().replace(' ', '.')}",
        name = "com.example.MissingActivity",
        isVisible = visible,
        isOpened = true,
        openCount = openCount
    )
}
