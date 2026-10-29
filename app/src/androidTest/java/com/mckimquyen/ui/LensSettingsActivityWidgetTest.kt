package com.mckimquyen.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensSettingsActivityWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun launch(id: String): ActivityScenario<LensSettingsActivity> = ActivityScenario.launch(
        Intent(context, LensSettingsActivity::class.java).putExtra(LensSettingsActivity.EXTRA_LENS_ID, id)
    )

    @Test fun bindingAndRecreationDoNotWritePreferencesOrChangeActiveLens() {
        val settings = UtilSettings(context)
        runBlocking { AppDatabase.getInstance().lensWorkspaceDao().insertOrUpdate(LensWorkspace(TARGET, "Work")) }
        val before = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).all.toMap()
        launch(TARGET).use { scenario ->
            awaitReady(scenario)
            scenario.onActivity {
                assertNotNull(it.findViewById<Slider>(R.id.sbLensSettingsIconSize))
                assertNotNull(it.findViewById<MaterialSwitch>(R.id.swLensSettingsSmartFocus))
                assertEquals("Work", it.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.tbLensSettings).title)
            }
            scenario.recreate()
            awaitReady(scenario)
        }
        assertEquals(before, androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).all)
    }

    @Test fun smartFocusOnlyWritesTheTargetLens() {
        val settings = UtilSettings(context)
        runBlocking { AppDatabase.getInstance().lensWorkspaceDao().insertOrUpdate(LensWorkspace(TARGET, "Work")) }
        val active = settings.getString(UtilSettings.KEY_ACTIVE_LENS_ID)
        val other = settings.isSmartFocusBias(LensWorkspace.DEFAULT_LENS_ID)
        settings.saveSmartFocusBias(TARGET, false)
        launch(TARGET).use { scenario ->
            awaitReady(scenario)
            scenario.onActivity { it.findViewById<MaterialSwitch>(R.id.swLensSettingsSmartFocus).performClick() }
        }
        assertTrue(settings.isSmartFocusBias(TARGET))
        assertEquals(other, settings.isSmartFocusBias(LensWorkspace.DEFAULT_LENS_ID))
        assertEquals(active, settings.getString(UtilSettings.KEY_ACTIVE_LENS_ID))
    }

    @Test fun missingLensClosesWithoutWritingPreferences() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val before = prefs.all.toMap()
        ActivityScenario.launch<LensSettingsActivity>(
            Intent(context, LensSettingsActivity::class.java).putExtra(LensSettingsActivity.EXTRA_LENS_ID, "missing-ui033")
        ).use { scenario ->
            val deadline = System.currentTimeMillis() + WAIT_MS
            while (scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MS)
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.state)
        }
        assertEquals(before, prefs.all)
    }

    @Test fun freezeAndUnfreezeNonActiveLensWriteOnlyTargetOrder() {
        val settings = UtilSettings(context)
        val dao = AppDatabase.getInstance().appPersistentDao()
        val seeded = com.mckimquyen.model.App(label = "Seed", packageName = "com.ui033.seed", name = "Main")
        runBlocking {
            AppDatabase.getInstance().lensWorkspaceDao().insertOrUpdate(LensWorkspace(TARGET, "Work"))
            dao.insert(com.mckimquyen.model.AppPersistent.defaults(seeded.packageName.toString(), "Main").copy(orderNumber = 9))
        }
        val originalApps = com.mckimquyen.app.RAppsSingleton.instance.apps
        com.mckimquyen.app.RAppsSingleton.instance.apps = arrayListOf(seeded)
        try {
            launch(TARGET).use { scenario ->
                awaitReady(scenario)
                scenario.onActivity { it.findViewById<android.view.View>(R.id.btLensSettingsFreeze).performClick() }
                waitUntil { settings.isLensFrozen(TARGET) }
                assertEquals(0, runBlocking { dao.findByIdentifier(TARGET, seeded.identifier)?.orderNumber })
                assertEquals(9, runBlocking { dao.findByIdentifier(seeded.identifier)?.orderNumber })
                scenario.onActivity { it.findViewById<android.view.View>(R.id.btLensSettingsFreeze).performClick() }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                var confirm: android.app.Dialog? = null
                waitUntil { scenario.onActivity { confirm = it.dialogForTest }; confirm?.isShowing == true }
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    (confirm as androidx.appcompat.app.AlertDialog)
                        .getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
                }
                waitUntil { !settings.isLensFrozen(TARGET) }
                assertEquals(-1, runBlocking { dao.findByIdentifier(TARGET, seeded.identifier)?.orderNumber })
                assertEquals(9, runBlocking { dao.findByIdentifier(seeded.identifier)?.orderNumber })
            }
        } finally {
            com.mckimquyen.app.RAppsSingleton.instance.apps = originalApps
            runBlocking { dao.deleteForLens(TARGET) }
        }
    }

    private fun withSeededApps(block: (List<com.mckimquyen.model.App>) -> Unit) {
        val apps = listOf(
            com.mckimquyen.model.App(label = "First", packageName = "com.ui033.first", name = "Main"),
            com.mckimquyen.model.App(label = "Second", packageName = "com.ui033.second", name = "Main")
        )
        runBlocking { AppDatabase.getInstance().lensWorkspaceDao().insertOrUpdate(LensWorkspace(TARGET, "Work")) }
        val original = com.mckimquyen.app.RAppsSingleton.instance.apps
        (context.applicationContext as com.mckimquyen.app.RApplication).appRefreshPipeline.cancel()
        com.mckimquyen.app.RAppsSingleton.instance.apps = ArrayList(apps)
        try { block(apps) } finally { com.mckimquyen.app.RAppsSingleton.instance.apps = original }
    }

    private fun confirmDialogAfterToggling(scenario: ActivityScenario<LensSettingsActivity>, index: Int) {
        var dialog: androidx.appcompat.app.AlertDialog? = null
        waitUntil { scenario.onActivity { dialog = it.dialogForTest }; dialog?.isShowing == true }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val list = dialog!!.listView
            list.performItemClick(list.getChildAt(index), index, list.adapter.getItemId(index))
            dialog!!.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        }
    }

    @Test fun chooseAppsOnlyChangesTheTargetLensScope() = withSeededApps { apps ->
        val settings = UtilSettings(context)
        settings.saveLensAppScope(TARGET, com.mckimquyen.util.LensAppScope.ALL)
        settings.saveLensAppScope(LensWorkspace.DEFAULT_LENS_ID, com.mckimquyen.util.LensAppScope.ALL)
        launch(TARGET).use { scenario ->
            awaitReady(scenario)
            scenario.onActivity { it.findViewById<android.view.View>(R.id.btLensSettingsApps).performClick() }
            confirmDialogAfterToggling(scenario, 0)   // untick the first app
            waitUntil { settings.getLensAppScope(TARGET) == com.mckimquyen.util.LensAppScope.SELECTED }
            assertEquals(setOf(apps[1].identifier), settings.getLensAppSelection(TARGET))
            assertEquals(com.mckimquyen.util.LensAppScope.ALL, settings.getLensAppScope(LensWorkspace.DEFAULT_LENS_ID))
        }
    }

    @Test fun restoringAHiddenAppOnlyTouchesTheTargetLens() = withSeededApps { apps ->
        val settings = UtilSettings(context)
        settings.saveLensHiddenAppIds(TARGET, setOf(apps[0].identifier, apps[1].identifier))
        settings.saveLensHiddenAppIds(LensWorkspace.DEFAULT_LENS_ID, setOf(apps[0].identifier))
        try {
            launch(TARGET).use { scenario ->
                awaitReady(scenario)
                scenario.onActivity { it.findViewById<android.view.View>(R.id.btLensSettingsHidden).performClick() }
                confirmDialogAfterToggling(scenario, 0)   // restore the first hidden app
                waitUntil { settings.getLensHiddenAppIds(TARGET).size == 1 }
                assertEquals(setOf(apps[1].identifier), settings.getLensHiddenAppIds(TARGET))
                assertEquals(setOf(apps[0].identifier), settings.getLensHiddenAppIds(LensWorkspace.DEFAULT_LENS_ID))
            }
        } finally {
            settings.saveLensHiddenAppIds(TARGET, emptySet())
            settings.saveLensHiddenAppIds(LensWorkspace.DEFAULT_LENS_ID, emptySet())
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MS)
        assertTrue(condition())
    }

    private fun awaitReady(scenario: ActivityScenario<LensSettingsActivity>) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        var ready = false
        while (!ready && System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { ready = it.findViewById<Slider>(R.id.sbLensSettingsIconSize).isEnabled }
            if (!ready) Thread.sleep(POLL_MS)
        }
        assertTrue("Target lens must load", ready)
    }

    private companion object {
        const val TARGET = "ui033-widget"
        const val WAIT_MS = 8_000L
        const val POLL_MS = 50L
    }
}
