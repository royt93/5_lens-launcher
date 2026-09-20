package com.mckimquyen.launcher

import android.content.pm.ActivityInfo
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.search.SearchBar
import com.google.android.material.tabs.TabLayout
import com.mckimquyen.R
import com.mckimquyen.enums.LauncherMode
import com.mckimquyen.ui.ActHome
import com.mckimquyen.ui.ActSettings
import com.mckimquyen.util.UtilSettings
import com.mckimquyen.views.LensView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-004: Widget tests verifying adaptive orientation and landscape reflow
 * on [ActHome] and [ActSettings].
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveOrientationWidgetTest {

    private lateinit var utilSettings: UtilSettings

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        utilSettings = UtilSettings(context)
        utilSettings.setLauncherMode(LauncherMode.FISHEYE)
    }

    @After
    fun tearDown() {
        utilSettings.setLauncherMode(LauncherMode.FISHEYE)
    }

    @Test
    fun testActHomeOrientationChangePreservesViews() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            // 1. Initial portrait orientation check
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                val searchBar = activity.findViewById<SearchBar>(R.id.searchBar)
                val lensViews = activity.findViewById<LensView>(R.id.lensViews)
                assertNotNull("searchBar must be present in portrait", searchBar)
                assertNotNull("lensViews must be present in portrait", lensViews)
                assertEquals("lensViews must be visible in portrait", View.VISIBLE, lensViews.visibility)
                assertEquals("searchBar must be visible in portrait", View.VISIBLE, searchBar.visibility)
            }

            // 2. Rotate to landscape
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            // 3. Verify views remain intact and visible in landscape
            scenario.onActivity { activity ->
                val searchBar = activity.findViewById<SearchBar>(R.id.searchBar)
                val lensViews = activity.findViewById<LensView>(R.id.lensViews)
                assertNotNull("searchBar must remain present in landscape", searchBar)
                assertNotNull("lensViews must remain present in landscape", lensViews)
                assertEquals("lensViews must remain visible in Fisheye landscape", View.VISIBLE, lensViews.visibility)
                assertEquals("searchBar must remain visible in landscape", View.VISIBLE, searchBar.visibility)
            }

            // 4. Restore portrait
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    @Test
    fun testActHomeListModeOrientationChange() {
        utilSettings.setLauncherMode(LauncherMode.LIST)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.updateModeVisibility()
                val rvList = activity.findViewById<RecyclerView>(R.id.rvHomeAppList)
                assertNotNull("rvHomeAppList should exist in list mode", rvList)
                assertEquals("rvHomeAppList should be VISIBLE", View.VISIBLE, rvList.visibility)

                // Rotate to landscape
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            scenario.onActivity { activity ->
                val rvList = activity.findViewById<RecyclerView>(R.id.rvHomeAppList)
                assertEquals("rvHomeAppList must remain VISIBLE in landscape", View.VISIBLE, rvList.visibility)

                // Rotate back to portrait
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            scenario.onActivity { activity ->
                val rvList = activity.findViewById<RecyclerView>(R.id.rvHomeAppList)
                assertEquals("rvHomeAppList must remain VISIBLE after rotation", View.VISIBLE, rvList.visibility)
            }
        }
    }

    @Test
    fun testActSettingsLandscapeLayoutReflow() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            // Rotate settings to landscape
            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
                val tabs = activity.findViewById<TabLayout>(R.id.tabs)
                assertNotNull("toolbar must exist in landscape", toolbar)
                assertNotNull("tabs must exist in landscape", tabs)
                assertEquals("toolbar must be visible", View.VISIBLE, toolbar.visibility)
                assertEquals("tabs must be visible", View.VISIBLE, tabs.visibility)

                // Restore portrait
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }
}
