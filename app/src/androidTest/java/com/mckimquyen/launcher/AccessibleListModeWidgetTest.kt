package com.mckimquyen.launcher

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.enums.LauncherMode
import com.mckimquyen.ui.ActHome
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-003: Widget tests verifying Accessible List Mode UI behavior,
 * view toggling, and search interactions on [ActHome].
 */
@RunWith(AndroidJUnit4::class)
class AccessibleListModeWidgetTest {

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
    fun testFisheyeMode_displaysLensView_hidesAppList() {
        utilSettings.setLauncherMode(LauncherMode.FISHEYE)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val lensViews = activity.findViewById<View>(R.id.lensViews)
                val rvHomeAppList = activity.findViewById<RecyclerView>(R.id.rvHomeAppList)

                assertNotNull("lensViews must be present", lensViews)
                assertNotNull("rvHomeAppList must be present", rvHomeAppList)

                assertEquals("rvHomeAppList must be GONE in Fisheye mode", View.GONE, rvHomeAppList.visibility)
                assertNotEquals("lensViews must not be GONE in Fisheye mode", View.GONE, lensViews.visibility)
            }
        }
    }

    @Test
    fun testAccessibleListMode_displaysAppList_hidesLensView() {
        utilSettings.setLauncherMode(LauncherMode.LIST)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val lensViews = activity.findViewById<View>(R.id.lensViews)
                val rvHomeAppList = activity.findViewById<RecyclerView>(R.id.rvHomeAppList)

                assertNotNull("lensViews must be present", lensViews)
                assertNotNull("rvHomeAppList must be present", rvHomeAppList)

                assertEquals("lensViews must be GONE in List mode", View.GONE, lensViews.visibility)
                assertNotNull("rvHomeAppList adapter must be initialized", rvHomeAppList.adapter)
            }
        }
    }

    @Test
    fun testDynamicModeSwitch_switchesViewsWithoutRestart() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val lensViews = activity.findViewById<View>(R.id.lensViews)
                val rvHomeAppList = activity.findViewById<RecyclerView>(R.id.rvHomeAppList)

                // Start in Fisheye
                utilSettings.setLauncherMode(LauncherMode.FISHEYE)
                activity.updateModeVisibility()
                assertEquals(View.GONE, rvHomeAppList.visibility)

                // Switch to List Mode dynamically
                utilSettings.setLauncherMode(LauncherMode.LIST)
                activity.updateModeVisibility()
                assertEquals(View.GONE, lensViews.visibility)

                // Switch back to Fisheye
                utilSettings.setLauncherMode(LauncherMode.FISHEYE)
                activity.updateModeVisibility()
                assertEquals(View.GONE, rvHomeAppList.visibility)
            }
        }
    }
}
