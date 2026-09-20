package com.mckimquyen.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration Test for Material You settings flows and dynamic configuration changes on Tecno.
 */
@RunWith(AndroidJUnit4::class)
class MaterialYouSettingsIntegrationTest {

    @Before
    fun setUp() {
        // Reset night mode
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    @Test
    fun testSettingsNavigationAndSliderIntegration() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            assertNotNull("ActSettings must be launched", activity)

            val tabLayout = activity.findViewById<TabLayout>(R.id.tabs)
            assertNotNull("TabLayout must exist", tabLayout)
            assertEquals("TabLayout must have 3 tabs", 3, tabLayout.tabCount)

            // Switch to Lens tab (Tab 0)
            tabLayout.getTabAt(0)?.select()
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        scenario.onActivity { activity ->
            // In Lens tab, find sbMinIconSize slider and label
            val slider = activity.findViewById<Slider>(R.id.sbMinIconSize)
            val tvValue = activity.findViewById<android.widget.TextView>(R.id.tvValueMinIconSize)

            assertNotNull("Slider sbMinIconSize must be present in Lens tab", slider)
            assertNotNull("TextView tvValueMinIconSize must be present", tvValue)

            val newValue = 24.0f
            slider.value = newValue

            assertEquals("Slider value must be updated", newValue, slider.value, 0.01f)
            assertEquals("Label must update with slider value", "${newValue.toInt()}dp", tvValue.text.toString())
        }

        scenario.close()
    }

    @Test
    fun testDayNightModeRecreationIntegration() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        // 1. Verify in Light Mode
        scenario.onActivity { activity ->
            assertNotNull("Activity is active in light mode", activity)
            assertFalse("Activity should not be finishing", activity.isFinishing)
        }

        // 2. Toggle to Night Mode
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // 3. Recreate activity and verify smooth lifecycle without crash
        scenario.recreate()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        scenario.onActivity { activity ->
            assertNotNull("Activity successfully recreated in dark mode", activity)
            assertFalse("Activity should not be finishing after dark mode switch", activity.isFinishing)
        }

        scenario.close()
    }
}
