package com.mckimquyen.ui

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.search.AppSearchEngine
import com.mckimquyen.search.SearchHistoryStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-009: proves the recent-apps list is genuinely global across lenses at runtime, not merely
 * by SharedPreferences-key-naming convention - a launch recorded while lens A is active must show
 * up in a panel opened from lens B.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeMultiLensRecentAppsIntegrationTest {

    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val store get() = SearchHistoryStore(context)

    @Before
    fun setUp() = runBlocking {
        AppDatabase.init(context)
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertOrUpdate(LensWorkspace(id = "second", name = "Second Lens", orderIndex = 1))
        store.clear()
    }

    @After
    fun tearDown() = runBlocking {
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        store.clear()
        RAppsSingleton.instance.apps = ArrayList()
    }

    @Test
    fun launchRecordedOnOneLensAppearsInThePanelOpenedFromAnotherLens() {
        val camera = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
        RAppsSingleton.instance.apps = arrayListOf(camera)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            // Lens A (default, page 0) is active on cold launch. Record a launch as if it
            // happened from lens A - SearchHistoryStore has no lensId parameter, matching its
            // global-by-design scope.
            store.recordLaunch(AppSearchEngine.componentKey(camera))

            scenario.onActivity { activity ->
                activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.lensPager).currentItem = 1
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            lateinit var fragment: RecentAppsPanelFragment
            scenario.onActivity { activity ->
                fragment = RecentAppsPanelFragment()
                fragment.show(activity.supportFragmentManager, RecentAppsPanelFragment.TAG)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            val recyclerView = requireNotNull(fragment.view).findViewById<RecyclerView>(R.id.rvRecentApps)
            assertEquals(1, recyclerView.adapter?.itemCount)
        }
    }
}
