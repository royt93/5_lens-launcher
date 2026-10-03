package com.mckimquyen.views

import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActHome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-022: the one gap a bare, unattached `LensView` (as `LensViewWidgetTest`/
 * `LensViewAccessibilityWidgetTest` construct it) can't prove — that the quick-actions
 * `PopupMenu` genuinely shows on a real, attached, themed window, not just "doesn't crash".
 * A real bug was found this exact way while building this story: `androidx.appcompat.widget
 * .PopupMenu.inflate()` threw on a non-Activity (bare Application) context because the
 * `PopupMenuTheme` overlay had no base Material3 theme to layer onto - this test uses the
 * real `ActHome` Activity (same pattern `AdaptiveMultiWindowIntegrationTest` already
 * established) specifically so that class of gap can't hide behind a swallowed exception.
 */
@RunWith(AndroidJUnit4::class)
class LensViewQuickActionsIntegrationTest {

    private companion object {
        const val GRID_APP_COUNT = 12
        const val FIRST_INDEX = 0
        const val LAST_INDEX = GRID_APP_COUNT - 1
        const val LAYOUT_TIMEOUT_MS = 5_000L
        const val POLL_MS = 100L
    }

    private fun gridApps() = ArrayList(
        (0 until GRID_APP_COUNT).map {
            App(packageName = "com.ui025.test.app$it", name = "App$it", label = "App $it")
        }
    )

