package com.mckimquyen.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.enums.LauncherMode
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.RingRotation
import com.mckimquyen.util.UtilSettings
import com.mckimquyen.views.LensView
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * FISH-035 on-device gesture smoke: the real ActHome, the real double-tap that pins focus, then ONE continuous
 * single-pointer arc delivered as a MotionEvent stream through the real input pipeline
 * (UiAutomation.injectInputEvent; Instrumentation.sendPointerSync is rejected on Android 17), which adb `input` cannot do
 * because it spaces events ~75 ms apart.
 * It proves pin -> classify -> rotate -> one commit -> frozen + persisted order, end to end on the real activity.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeRingRotateGestureSmokeTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val lensId = LensWorkspace.DEFAULT_LENS_ID
    private val dao get() = AppDatabase.getInstance().appPersistentDao()
    private var originalApps: ArrayList<App>? = null
    private var roomSnapshot: LensManagerRoomSnapshot? = null

    private val fixture = listOf(
        row("f", PinnedZone.START, false), row("d", PinnedZone.NONE, true), row("e", PinnedZone.NONE, true),
        row("a", PinnedZone.NONE, false), row("b", PinnedZone.NONE, false), row("c", PinnedZone.NONE, false)
    )

    private fun row(n: String, zone: PinnedZone, fav: Boolean) = App(
        id = n.hashCode(), label = n.uppercase(), packageName = "com.test.ringsmoke.$n", name = "Main",
        isVisible = true, pinnedZone = zone, isFavorite = fav
    )

    @Before fun setup() {
        // A previous crashed run can leave the injector's virtual finger down; an UP is harmless if none is.
        runCatching { releasePointer(1f, 1f) }
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        roomSnapshot = LensManagerRoomSnapshot.capture(context)
        originalApps = RAppsSingleton.instance.apps
        val settings = UtilSettings(context)
        settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, lensId)
        settings.setLauncherMode(LauncherMode.FISHEYE)
        settings.saveLensFrozen(lensId, false)
        settings.saveSmartFocusBias(lensId, false)
        settings.saveLensAppScope(lensId, LensAppScope.ALL)
        settings.saveLensHiddenAppIds(lensId, emptySet())
        runBlocking {
            dao.deleteForLens(lensId)
            fixture.forEachIndexed { i, a ->
                dao.insert(AppPersistent.defaults(a.packageName.toString(), "Main", lensId)
                    .copy(orderNumber = i, isFavorite = a.isFavorite, pinnedZone = a.pinnedZone.name))
            }
        }
        RAppsSingleton.instance.apps = ArrayList(fixture)
    }

    @After fun tearDown() {
        UtilSettings(context).saveLensFrozen(lensId, false)
        roomSnapshot?.restore(context)
        RAppsSingleton.instance.apps = originalApps
    }

    private fun awaitGrid(scenario: ActivityScenario<ActHome>): LensView {
        var view: LensView? = null
        var size = 0
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline && size != fixture.size) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { view = it.findViewById(R.id.lensViews); size = view?.displayedApps?.size ?: 0 }
            if (size != fixture.size) Thread.sleep(POLL_MS)
        }
        assertEquals("the grid never held the fixture", fixture.size, size)
        return requireNotNull(view) { "assigned in onActivity above" }
    }

    private fun labels(view: LensView) = view.displayedApps.map { it.label.toString() }

    private fun storedLabels() = runBlocking { dao.getAllForLens(lensId) }.sortedBy { it.orderNumber }
        .map { it.packageName?.substringAfterLast('.')?.uppercase() }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline && !condition()) { instrumentation.waitForIdleSync(); Thread.sleep(POLL_MS) }
        assertTrue("timed out: $what", condition())
    }

    /** Screen position of a point inside the view, for the window-level injector. */
    private fun onScreen(view: LensView, x: Float, y: Float): Pair<Float, Float> {
        val loc = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(loc) }
        return (loc[0] + x) to (loc[1] + y)
    }

    /** Cleanup path called from finally if an assertion throws while a pointer is still down. */
    private fun releasePointer(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        check(instrumentation.uiAutomation.injectInputEvent(
            MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0).apply {
                source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            }, true
        )) { "the device rejected cleanup ACTION_UP" }
    }

    private fun inject(action: Int, downTime: Long, eventTime: Long, x: Float, y: Float) {
        val e = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        e.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        check(instrumentation.uiAutomation.injectInputEvent(e, true)) { "the device rejected MotionEvent action=$action" }
        e.recycle()
    }

    /** A held tap on a screen point; [holdMs] keeps a frame between DOWN and UP like a finger. */
    private fun tapAt(x: Float, y: Float, holdMs: Long = TAP_HOLD_MS) {
        val down = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, down, down, x, y)
        SystemClock.sleep(holdMs)
        inject(MotionEvent.ACTION_UP, down, SystemClock.uptimeMillis(), x, y)
    }

    @Test fun aRealDoubleTapPinsThenOneContinuousArcRotatesGroupsAndCommitsOnce() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitGrid(scenario)
            val before = labels(view)
            val centre = onScreen(view, view.width / 2f, view.height * EMPTY_Y_FRACTION)
            // an empty spot below the six-icon grid; pin with the real double-tap path
            tapAt(centre.first, centre.second)
            SystemClock.sleep(DOUBLE_TAP_GAP_MS)
            tapAt(centre.first, centre.second)
            waitUntil("focus pinned by the real double tap") { view.isFocusPinnedForTest() }

            // one continuous single-pointer arc around the pinned point, 8 ms per MOVE, ~1.2 steps
            val radius = ARC_RADIUS_PX
            val sweep = RingRotation.STEP_RADIANS * ARC_STEPS
            val perFrame = ARC_SPEED_RAD_PER_SEC * FRAME_MS / 1000f
            val frames = (abs(sweep) / perFrame).toInt()
            val cx = centre.first
            val cy = centre.second
            fun px(a: Float) = cx + radius * cos(a)
            fun py(a: Float) = cy + radius * sin(a)
            val down = SystemClock.uptimeMillis()
            inject(MotionEvent.ACTION_DOWN, down, down, px(0f), py(0f))
            var released = false
            try {
                for (i in 1..frames) {
                    val target = down + i * FRAME_MS
                    val wait = target - SystemClock.uptimeMillis()
                    if (wait > 0) SystemClock.sleep(wait)
                    inject(MotionEvent.ACTION_MOVE, down, target, px(perFrame * i), py(perFrame * i))
                }
                assertTrue("the arc was classified as a rotation", view.isRingRotatingForTest())
                val last = down + (frames + 1) * FRAME_MS
                inject(MotionEvent.ACTION_UP, down, last, px(perFrame * frames), py(perFrame * frames))
                released = true
            } finally {
                if (!released) releasePointer(px(perFrame * frames), py(perFrame * frames))
            }

            waitUntil("frozen after the confirmed write") { UtilSettings(context).isLensFrozen(lensId) }
            val expected = listOf("F", "E", "D", "B", "C", "A")
            waitUntil("rows rewritten once in group-rotated order") { storedLabels() == expected }
            scenario.onActivity { assertEquals(expected, labels(view)) }
            assertFalse("the grid order changed from the start", before == expected)
            assertNotNull(view.pinnedFocusForTest())
        }
    }

    /**
     * The injector keeps one virtual device across tests: a gesture left without UP makes every later DOWN fail with
     * "pointers already down". A test that aborts mid-arc must therefore still release the pointer.
     */
    @Test fun anAbortedArcStillReleasesThePointerSoTheNextGestureCanStart() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitGrid(scenario)
            val spot = onScreen(view, view.width / 2f, view.height * EMPTY_Y_FRACTION)
            try {
                val down = SystemClock.uptimeMillis()
                inject(MotionEvent.ACTION_DOWN, down, down, spot.first, spot.second)
                inject(MotionEvent.ACTION_MOVE, down, down + FRAME_MS, spot.first + 5f, spot.second)
                error("simulated failure while the finger is down")
            } catch (expected: IllegalStateException) {
                assertEquals("simulated failure while the finger is down", expected.message)
            } finally {
                releasePointer(spot.first, spot.second)
            }
            val next = SystemClock.uptimeMillis()
            inject(MotionEvent.ACTION_DOWN, next, next, spot.first, spot.second)
            inject(MotionEvent.ACTION_UP, next, next + FRAME_MS, spot.first, spot.second)
        }
    }

    private companion object {
        const val WAIT_MS = 8_000L
        const val POLL_MS = 50L
        const val TAP_HOLD_MS = 90L
        const val DOUBLE_TAP_GAP_MS = 80L
        const val FRAME_MS = 8L
        const val ARC_RADIUS_PX = 300f
        const val ARC_SPEED_RAD_PER_SEC = 3f
        const val ARC_STEPS = 1.2f
        const val EMPTY_Y_FRACTION = 0.7f
    }
}
