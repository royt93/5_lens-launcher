package com.mckimquyen.ui

import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.tabs.TabLayout
import com.mckimquyen.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration Test verifying that all settings dialogs in ActSettings open properly
 * as Material 3 Dialogs and can be dismissed without error.
 */
@RunWith(AndroidJUnit4::class)
class ActSettingsDialogsIntegrationTest {

    @Test
    fun testAllSettingsDialogsOpenAsMaterial3() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        // 1. Navigate to Settings Tab (Tab Index 2)
        scenario.onActivity { activity ->
            val tabs = activity.findViewById<TabLayout>(R.id.tabs)
            assertNotNull("Tabs must exist", tabs)
            tabs.getTabAt(2)?.select()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // 2. Open Sort Dialog
        scenario.onActivity { activity ->
            val rowSort = activity.findViewById<android.view.View>(R.id.fabSort)
            assertNotNull("Row sort must exist", rowSort)
            rowSort.performClick()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        scenario.onActivity { activity ->
            // Access dlgSortType field or verify active dialog
            val field = ActSettings::class.java.getDeclaredField("dlgSortType").apply { isAccessible = true }
            val dlg = field.get(activity) as? AlertDialog
            assertNotNull("dlgSortType must be instantiated as androidx.appcompat.app.AlertDialog", dlg)
            assertTrue("Sort dialog must be showing", dlg?.isShowing == true)
            dlg?.dismiss()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // 3. Open Night Mode Dialog
        scenario.onActivity { activity ->
            val rowNight = activity.findViewById<android.view.View>(R.id.llNightMode)
            assertNotNull("Row night mode must exist", rowNight)
            rowNight.performClick()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        scenario.onActivity { activity ->
            val field = ActSettings::class.java.getDeclaredField("dlgNightMode").apply { isAccessible = true }
            val dlg = field.get(activity) as? AlertDialog
            assertNotNull("dlgNightMode must be instantiated as androidx.appcompat.app.AlertDialog", dlg)
            assertTrue("Night Mode dialog must be showing", dlg?.isShowing == true)
            dlg?.dismiss()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // 4. Open Highlight Color Dialog
        scenario.onActivity { activity ->
            val rowColor = activity.findViewById<android.view.View>(R.id.llHighlightColor)
            assertNotNull("Row highlight color must exist", rowColor)
            rowColor.performClick()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        scenario.onActivity { activity ->
            val field = ActSettings::class.java.getDeclaredField("dlgHighlightColor").apply { isAccessible = true }
            val dlg = field.get(activity) as? AlertDialog
            assertNotNull("dlgHighlightColor must be instantiated as androidx.appcompat.app.AlertDialog", dlg)
            assertTrue("Highlight Color dialog must be showing", dlg?.isShowing == true)
            dlg?.dismiss()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        scenario.close()
    }
}
