package com.mckimquyen.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.search.SearchView
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.search.SearchHistoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-020: allAppsHeader/resultsSectionHeader used to show a static label ("All apps"/"Matching
 * apps") with no count - the apps_count/search_results_count plural strings existed but had no
 * call site (a real, if minor, A11Y-001 gap: TalkBack never announced how many results there
 * were). This proves both headers now show a real count that matches the adapter's actual
 * item count, using the correct locale plural form.
 */
@RunWith(AndroidJUnit4::class)
class SearchResultCountHeaderWidgetTest {

    @Before
    fun ensureRealAppListAndClearHistory() {
        // Other test classes in this suite record real launches/queries into the same on-device
        // SearchHistoryStore (it's not test-scoped) - a leftover recent entry makes a blank query
        // return non-empty results, which turns off the all-apps fallback this test depends on.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SearchHistoryStore(context).clear()

        // UI-020 finding: some test classes (e.g. a11y.AccessibilityActionsIntegrationTest)
        // replace RAppsSingleton.instance.apps with a single fake entry for their own assertions,
        // then "clean up" via clearAllData() - which wipes it to an EMPTY list rather than
        // restoring the real one. Nothing re-triggers a real PackageManager scan afterwards, so
        // any later test in the same instrumentation process (this one included) can inherit a
        // permanently empty app list. Don't depend on ambient state left by unrelated tests -
        // populate a real launchable-app list ourselves so this test's precondition always holds.
        if (RAppsSingleton.instance.apps.isNullOrEmpty()) {
            val pm = context.packageManager
            val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val realApps = pm.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
                .map { resolveInfo ->
                    App(
                        id = 0,
                        label = resolveInfo.loadLabel(pm),
                        packageName = resolveInfo.activityInfo.packageName,
                        name = resolveInfo.activityInfo.name,
                        isVisible = true
                    )
                }
            assertTrue("expected at least one real launchable app on this device", realApps.isNotEmpty())
            RAppsSingleton.instance.apps = ArrayList(realApps)
        }
    }

    private fun waitUntilVisible(view: View, timeoutMs: Long = 15_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (view.visibility == View.VISIBLE) return true
            Thread.sleep(50)
        }
        return view.visibility == View.VISIBLE
    }

    private fun waitUntilShowing(searchView: SearchView, showing: Boolean, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (searchView.isShowing == showing) return
            Thread.sleep(50)
        }
    }

    @Test
    fun allAppsHeaderShowsThePluralCountMatchingTheAdapter() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var searchView: SearchView? = null
            scenario.onActivity { activity ->
                searchView = activity.findViewById(R.id.searchView)
                searchView!!.show()
            }
            waitUntilShowing(searchView!!, true)
            // Query starts empty already - nudge the listener so updateSearchResults() runs for
            // the blank-query (all-apps fallback) state, same as a fresh search-view open would.
            scenario.onActivity {
                searchView!!.editText.setText(" ")
                searchView!!.editText.setText("")
            }

            var header: TextView? = null
            var recycler: RecyclerView? = null
            scenario.onActivity { activity ->
                header = activity.findViewById(R.id.allAppsHeader)
                recycler = activity.findViewById(R.id.rvSearchResults)
            }
            assertTrue("expected the all-apps fallback header to show up", waitUntilVisible(header!!))

            scenario.onActivity { activity ->
                val count = recycler!!.adapter?.itemCount ?: 0
                assertTrue("expected at least one app on a real device", count > 0)
                val expected = activity.resources.getQuantityString(R.plurals.apps_count, count, count)
                assertEquals(expected, header!!.text.toString())
            }
        }
    }

    @Test
    fun resultsSectionHeaderShowsThePluralCountMatchingTheAdapter() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var searchView: SearchView? = null
            scenario.onActivity { activity ->
                searchView = activity.findViewById(R.id.searchView)
                searchView!!.show()
            }
            waitUntilShowing(searchView!!, true)
            scenario.onActivity {
                // Broad single-letter query: virtually guaranteed to match at least one app on
                // any real device, without depending on which specific apps are installed.
                searchView!!.editText.setText("a")
            }

            var header: TextView? = null
            var recycler: RecyclerView? = null
            scenario.onActivity { activity ->
                header = activity.findViewById(R.id.resultsSectionHeader)
                recycler = activity.findViewById(R.id.rvSearchResults)
            }
            assertTrue("expected the results header to show up for a broad query", waitUntilVisible(header!!))

            scenario.onActivity { activity ->
                val count = recycler!!.adapter?.itemCount ?: 0
                assertTrue("expected at least one match for a single-letter query", count > 0)
                val expected = activity.resources.getQuantityString(R.plurals.search_results_count, count, count)
                assertEquals(expected, header!!.text.toString())
            }
        }
    }
}