    /** Launches ActHome and waits until the grid has been drawn once, so icon bounds exist. */
    private fun launchWithDrawnGrid(): ActivityScenario<ActHome> {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { it.findViewById<LensView>(R.id.lensViews).setApps(gridApps()) }
        val deadline = SystemClock.uptimeMillis() + LAYOUT_TIMEOUT_MS
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                ready = it.findViewById<LensView>(R.id.lensViews).getAppBounds(LAST_INDEX, Rect())
            }
            if (!ready) SystemClock.sleep(POLL_MS)
        }
        assertTrue("the lens grid never drew, so icon bounds are unavailable", ready)
        return scenario
    }

    /** Screen position the popup anchor must have for [index]: the icon cell's bottom-centre. */
    private fun expectedAnchorScreenPosition(lensView: LensView, index: Int): Pair<Int, Int> {
        val bounds = Rect()
        assertTrue(lensView.getAppBounds(index, bounds))
        val lensOnScreen = IntArray(2).also { lensView.getLocationOnScreen(it) }
        return (lensOnScreen[0] + bounds.centerX()) to (lensOnScreen[1] + bounds.bottom)
    }

    private fun anchorScreenPosition(anchor: View): Pair<Int, Int> {
        val onScreen = IntArray(2).also { anchor.getLocationOnScreen(it) }
        return onScreen[0] to onScreen[1]
    }

    @Before
    fun setup() {
        RAppsSingleton.instance.apps = arrayListOf(
            App(packageName = "com.ui022.test.app0", name = "App0", label = "App 0")
        )
    }

    @After
    fun tearDown() {
        RAppsSingleton.instance.clearAllData()
    }

    @Test
    fun testShowAppOptionsAtIndex_realAttachedThemedWindow_actuallySucceeds() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        try {
            var result = false
            scenario.onActivity { activity ->
                val lensView = activity.findViewById<LensView>(R.id.lensViews)
                result = lensView.showAppOptionsAtIndex(0)
            }
            assertTrue(
                "a real, attached, themed LensView must actually show the quick-actions popup, not silently fail",
                result
            )
        } finally {
            scenario.close()
        }
    }

    /**
     * A11Y-001 non-gesture-path acceptance, proven end-to-end this time (not just "reaches
     * the method" as `LensViewAccessibilityWidgetTest` proves on a bare, unattached view):
     * TalkBack's real long-click action, through the real accessibility helper, on a real
     * attached+themed window, must succeed exactly like the touch long-press does.
     */
    @Test
    fun testAccessibilityLongClickAction_realAttachedThemedWindow_actuallySucceeds() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        try {
            var handled = false
            scenario.onActivity { activity ->
                val lensView = activity.findViewById<LensView>(R.id.lensViews)
                val helper = lensView.getAccessibilityHelper()
                handled = helper?.testPerformActionForVirtualView(
                    0,
                    AccessibilityNodeInfoCompat.ACTION_LONG_CLICK,
                    null
                ) ?: false
            }
            assertTrue(
                "TalkBack's long-click action must reach the same quick-actions popup and actually show it",
                handled
            )
        } finally {
            scenario.close()
        }
    }

    @Test
    fun quickActionsMenu_isAnchoredAtThePressedIconsCell() {
        launchWithDrawnGrid().use { scenario ->
            scenario.onActivity { activity ->
                val lensView = activity.findViewById<LensView>(R.id.lensViews)

                assertTrue(lensView.showAppOptionsAtIndex(FIRST_INDEX))
                val firstAnchor = lensView.quickActionsAnchorForTest
                assertNotNull("an anchor must exist while the menu is open", firstAnchor)
                val firstPosition = anchorScreenPosition(firstAnchor!!)
                assertEquals(expectedAnchorScreenPosition(lensView, FIRST_INDEX), firstPosition)

                assertTrue(lensView.showAppOptionsAtIndex(LAST_INDEX))
                val secondPosition = anchorScreenPosition(lensView.quickActionsAnchorForTest!!)
                assertEquals(expectedAnchorScreenPosition(lensView, LAST_INDEX), secondPosition)

                assertNotEquals(
                    "different icons must anchor at different places, not one fixed spot",
                    firstPosition,
                    secondPosition
                )
            }
        }
    }

    @Test
    fun quickActionsMenu_neverLeavesMoreThanOneAnchor() {
        launchWithDrawnGrid().use { scenario ->
            scenario.onActivity { activity ->
                val lensView = activity.findViewById<LensView>(R.id.lensViews)
                val parent = lensView.parent as ViewGroup
                val before = parent.childCount

                assertTrue(lensView.showAppOptionsAtIndex(FIRST_INDEX))
                assertTrue(lensView.showAppOptionsAtIndex(LAST_INDEX))

                assertEquals("exactly one anchor may be added", before + 1, parent.childCount)
            }
        }
    }

    @Test
    fun quickActionsMenu_removesItsAnchorWhenDismissed() {
        launchWithDrawnGrid().use { scenario ->
            var parent: ViewGroup? = null
            var before = 0
            scenario.onActivity { activity ->
                val lensView = activity.findViewById<LensView>(R.id.lensViews)
                parent = lensView.parent as ViewGroup
                before = parent!!.childCount
                assertTrue(lensView.showAppOptionsAtIndex(FIRST_INDEX))
                assertEquals(before + 1, parent!!.childCount)
                lensView.quickActionsMenuForTest!!.dismiss()
            }
            val deadline = SystemClock.uptimeMillis() + LAYOUT_TIMEOUT_MS
            while (parent!!.childCount != before && SystemClock.uptimeMillis() < deadline) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                SystemClock.sleep(POLL_MS)
            }
            assertEquals("dismissing the menu must remove its anchor", before, parent!!.childCount)
            scenario.onActivity {
                assertNull(it.findViewById<LensView>(R.id.lensViews).quickActionsAnchorForTest)
            }
        }
    }

    @Test
    fun quickActionsMenu_removesItsAnchorWhenTheLensIsDetached() {
        var anchor: View? = null
        launchWithDrawnGrid().use { scenario ->
            scenario.onActivity { activity ->
                val lensView = activity.findViewById<LensView>(R.id.lensViews)
                assertTrue(lensView.showAppOptionsAtIndex(FIRST_INDEX))
                anchor = lensView.quickActionsAnchorForTest
                assertNotNull(anchor!!.parent)
            }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.DESTROYED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        assertNull("a detached lens must not leave its anchor attached to the page", anchor!!.parent)
    }
}
