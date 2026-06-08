package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIntegrationTest {

    @Test
    fun testActHomeLaunchesSuccessfully() {
        // Launch ActHome
        val scenario = ActivityScenario.launch(ActHome::class.java)

        // Verify that the LensView is present programmatically on UI thread (bypassing Espresso on Android 16)
        scenario.onActivity { activity ->
            assertNotNull(activity)
            val lensView = activity.findViewById<android.view.View>(R.id.lensViews)
            assertNotNull("LensView should be present", lensView)
            assertEquals(android.view.View.VISIBLE, lensView.visibility)
        }

        scenario.close()
    }

    @Test
    fun testActSettingsLaunchesSuccessfully() {
        // Launch ActSettings
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        // Verify that viewpager is present programmatically
        scenario.onActivity { activity ->
            assertNotNull(activity)
            val viewPager = activity.findViewById<android.view.View>(R.id.viewpager)
            assertNotNull("ViewPager should be present", viewPager)
            assertEquals(android.view.View.VISIBLE, viewPager.visibility)
        }

        scenario.close()
    }
}
