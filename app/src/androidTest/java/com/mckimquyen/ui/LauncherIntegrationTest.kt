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
            // Installed apps load asynchronously, so the lens may already be visible here.
            // It must never be removed from layout, and the primary search entry point must exist.
            assertNotEquals(android.view.View.GONE, lensView.visibility)
            // UI-001: primary search entry point is now the always-visible SearchBar pill
            // (the SearchView results panel itself starts hidden until tapped).
            val search = activity.findViewById<android.view.View>(R.id.searchBar)
            assertNotNull("App search should be present", search)
            assertEquals(android.view.View.VISIBLE, search.visibility)
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
