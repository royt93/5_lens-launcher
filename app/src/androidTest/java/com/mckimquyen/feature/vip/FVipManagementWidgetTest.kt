package com.mckimquyen.feature.vip

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.mckimquyen.R
import com.mckimquyen.ui.ActSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FVipManagementWidgetTest {

    @Test
    fun testVipScreenUIElementsDisplayed() {
        // Launch ActVipManagement directly as a standalone Activity
        val scenario = ActivityScenario.launch(ActVipManagement::class.java)

        scenario.onActivity { activity ->
            // Verify key UI elements are present and displayed
            val tvStatusTitle = activity.findViewById<View>(R.id.tvStatusTitle)
            assertNotNull("tvStatusTitle should be present", tvStatusTitle)
            assertEquals(View.VISIBLE, tvStatusTitle.visibility)
            
            // Verify input elements
            val edtVipKey = activity.findViewById<View>(R.id.edtVipKey)
            assertNotNull("edtVipKey should be present", edtVipKey)
            assertEquals(View.VISIBLE, edtVipKey.visibility)

            val btnActivateVipKey = activity.findViewById<View>(R.id.btnActivateVipKey)
            assertNotNull("btnActivateVipKey should be present", btnActivateVipKey)
            assertEquals(View.VISIBLE, btnActivateVipKey.visibility)
            
            // Verify watch ad button
            val btnWatchAdVip = activity.findViewById<View>(R.id.btnWatchAdVip)
            assertNotNull("btnWatchAdVip should be present", btnWatchAdVip)
            assertEquals(View.VISIBLE, btnWatchAdVip.visibility)
        }

        scenario.close()
    }

    @Test
    fun testVipSettingsItemClickNavigatesToVipActivity() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        
        scenario.onActivity { activity ->
            // Switch to Settings tab (tab index 2)
            val viewPager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewpager)
            viewPager.setCurrentItem(2, false)
        }

        Thread.sleep(500)

        scenario.onActivity { activity ->
            val llVipPremium = activity.findViewById<View>(R.id.llVipPremium)
            assertNotNull("llVipPremium should exist", llVipPremium)
            
            // Perform click on the settings VIP row
            llVipPremium.performClick()
        }

        Thread.sleep(1000)

        // Verify the currently active Activity is ActVipManagement
        var currentActivity: android.app.Activity? = null
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val resumedActivities = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            if (resumedActivities.iterator().hasNext()) {
                currentActivity = resumedActivities.iterator().next()
            }
        }

        assertNotNull("An activity should be in RESUMED state", currentActivity)
        assertTrue("Launched activity should be ActVipManagement", currentActivity is ActVipManagement)
        
        scenario.close()
    }
}
