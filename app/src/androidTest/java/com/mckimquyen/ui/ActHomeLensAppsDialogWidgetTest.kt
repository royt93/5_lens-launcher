package com.mckimquyen.ui

import android.content.DialogInterface
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeLensAppsDialogWidgetTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private var originalApps: ArrayList<App>? = null

    private fun app(pkg: String) =
        App(id = pkg.hashCode(), label = pkg, packageName = pkg, name = "Main", isVisible = true)

    private val mail = app("com.test.dialog.mail")
    private val chat = app("com.test.dialog.chat")

    @Before
    fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(mail, chat)
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.ALL)
        UtilSettings(context).saveLensAppSelection(lensId, emptySet())
    }

    @After
    fun tearDown() {
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.ALL)
        UtilSettings(context).saveLensAppSelection(lensId, emptySet())
        RAppsSingleton.instance.apps = originalApps
    }

    private fun waitForApps(scenario: ActivityScenario<ActHome>) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        var ready = false
        while (System.currentTimeMillis() < deadline && !ready) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { ready = it.lensDialog == null && !it.isFinishing }
            Thread.sleep(POLL_MS)
        }
    }

    @Test
    fun menuEntryOpensTheChecklistWithEveryAppTickedForAnAllLens() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForApps(scenario)
            scenario.onActivity { activity ->
                assertTrue(activity.onLensMenuItemSelected(ActHome.MENU_ID_LENS_APPS, 0))
                val dialog = activity.lensDialog
                assertNotNull("The checklist dialog must be showing", dialog)
                val list: ListView = dialog!!.listView
                val checked = (0 until list.count).count { list.isItemChecked(it) }
                assertEquals("An ALL lens starts with every app ticked", list.count, checked)
            }
        }
    }

    @Test
    fun confirmingASubsetSelectsOnlyThoseApps() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForApps(scenario)
            scenario.onActivity { activity ->
                activity.onLensMenuItemSelected(ActHome.MENU_ID_LENS_APPS, 0)
                val dialog = activity.lensDialog!!
                val list = dialog.listView
                val keepIndex = (0 until list.count).first {
                    list.adapter.getItem(it).toString() == chat.label.toString()
                }
                (0 until list.count).forEach { list.setItemChecked(it, it == keepIndex) }
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val settings = UtilSettings(context)
            assertEquals(LensAppScope.SELECTED, settings.getLensAppScope(lensId))
            assertEquals(setOf(LensAppScope.identifierOf(chat)), settings.getLensAppSelection(lensId))
        }
    }

    @Test
    fun untickingEverythingKeepsTheLensOnAll() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            waitForApps(scenario)
            scenario.onActivity { activity ->
                activity.onLensMenuItemSelected(ActHome.MENU_ID_LENS_APPS, 0)
                val dialog = activity.lensDialog!!
                (0 until dialog.listView.count).forEach { dialog.listView.setItemChecked(it, false) }
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(LensAppScope.ALL, UtilSettings(context).getLensAppScope(lensId))
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 100L
    }
}
