package com.mckimquyen.ui

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.tabs.TabLayout
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-008 Phase 2 widget tests:
 * 1. Single lens: indicator is GONE so today's default experience has zero visual clutter.
 * 2. Multiple lenses: indicator is VISIBLE with one dot per lens.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeMultiLensWidgetTest {

    @Before
    fun setup(): Unit {
        cleanDb()
    }

    @After
    fun tearDown(): Unit {
        cleanDb()
    }

    private fun cleanDb() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppDatabase.init(context)
        val dao = AppDatabase.getInstance().lensWorkspaceDao()
        val all = dao.getAll()
        all.filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
    }

    @Test
    fun singleLens_hidesIndicatorEntirely() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val indicator = activity.findViewById<TabLayout>(R.id.lensPageIndicator)
                assertEquals(
                    "Dots indicator must be GONE when only one lens exists",
                    View.GONE,
                    indicator.visibility
                )
            }
        }
    }

    @Test
    fun multipleLenses_showsDotsIndicatorWithMatchingTabCount() {
        runBlocking {
            val dao = AppDatabase.getInstance().lensWorkspaceDao()
            dao.insertOrUpdate(LensWorkspace(id = "work", name = "Work", orderIndex = 1))
            dao.insertOrUpdate(LensWorkspace(id = "focus", name = "Focus", orderIndex = 2))
        }

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val indicator = activity.findViewById<TabLayout>(R.id.lensPageIndicator)
                assertEquals(
                    "Dots indicator must be VISIBLE when multiple lenses exist",
                    View.VISIBLE,
                    indicator.visibility
                )
                assertEquals(
                    "Indicator must have one dot per lens workspace",
                    3,
                    indicator.tabCount
                )
            }
        }
    }

    /**
     * FISH-008 Phase 3 regression: after a configuration change every page rebinds while the app
     * snapshot is already loaded, but `lensViews` still pointed at the destroyed Activity's view,
     * so no page matched and the restored page kept an empty grid. Caught by rotating a real
     * TECNO KJ7 - the lens drew nothing but the wallpaper until the next app-list broadcast.
     */
    @Test
    fun recreate_keepsTheRestoredLensPagePopulated() {
        runBlocking {
            AppDatabase.getInstance().lensWorkspaceDao()
                .insertOrUpdate(LensWorkspace(id = "work", name = "Work", orderIndex = 1))
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Page 1, not page 0: the bug only shows on a page the pager has to restore onto, which
        // binds while `lensViews` still points at the destroyed Activity's view.
        com.mckimquyen.util.UtilSettings(context)
            .save(com.mckimquyen.util.UtilSettings.KEY_ACTIVE_LENS_ID, "work")
        com.mckimquyen.app.RAppsSingleton.instance.apps = arrayListOf(
            com.mckimquyen.model.App(id = 1, label = "App 1", packageName = "com.t1", name = "A1"),
            com.mckimquyen.model.App(id = 2, label = "App 2", packageName = "com.t2", name = "A2")
        )

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            android.os.SystemClock.sleep(500)

            scenario.recreate()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            android.os.SystemClock.sleep(800)

            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.lensPager)
                val recycler = pager.getChildAt(0) as androidx.recyclerview.widget.RecyclerView
                val holder = recycler.findViewHolderForAdapterPosition(pager.currentItem)
                val lensView = (holder as com.mckimquyen.adt.LensPagerAdapter.PageHolder).lensView
                val apps = com.mckimquyen.views.LensView::class.java
                    .getDeclaredField("mApps")
                    .apply { isAccessible = true }
                    .get(lensView) as? ArrayList<*>
                // The real app-refresh pipeline rescans PackageManager here, so the exact count
                // belongs to the device - the invariant the bug broke is simply "not empty".
                org.junit.Assert.assertTrue(
                    "The restored lens page must still hold its apps after recreate, not an empty grid",
                    (apps?.size ?: 0) > 0
                )
            }
        }
    }

    @Test
    fun savedActiveLens_isRestoredOnLaunch() {
        runBlocking {
            val dao = AppDatabase.getInstance().lensWorkspaceDao()
            dao.insertOrUpdate(LensWorkspace(id = "work", name = "Work", orderIndex = 1))
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = com.mckimquyen.util.UtilSettings(context)
        settings.save(com.mckimquyen.util.UtilSettings.KEY_ACTIVE_LENS_ID, "work")

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val pager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.lensPager)
                assertEquals("Pager must restore to the saved active lens page", 1, pager.currentItem)
            }
        }
    }
}
