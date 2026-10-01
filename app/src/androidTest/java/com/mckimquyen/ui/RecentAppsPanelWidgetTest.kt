package com.mckimquyen.ui

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.search.AppSearchEngine
import com.mckimquyen.search.SearchHistoryStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-009: proves the panel resolves the real recent list against the real app snapshot, shows
 * the empty state when there's nothing to show, launches on tap, and removes-in-place on the
 * "Remove from recent" action - all against real ActHome/RAppsSingleton/SearchHistoryStore state,
 * not fakes. No Espresso (see SearchResultAdapterWidgetTest's class docstring for why).
 */
@RunWith(AndroidJUnit4::class)
class RecentAppsPanelWidgetTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val store get() = SearchHistoryStore(context)

    @Before
    fun setUp() {
        store.clear()
        RAppsSingleton.instance.apps = ArrayList()
    }

    @After
    fun tearDown() {
        store.clear()
        RAppsSingleton.instance.apps = ArrayList()
    }

    /** waitForIdleSync() must run on the test thread, never inside onActivity (main thread) -
     *  it throws "This method can not be called from the main application thread" otherwise.
     *  Hardened against RApplication's background scan: accepts an optional expectedApps list
     *  to re-assert inside the same onActivity callback right before show(), leaving zero window
     *  for a real background scan to replace the fake snapshot. */
    private fun showPanel(
        scenario: ActivityScenario<ActHome>,
        expectedApps: List<App>? = null
    ): RecentAppsPanelFragment {
        val fragment = RecentAppsPanelFragment()
        scenario.onActivity { activity ->
            if (expectedApps != null) {
                RAppsSingleton.instance.apps = ArrayList(expectedApps)
            }
            fragment.show(activity.supportFragmentManager, RecentAppsPanelFragment.TAG)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        return fragment
    }

    @Test
    fun emptyRecentList_showsEmptyStateNotTheList() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val fragment = showPanel(scenario)
            val view = requireNotNull(fragment.view)
            assertEquals(View.VISIBLE, view.findViewById<TextView>(R.id.tvRecentAppsEmpty).visibility)
            assertEquals(View.GONE, view.findViewById<RecyclerView>(R.id.rvRecentApps).visibility)
        }
    }

    @Test
    fun populatedRecentList_showsRealAppsInMostRecentFirstOrder() {
        val camera = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
        val notes = App(label = "Notes", packageName = "pkg.notes", name = "NotesActivity")
        RAppsSingleton.instance.apps = arrayListOf(camera, notes)
        store.recordLaunch(AppSearchEngine.componentKey(camera))
        store.recordLaunch(AppSearchEngine.componentKey(notes))

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val fragment = showPanel(scenario, listOf(camera, notes))
            val view = requireNotNull(fragment.view)
            assertEquals(View.GONE, view.findViewById<TextView>(R.id.tvRecentAppsEmpty).visibility)
            val recyclerView = view.findViewById<RecyclerView>(R.id.rvRecentApps)
            assertEquals(View.VISIBLE, recyclerView.visibility)
            assertEquals(2, recyclerView.adapter?.itemCount)

            val firstRow = recyclerView.findViewHolderForAdapterPosition(0)!!.itemView
            assertEquals(
                "Notes",
                firstRow.findViewById<TextView>(R.id.tvSearchResultLabel).text.toString()
            )
        }
    }

    @Test
    fun removingAnEntry_updatesTheListLiveWithoutClosingTheSheet() {
        val camera = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
        val notes = App(label = "Notes", packageName = "pkg.notes", name = "NotesActivity")
        RAppsSingleton.instance.apps = arrayListOf(camera, notes)
        store.recordLaunch(AppSearchEngine.componentKey(camera))
        store.recordLaunch(AppSearchEngine.componentKey(notes))

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val fragment = showPanel(scenario, listOf(camera, notes))
            val recyclerView = requireNotNull(fragment.view).findViewById<RecyclerView>(R.id.rvRecentApps)

            var handled = false
            scenario.onActivity {
                val holder = recyclerView.findViewHolderForAdapterPosition(0)!!
                holder.itemView.findViewById<View>(R.id.llSearchResultMainRow).performLongClick()
                val resultHolder = holder as com.mckimquyen.search.SearchResultAdapter.ResultViewHolder
                handled = resultHolder.handleMenuAction(R.id.menuItemRemoveFromRecent, notes, resultHolder.itemView)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            assertEquals(true, handled)
            assertEquals(1, recyclerView.adapter?.itemCount)
            assertEquals(false, fragment.isRemoving)
            assertEquals(listOf(AppSearchEngine.componentKey(camera)), store.recentKeys())
        }
    }
}
