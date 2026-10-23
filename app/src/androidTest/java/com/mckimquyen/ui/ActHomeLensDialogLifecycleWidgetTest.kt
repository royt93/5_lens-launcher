package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeLensDialogLifecycleWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private val firstApp = App(label = "First", packageName = "com.test.dialog.first", name = "Main")
    private val secondApp = App(label = "Second", packageName = "com.test.dialog.second", name = "Main")
    private var originalApps: ArrayList<App>? = null

    @Before
    fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(firstApp, secondApp)
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.ALL)
        UtilSettings(context).saveLensAppSelection(lensId, emptySet())
    }

    @After
    fun cleanup() {
        RAppsSingleton.instance.apps = originalApps
    }

    private fun awaitApps(scenario: ActivityScenario<ActHome>) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        var count = 0
        while (System.currentTimeMillis() < deadline && count != SEEDED_APPS) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                count = activity.findViewById<com.mckimquyen.views.LensView>(R.id.lensViews)
                    ?.displayedApps?.size ?: 0
            }
            if (count != SEEDED_APPS) Thread.sleep(POLL_MS)
        }
        assertEquals("ActHome must have fixture apps before dialog", SEEDED_APPS, count)
    }

    @Test
    fun openingAnotherLensDialogDismissesThePreviousDialog() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            awaitApps(scenario)
            lateinit var oldDialog: androidx.appcompat.app.AlertDialog
            lateinit var currentDialog: androidx.appcompat.app.AlertDialog
            scenario.onActivity { activity ->
                activity.showLensAppsDialog(LensWorkspace.createDefault())
                oldDialog = requireNotNull(activity.lensDialog)
                assertTrue("First dialog must show", oldDialog.isShowing)

                activity.showLensAppsDialog(LensWorkspace.createDefault())
                currentDialog = requireNotNull(activity.lensDialog)
                assertTrue("Second dialog must show", currentDialog.isShowing)
                assertFalse("Replacing lens dialog must dismiss old window", oldDialog.isShowing)
            }
        }
    }

    @Test
    fun destroyingActivityDismissesTheCurrentlyTrackedLensDialog() {
        lateinit var dialog: androidx.appcompat.app.AlertDialog
        val scenario = ActivityScenario.launch(ActHome::class.java)
        awaitApps(scenario)
        scenario.onActivity { activity ->
            activity.showLensAppsDialog(LensWorkspace.createDefault())
            dialog = requireNotNull(activity.lensDialog)
            assertNotNull(dialog.window)
            assertTrue(dialog.isShowing)
        }
        scenario.close()
        assertFalse("Activity destroy must dismiss current lens dialog", dialog.isShowing)
    }

    private companion object {
        const val SEEDED_APPS = 2
        const val WAIT_MS = 8_000L
        const val POLL_MS = 100L
    }
}
