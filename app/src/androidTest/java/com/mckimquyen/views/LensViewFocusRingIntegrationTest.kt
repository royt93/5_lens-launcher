package com.mckimquyen.views

import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.search.QuickShortcut
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-031: real LensView long-press -> ring -> slide -> release path, hosted inside a neutral
 * [OrganizationHarnessActivity] window so it is fully attached, laid out and drawn without
 * ActHome's live background loaders mutating the icon list.
 */
@RunWith(AndroidJUnit4::class)
class LensViewFocusRingIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val WIDTH_PX = 1080
        const val HEIGHT_PX = 1920
        const val SETTLE_MS = 600L
        const val POLL_MS = 25L
        const val WAIT_MS = 4_000L
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val menuOpened = mutableListOf<Int>()
    private val launched = mutableListOf<String>()
    private var shortcutsToOffer: List<QuickShortcut> = listOf(shortcut("a"), shortcut("b"))

    private fun shortcut(id: String) = QuickShortcut(id, "pkg.camera", "Label $id", null)

    @Before fun setup() {
        val target = instrumentation.targetContext
        PreferenceManager.getDefaultSharedPreferences(target).edit().clear().commit()
    }

    @After fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext).edit().clear().commit()
    }

    private fun runTest(block: (ActivityScenario<OrganizationHarnessActivity>, LensView) -> Unit) {
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            var lv: LensView? = null
            scenario.onActivity { activity ->
                val view = LensView(activity)
                view.setApps(arrayListOf(App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")))
                view.appOptionsOpener = { index -> menuOpened += index; true }
                view.shortcutProviderForTest = { _, _ -> shortcutsToOffer }
                view.shortcutLauncherForTest = { _, sc, _ -> launched += sc.id; true }
                activity.setContentView(view, FrameLayout.LayoutParams(WIDTH_PX, HEIGHT_PX))
                lv = view
            }
            instrumentation.waitForIdleSync()
            val view = requireNotNull(lv)
            // Draw once so base rects and icon bounds are measured
            instrumentation.runOnMainSync { view.draw(Canvas()) }
            block(scenario, view)
        }
    }

    private fun iconCentre(view: LensView): Pair<Float, Float> {
        var out = Pair(-1f, -1f)
        instrumentation.runOnMainSync {
            view.draw(Canvas())
            val r = Rect()
            if (view.getAppBounds(0, r)) out = Pair(r.exactCenterX(), r.exactCenterY())
        }
        assertTrue("first app must have bounds", out.first >= 0f)
        return out
    }

    private fun send(view: LensView, downTime: Long, action: Int, x: Float, y: Float) = instrumentation.runOnMainSync {
        val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        view.dispatchTouchEvent(e)
        e.recycle()
    }

    private fun awaitRing(view: LensView, timeoutMs: Long = WAIT_MS): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            var active = false
            instrumentation.runOnMainSync { active = view.isFocusRingActiveForTest() }
            if (active) return true
            SystemClock.sleep(POLL_MS)
        }
        return false
    }

    private fun holdIcon(view: LensView): Triple<Float, Float, Long> {
        val (cx, cy) = iconCentre(view)
        val down = SystemClock.uptimeMillis()
        send(view, down, MotionEvent.ACTION_DOWN, cx, cy)
        // Draw once against the DOWN point so mSelectIndex is computed (matches LensViewWidgetTest)
        instrumentation.runOnMainSync { view.draw(Canvas()) }
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + SETTLE_MS)
        return Triple(cx, cy, down)
    }

    @Test fun holdingAnIconWithShortcutsRaisesTheRingAndKeepsTheMenuClosed() = runTest { _, view ->
        val (_, _, down) = holdIcon(view)
        assertTrue("ring must appear", awaitRing(view))
        assertEquals(2, view.focusRingSlotsForTest().size)
        assertTrue("menu must wait for release while ring is up", menuOpened.isEmpty())
        send(view, down, MotionEvent.ACTION_CANCEL, 0f, 0f)
    }

    @Test fun slidingOntoAChipAndReleasingRunsThatShortcutOnly() = runTest { _, view ->
        val (_, _, down) = holdIcon(view)
        assertTrue(awaitRing(view))
        val chip = view.focusRingSlotsForTest()[1].rect
        send(view, down, MotionEvent.ACTION_MOVE, chip.centerX(), chip.centerY())
        assertEquals(1, view.activeFocusChipIndexForTest())
        send(view, down, MotionEvent.ACTION_UP, chip.centerX(), chip.centerY())
        assertEquals(listOf("b"), launched)
        assertTrue("running shortcut must not open menu", menuOpened.isEmpty())
        assertFalse(view.isFocusRingActiveForTest())
    }

    @Test fun releasingOffEveryChipOpensTheAppMenuInstead() = runTest { _, view ->
        val (cx, cy, down) = holdIcon(view)
        assertTrue(awaitRing(view))
        send(view, down, MotionEvent.ACTION_UP, cx, cy)
        assertTrue("no shortcut may run", launched.isEmpty())
        assertEquals(listOf(0), menuOpened)
        assertFalse(view.isFocusRingActiveForTest())
    }

    /** Single lens (default): an app without shortcuts opens the menu at timeout as before. */
    @Test fun anAppWithoutShortcutsOpensTheMenuAtTheTimeout() = runTest { _, view ->
        shortcutsToOffer = emptyList()
        val (_, _, down) = holdIcon(view)
        val deadline = SystemClock.uptimeMillis() + WAIT_MS
        while (menuOpened.isEmpty() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(POLL_MS)
        assertEquals("single lens opens menu at timeout", listOf(0), menuOpened)
        assertFalse(view.isFocusRingActiveForTest())
        send(view, down, MotionEvent.ACTION_CANCEL, 0f, 0f)
    }

    /** Multi lens: an app without shortcuts waits for release (matches FISH-038b drag policy). */
    @Test fun multiLensWithoutShortcutsWaitsForRelease() = runTest { _, view ->
        shortcutsToOffer = emptyList()
        instrumentation.runOnMainSync { view.setHasOtherLenses(true) }
        val (cx, cy, down) = holdIcon(view)
        assertTrue("multi lens must not open menu at timeout", menuOpened.isEmpty())
        send(view, down, MotionEvent.ACTION_UP, cx, cy)
        assertEquals(listOf(0), menuOpened)
    }

    @Test fun cleanModeNeverShowsTheRing() = runTest { _, view ->
        UtilSettings(instrumentation.targetContext).save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        val (_, _, down) = holdIcon(view)
        val deadline = SystemClock.uptimeMillis() + WAIT_MS
        while (menuOpened.isEmpty() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(POLL_MS)
        assertFalse(view.isFocusRingActiveForTest())
        assertEquals(listOf(0), menuOpened)
        send(view, down, MotionEvent.ACTION_CANCEL, 0f, 0f)
    }

    @Test fun cancellingTheTouchTearsTheRingDownWithoutRunningAnything() = runTest { _, view ->
        val (_, _, down) = holdIcon(view)
        assertTrue(awaitRing(view))
        send(view, down, MotionEvent.ACTION_CANCEL, 0f, 0f)
        assertFalse(view.isFocusRingActiveForTest())
        assertTrue(launched.isEmpty())
        assertTrue(menuOpened.isEmpty())
    }

    @Test fun releasingBeforeTheShortcutsLoadNeverRaisesALateRing() = runTest { _, view ->
        shortcutsToOffer = listOf(shortcut("slow"))
        view.shortcutProviderForTest = { _, _ -> SystemClock.sleep(SETTLE_MS * 2); shortcutsToOffer }
        val (cx, cy) = iconCentre(view)
        val down = SystemClock.uptimeMillis()
        send(view, down, MotionEvent.ACTION_DOWN, cx, cy)
        instrumentation.runOnMainSync { view.draw(Canvas()) }
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + POLL_MS)
        send(view, down, MotionEvent.ACTION_UP, cx, cy)
        SystemClock.sleep(SETTLE_MS * 3)
        assertFalse("a stale load must not raise ring late", view.isFocusRingActiveForTest())
        assertTrue(launched.isEmpty())
    }
}
