package com.mckimquyen.views

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.ui.ActHome
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
class LensViewRemoveFromLensIntegrationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private var originalApps: ArrayList<App>? = null

    private val mail = App(id = 1, label = "Mail", packageName = "com.test.rm.mail", name = "Main", isVisible = true)
    private val chat = App(id = 2, label = "Chat", packageName = "com.test.rm.chat", name = "Main", isVisible = true)

    @Before
    fun setup() {
        // A cold process can still run RApplication's first PackageManager scan; cancel it so it
        // cannot land on top of the seeded snapshot.
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(mail, chat)
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, lensId)
        reset()
    }

    @After
    fun tearDown() {
        reset()
        // Restore, never clearAllData(): that leaves the shared singleton empty for later tests.
        RAppsSingleton.instance.apps = originalApps
    }

    private fun reset() {
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.ALL)
        UtilSettings(context).saveLensAppSelection(lensId, emptySet())
    }

    /** Waits until the grid holds [expected] apps, then returns its LensView. */
    private fun lensViewWithCount(scenario: ActivityScenario<ActHome>, expected: Int): LensView {
        var view: LensView? = null
        var count = -1
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline && count != expected) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                view = it.findViewById(R.id.lensViews)
                count = view?.appsForTest?.size ?: -1
            }
            if (count != expected) Thread.sleep(POLL_MS)
        }
        assertEquals("the lens grid never held $expected apps", expected, count)
        return requireNotNull(view)
    }

    private fun scopeOf() = UtilSettings(context).getLensAppScope(lensId)
    private fun selectionOf() = UtilSettings(context).getLensAppSelection(lensId)

    @Test
    fun removeEntryExistsButIsHiddenOnAnAllLens() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val lensView = lensViewWithCount(scenario, expected = 2)
            scenario.onActivity {
                assertTrue(lensView.showAppOptionsAtIndex(0))
                val item = lensView.quickActionsMenuForTest!!.menu.findItem(R.id.menuItemRemoveFromLens)
                assertNotNull("the menu resource must carry the entry", item)
                assertFalse("an ALL lens has nothing to remove an app from", item.isVisible)
            }
        }
    }

    @Test
    fun removeEntryIsShownOnASelectedLens() {
        UtilSettings(context).saveLensAppSelection(lensId, setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(chat)))
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.SELECTED)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val lensView = lensViewWithCount(scenario, expected = 2)
            scenario.onActivity {
                assertTrue(lensView.showAppOptionsAtIndex(0))
                assertTrue(lensView.quickActionsMenuForTest!!.menu.findItem(R.id.menuItemRemoveFromLens).isVisible)
            }
        }
    }

    @Test
    fun removingDropsExactlyThatAppAndTheGridRefreshes() {
        UtilSettings(context).saveLensAppSelection(lensId, setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(chat)))
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.SELECTED)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val lensView = lensViewWithCount(scenario, expected = 2)
            lateinit var removed: App // assigned in the same onActivity block before any use
            scenario.onActivity {
                removed = lensView.appsForTest!![0]
                assertTrue(lensView.showAppOptionsAtIndex(0))
                lensView.quickActionsMenuForTest!!.menu.performIdentifierAction(R.id.menuItemRemoveFromLens, 0)
            }
            val survivors = setOf(mail, chat).filter { it.packageName != removed.packageName }
            assertEquals(survivors.map(LensAppScope::identifierOf).toSet(), selectionOf())
            assertEquals(LensAppScope.SELECTED, scopeOf())
            // The event reaches ActHome, which re-filters and pushes the shorter list to the grid.
            val refreshed = lensViewWithCount(scenario, expected = 1)
            scenario.onActivity {
                assertEquals(survivors.single().label.toString(), refreshed.appsForTest!!.single().label.toString())
            }
        }
    }

    @Test
    fun removingTheLastSelectedAppFallsBackToShowingEveryApp() {
        UtilSettings(context).saveLensAppSelection(lensId, setOf(LensAppScope.identifierOf(mail)))
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.SELECTED)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val lensView = lensViewWithCount(scenario, expected = 1)
            scenario.onActivity {
                assertTrue(lensView.showAppOptionsAtIndex(0))
                lensView.quickActionsMenuForTest!!.menu.performIdentifierAction(R.id.menuItemRemoveFromLens, 0)
            }
            // A lens must never end up blank: the editor reverts it to ALL and the grid shows both apps.
            assertEquals(LensAppScope.ALL, scopeOf())
            assertEquals(emptySet<String>(), selectionOf())
            lensViewWithCount(scenario, expected = 2)
        }
    }

    @Test
    fun removalSurvivesRecreatingTheActivity() {
        UtilSettings(context).saveLensAppSelection(lensId, setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(chat)))
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.SELECTED)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val lensView = lensViewWithCount(scenario, expected = 2)
            scenario.onActivity {
                lensView.showAppOptionsAtIndex(0)
                lensView.quickActionsMenuForTest!!.menu.performIdentifierAction(R.id.menuItemRemoveFromLens, 0)
            }
            lensViewWithCount(scenario, expected = 1)
            scenario.recreate()
            lensViewWithCount(scenario, expected = 1)
        }
    }

    @Test
    fun removingFromOneLensLeavesAnotherLensUntouched() {
        val other = "remove-other-lens"
        UtilSettings(context).saveLensAppSelection(lensId, setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(chat)))
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.SELECTED)
        UtilSettings(context).saveLensAppSelection(other, setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(chat)))
        UtilSettings(context).saveLensAppScope(other, LensAppScope.SELECTED)
        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                val lensView = lensViewWithCount(scenario, expected = 2)
                scenario.onActivity {
                    lensView.showAppOptionsAtIndex(0)
                    lensView.quickActionsMenuForTest!!.menu.performIdentifierAction(R.id.menuItemRemoveFromLens, 0)
                }
                lensViewWithCount(scenario, expected = 1)
                assertEquals(1, selectionOf().size)
                assertEquals(
                    "the other lens must keep both apps",
                    setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(chat)),
                    UtilSettings(context).getLensAppSelection(other)
                )
            }
        } finally {
            UtilSettings(context).deleteLensSettings(other)
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 100L
    }
}
