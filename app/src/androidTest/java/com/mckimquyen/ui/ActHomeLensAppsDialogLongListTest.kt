package com.mckimquyen.ui

import android.content.DialogInterface
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.LensAppScopeEditor
import com.mckimquyen.util.UtilSettings
import com.mckimquyen.views.LensView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AlertDialog only applies a multi-choice dialog's initial ticks to rows that have actually been
 * bound, so a list longer than the screen has rows the ListView reports as unticked although the
 * user never touched them. These tests use a list far longer than any screen to pin that down.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeLensAppsDialogLongListTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private var originalApps: ArrayList<App>? = null

    private fun app(pkg: String) =
        App(id = pkg.hashCode(), label = pkg, packageName = pkg, name = "Main", isVisible = true)

    private val apps = (0 until APP_COUNT).map { app("com.test.long.a%02d".format(it)) }
    private val allIds = apps.map(LensAppScope::identifierOf).toSet()

    @Before
    fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = ArrayList(apps)
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, lensId)
        reset()
    }

    @After
    fun tearDown() {
        reset()
        RAppsSingleton.instance.apps = originalApps
    }

    private fun reset() {
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.ALL)
        UtilSettings(context).saveLensAppSelection(lensId, emptySet())
    }

    private fun waitForApps(scenario: ActivityScenario<ActHome>) {
        var shown = 0
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline && shown != APP_COUNT) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { shown = it.findViewById<LensView>(R.id.lensViews)?.appsForTest?.size ?: 0 }
            if (shown != APP_COUNT) Thread.sleep(POLL_MS)
        }
        assertEquals("the seeded apps never reached the lens grid", APP_COUNT, shown)
    }

    private fun effective() = LensAppScopeEditor(UtilSettings(context)).effectiveIds(lensId, allIds)

    @Test
    fun confirmingAnUntouchedDialogKeepsEveryAppEvenThoseNeverScrolledIntoView() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForApps(scenario)
            scenario.onActivity { activity ->
                activity.onLensMenuItemSelected(ActHome.MENU_ID_LENS_APPS, 0)
                activity.lensDialog!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(allIds, effective())
        }
    }

    @Test
    fun untickingOneVisibleRowRemovesOnlyThatApp() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForApps(scenario)
            var removedId = ""
            scenario.onActivity { activity ->
                activity.onLensMenuItemSelected(ActHome.MENU_ID_LENS_APPS, 0)
            }
            // The rows are bound during layout; tap the first one only once the list has children.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val dialog = activity.lensDialog!!
                val list = dialog.listView
                val label = list.adapter.getItem(0).toString()
                removedId = LensAppScope.identifierOf(apps.first { it.label.toString() == label })
                list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0))
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(allIds - removedId, effective())
        }
    }

    @Test
    fun aSmallSelectionIsKeptExactlyWhenConfirmedUntouched() {
        val chosen = apps.take(3).map(LensAppScope::identifierOf).toSet()
        LensAppScopeEditor(UtilSettings(context)).apply(lensId, chosen)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var shown = 0
            val deadline = System.currentTimeMillis() + WAIT_MS
            while (System.currentTimeMillis() < deadline && shown != chosen.size) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { shown = it.findViewById<LensView>(R.id.lensViews)?.appsForTest?.size ?: 0 }
                if (shown != chosen.size) Thread.sleep(POLL_MS)
            }
            scenario.onActivity { activity ->
                activity.onLensMenuItemSelected(ActHome.MENU_ID_LENS_APPS, 0)
                activity.lensDialog!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(chosen, effective())
        }
    }

    private companion object {
        const val APP_COUNT = 60
        const val WAIT_MS = 5_000L
        const val POLL_MS = 100L
    }
}
