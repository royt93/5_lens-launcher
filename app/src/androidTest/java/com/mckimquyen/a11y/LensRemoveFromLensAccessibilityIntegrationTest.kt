package com.mckimquyen.a11y

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
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
import com.mckimquyen.views.LensView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-021 + A11Y-001: "Remove from this lens" must be reachable without touch. TalkBack's long
 * click goes through [com.mckimquyen.views.LensAccessibilityHelper] to the same menu a touch
 * long-press opens, so these tests drive that accessibility action, not the touch path.
 */
@RunWith(AndroidJUnit4::class)
class LensRemoveFromLensAccessibilityIntegrationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private var originalApps: ArrayList<App>? = null

    private val mail = App(id = 1, label = "Mail", packageName = "com.test.a11y.rm.mail", name = "Main", isVisible = true)
    private val chat = App(id = 2, label = "Chat", packageName = "com.test.a11y.rm.chat", name = "Main", isVisible = true)

    @Before
    fun setup() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        originalApps = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = arrayListOf(mail, chat)
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

    private fun selectBoth() {
        UtilSettings(context).saveLensAppSelection(lensId, setOf(LensAppScope.identifierOf(mail), LensAppScope.identifierOf(chat)))
        UtilSettings(context).saveLensAppScope(lensId, LensAppScope.SELECTED)
    }

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

    @Test
    fun anAppAdvertisesTheLongClickActionSoTalkBackCanOpenItsMenu() {
        selectBoth()
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val lensView = lensViewWithCount(scenario, expected = 2)
            scenario.onActivity {
                val node = AccessibilityNodeInfoCompat.obtain()
                lensView.getAccessibilityHelper()!!.testPopulateNodeForVirtualView(0, node)
                val actions = node.actionList.map { it.id }
                assertTrue("TalkBack needs ACTION_LONG_CLICK to reach the app menu",
                    AccessibilityNodeInfoCompat.ACTION_LONG_CLICK in actions)
            }
        }
    }

    @Test
    fun theAccessibilityLongClickOpensTheMenuWithRemoveOfferedOnASelectedLens() {
        selectBoth()
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val lensView = lensViewWithCount(scenario, expected = 2)
            scenario.onActivity {
                val done = lensView.getAccessibilityHelper()!!
                    .testPerformActionForVirtualView(0, AccessibilityNodeInfoCompat.ACTION_LONG_CLICK, null)
                assertTrue("the accessibility long click must succeed", done)
                val item = lensView.quickActionsMenuForTest!!.menu.findItem(R.id.menuItemRemoveFromLens)
                assertNotNull(item)
                assertTrue("TalkBack users must be offered Remove from this lens", item.isVisible)
            }
        }
    }

    @Test
    fun removingThroughTheAccessibilityPathUpdatesTheLens() {
        selectBoth()
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val lensView = lensViewWithCount(scenario, expected = 2)
            scenario.onActivity {
                lensView.getAccessibilityHelper()!!
                    .testPerformActionForVirtualView(0, AccessibilityNodeInfoCompat.ACTION_LONG_CLICK, null)
                lensView.quickActionsMenuForTest!!.menu.performIdentifierAction(R.id.menuItemRemoveFromLens, 0)
            }
            lensViewWithCount(scenario, expected = 1)
            assertEquals(1, UtilSettings(context).getLensAppSelection(lensId).size)
        }
    }

    @Test
    fun onAnAllLensTheAccessibilityMenuDoesNotOfferRemove() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val lensView = lensViewWithCount(scenario, expected = 2)
            scenario.onActivity {
                lensView.getAccessibilityHelper()!!
                    .testPerformActionForVirtualView(0, AccessibilityNodeInfoCompat.ACTION_LONG_CLICK, null)
                assertFalse(lensView.quickActionsMenuForTest!!.menu.findItem(R.id.menuItemRemoveFromLens).isVisible)
            }
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 100L
    }
}
