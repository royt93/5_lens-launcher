package com.mckimquyen.launcher

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.adt.AppAdapter
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.enums.LauncherMode
import com.mckimquyen.model.App
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.ui.ActHome
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-003: Integration tests verifying cross-mode state sharing,
 * persistence boundaries, and accessibility action dispatch in Accessible List Mode.
 */
@RunWith(AndroidJUnit4::class)
class AccessibleListModeIntegrationTest {

    private lateinit var utilSettings: UtilSettings

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        utilSettings = UtilSettings(context)
        utilSettings.setLauncherMode(LauncherMode.LIST)
    }

    @After
    fun tearDown() {
        utilSettings.setLauncherMode(LauncherMode.FISHEYE)
    }

    @Test
    fun testAccessibleListMode_sharesStateWithAppPersistentAndSingleton() {
        val scenario = ActivityScenario.launch(ActHome::class.java)

        scenario.onActivity { activity ->
            val rvHomeAppList = activity.findViewById<RecyclerView>(R.id.rvHomeAppList)
            assertNotNull("rvHomeAppList must exist", rvHomeAppList)

            val adapter = rvHomeAppList.adapter as? AppAdapter
            assertNotNull("Adapter must be an AppAdapter instance", adapter)

            // Verify that all visible apps from singleton are accounted for in list mode
            val singletonApps = RAppsSingleton.instance.apps.orEmpty().filter { it.isVisible }
            if (singletonApps.isNotEmpty()) {
                assertEquals(
                    "List mode must contain all visible apps from repository",
                    singletonApps.size,
                    adapter!!.itemCount
                )
            }
        }

        scenario.close()
    }

    @Test
    fun testModeSwitching_preservesAppItemIntegrity() {
        val scenario = ActivityScenario.launch(ActHome::class.java)

        scenario.onActivity { activity ->
            // Switch to List Mode
            utilSettings.setLauncherMode(LauncherMode.LIST)
            activity.updateModeVisibility()

            val rvHomeAppList = activity.findViewById<RecyclerView>(R.id.rvHomeAppList)
            val adapter = rvHomeAppList.adapter as? AppAdapter
            assertNotNull("Adapter must exist", adapter)

            val initialCount = adapter!!.itemCount

            // Switch to Fisheye
            utilSettings.setLauncherMode(LauncherMode.FISHEYE)
            activity.updateModeVisibility()
            val lensView = activity.findViewById<View>(R.id.lensViews)
            assertNotEquals(View.GONE, lensView.visibility)

            // Switch back to List
            utilSettings.setLauncherMode(LauncherMode.LIST)
            activity.updateModeVisibility()
            assertEquals("Item count must remain identical after switching modes", initialCount, adapter.itemCount)
        }

        scenario.close()
    }

    @Test
    fun testListModeAppItems_haveAccessibilitySemanticsAndFocusability() {
        val scenario = ActivityScenario.launch(ActHome::class.java)

        scenario.onActivity { activity ->
            val rvHomeAppList = activity.findViewById<RecyclerView>(R.id.rvHomeAppList)
            assertNotNull(rvHomeAppList)

            // Trigger measure and layout to bind items
            rvHomeAppList.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY)
            )
            rvHomeAppList.layout(0, 0, 1080, 2000)

            val childCount = rvHomeAppList.childCount
            if (childCount > 0) {
                val firstItem = rvHomeAppList.getChildAt(0)
                assertNotNull("Item view must exist", firstItem)
                assertTrue("Item container must be focusable for TalkBack/D-pad", firstItem.isFocusable)

                val ivAppHide = firstItem.findViewById<View>(R.id.ivAppHide)
                if (ivAppHide != null && ivAppHide.visibility == View.VISIBLE) {
                    assertNotNull("Hide button must have accessible content description", ivAppHide.contentDescription)
                    assertTrue("Content description must not be empty", ivAppHide.contentDescription.isNotBlank())
                }
            }
        }

        scenario.close()
    }
}
