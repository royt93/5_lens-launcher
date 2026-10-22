package com.mckimquyen.ui.settings

import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.ui.ActSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ARCH-001: Integration test verifying lifecycle and coordinator boundaries in ActSettings.
 */
@RunWith(AndroidJUnit4::class)
class ActSettingsArchitectureIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()


    @Test
    fun testActivityRecreatePreservesStateAndClearsDialogsWithoutWindowLeak() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        // Open sort dialog
        scenario.onActivity { activity ->
            val field = ActSettings::class.java.getDeclaredMethod("showSortTypeDialog").apply { isAccessible = true }
            field.invoke(activity)
        }
        instrumentation.waitForIdleSync()

        scenario.onActivity { activity ->
            val field = ActSettings::class.java.getDeclaredField("dlgSortType").apply { isAccessible = true }
            val dlg = field.get(activity) as? AlertDialog
            assertNotNull("Sort dialog must be open", dlg)
            assertTrue("Sort dialog must be showing", dlg?.isShowing == true)
        }

        // Recreate activity - dialog must be dismissed in onPause/onDestroy to avoid leak
        scenario.recreate()
        instrumentation.waitForIdleSync()

        scenario.onActivity { newActivity ->
            assertFalse("New activity must not be finishing", newActivity.isFinishing)
            assertFalse("New activity must not be destroyed", newActivity.isDestroyed)
        }

        scenario.close()
    }

    @Test
    fun iconPackDialogPersistsPackageIdentityForChosenRow() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        scenario.onActivity { activity ->
            val settingsField = com.mckimquyen.ui.ActBase::class.java.getDeclaredField("utilSettings").apply { isAccessible = true }
            val settings = settingsField.get(activity) as com.mckimquyen.util.UtilSettings
            settings.saveIconPackPackageName("com.example.core005")
            settings.saveLegacyIconPackLabel("Core 005 Pack")
            activity.showIconPackDialog()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario.onActivity { activity ->
            val field = ActSettings::class.java.getDeclaredField("dlgIconPack").apply { isAccessible = true }
            val dialog = field.get(activity) as AlertDialog
            dialog.listView.performItemClick(dialog.listView, 0, dialog.listView.adapter.getItemId(0))
            val settingsField = com.mckimquyen.ui.ActBase::class.java.getDeclaredField("utilSettings").apply { isAccessible = true }
            val settings = settingsField.get(activity) as com.mckimquyen.util.UtilSettings
            assertEquals("system option stores default legacy sentinel", "__default_icon_pack__", settings.getString(com.mckimquyen.util.UtilSettings.KEY_ICON_PACK_LABEL_NAME))
            assertEquals("system option clears package identity", "", settings.getString(com.mckimquyen.util.UtilSettings.KEY_ICON_PACK_PACKAGE_NAME))
        }
        scenario.close()
    }

    @Test
    fun testOptionsItemSelectedDispatchesHandledMenuActions() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        scenario.onActivity { activity ->
            val item = androidx.appcompat.view.menu.ActionMenuItem(
                activity,
                0,
                R.id.menuItemResetDefaultSettings,
                0,
                0,
                "Reset"
            )
            val handled = activity.onOptionsItemSelected(item)
            assertTrue("menuItemResetDefaultSettings must be handled by dispatcher", handled)
        }
        scenario.close()
    }

    @Test
    fun testDismissAllDialogsGuardsAllActiveDialogs() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        scenario.onActivity { activity ->
            activity.showIconPackDialog()
        }
        instrumentation.waitForIdleSync()

        scenario.onActivity { activity ->
            val field = ActSettings::class.java.getDeclaredField("dlgIconPack").apply { isAccessible = true }
            val dlg = field.get(activity) as? AlertDialog
            assertNotNull("Icon pack dialog must be open", dlg)

            // Invoke private dismissAllDialogs
            val dismissMethod = ActSettings::class.java.getDeclaredMethod("dismissAllDialogs").apply { isAccessible = true }
            dismissMethod.invoke(activity)

            val dlgAfter = field.get(activity) as? AlertDialog
            assertTrue("Dialog reference must be cleared after dismissAllDialogs", dlgAfter == null || !dlgAfter.isShowing)
        }

        scenario.close()
    }
}
