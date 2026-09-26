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
