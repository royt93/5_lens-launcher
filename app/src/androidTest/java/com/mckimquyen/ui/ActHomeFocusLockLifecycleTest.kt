package com.mckimquyen.ui

import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.views.LensView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-029: focus lock is a Home-session visual aid. It must survive nothing: not a pause, not a recreate.
 * Runs against the real ActHome; DeviceStateGuard restores the prefs/apps the Activity touches.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeFocusLockLifecycleTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun doubleTapEmptySpace(view: LensView) {
        val x = view.width / 2f
        val y = view.height * EMPTY_SPACE_Y_FRACTION
        repeat(2) {
            instrumentation.runOnMainSync {
                val down = SystemClock.uptimeMillis()
                view.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
                view.draw(Canvas())
                view.dispatchTouchEvent(MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0))
            }
            SystemClock.sleep(TAP_GAP_MS)
        }
    }

    private fun activeLensView(scenario: ActivityScenario<ActHome>): LensView {
        var view: LensView? = null
        scenario.onActivity { view = it.lensViews }
        return requireNotNull(view)
    }

    private fun awaitLaidOut(scenario: ActivityScenario<ActHome>): LensView {
        val deadline = SystemClock.uptimeMillis() + WAIT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            var view: LensView? = null
            scenario.onActivity { view = it.lensViews }
            if (view != null && view!!.width > 0 && view!!.height > 0) return view!!
            SystemClock.sleep(POLL_MS)
        }
        return activeLensView(scenario)
    }

    @Test fun pausingHomeClearsAPinnedFocus() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitLaidOut(scenario)
            assertNotNull(view)
            instrumentation.runOnMainSync { assertTrue(view.toggleFocusLockAtForTest(view.width / 2f, view.height / 2f)) }
            assertTrue(view.isFocusPinnedForTest())

            scenario.moveToState(Lifecycle.State.STARTED)
            instrumentation.waitForIdleSync()

            assertFalse("Home pause must clear focus lock", view.isFocusPinnedForTest())
        }
    }

    @Test fun aPendingFirstTapDoesNotSurviveAPause() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            val view = awaitLaidOut(scenario)
            instrumentation.runOnMainSync { view.dispatchTouchEvent(MotionEvent.obtain(1, 1, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)) }
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            assertFalse(view.isFocusPinnedForTest())
            // one tap after resume must not pair with anything from before the pause
            doubleTapEmptySpace(view)
            assertTrue("a fresh double tap after resume still pins", view.isFocusPinnedForTest())
        }
    }

    private companion object {
        const val EMPTY_SPACE_Y_FRACTION = 0.5f
        const val TAP_GAP_MS = 80L
        const val WAIT_MS = 8_000L
        const val POLL_MS = 100L
    }
}
