package com.mckimquyen.views

import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActHome
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-007 end-to-end on the real ActHome window:
 * 1. Default (setting off): dragging produces NO blur (Mode.OFF) - owner requirement respected.
 * 2. Setting enabled: dragging turns on the hardware RenderEffect BLUR path on API 31+.
 * 3. Gesture cancel turns it back off.
 */
@RunWith(AndroidJUnit4::class)
class LensViewDepthOfFieldIntegrationTest {

    private companion object {
        const val APP_COUNT = 30
        const val DRAG_STEPS = 12
        const val STEP_DELAY_MS = 16L
        const val SETTLE_TIMEOUT_MS = 3_000L
        const val POLL_MS = 50L
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        RAppsSingleton.instance.apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish007.int$i", name = "Int$i", label = "Int $i")
        })
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        RAppsSingleton.instance.clearAllData()
    }

    private fun inject(lensView: LensView, action: Int, downTime: Long, x: Float, y: Float) {
        instrumentation.runOnMainSync {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            lensView.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun dragAcrossGrid(view: LensView, downTime: Long, w: Float, h: Float) {
        inject(view, MotionEvent.ACTION_DOWN, downTime, w / 4, h / 4)
        for (i in 1..DRAG_STEPS) {
            inject(view, MotionEvent.ACTION_MOVE, downTime, w / 4 + w / 2 * i / DRAG_STEPS, h / 4 + h / 2 * i / DRAG_STEPS)
            SystemClock.sleep(STEP_DELAY_MS)
        }
    }

    private fun waitFor(condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + SETTLE_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            var ok = false
            instrumentation.runOnMainSync { ok = condition() }
            if (ok) return true
            SystemClock.sleep(POLL_MS)
        }
        return false
    }

    @Test
    fun defaultSettingOff_dragProducesNoBlur() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var lensView: LensView? = null
            scenario.onActivity { lensView = it.findViewById(R.id.lensViews) }
            val view = requireNotNull(lensView)
            instrumentation.waitForIdleSync()
            var w = 0f
            var h = 0f
            instrumentation.runOnMainSync { w = view.width.toFloat(); h = view.height.toFloat() }

            val downTime = SystemClock.uptimeMillis()
            dragAcrossGrid(view, downTime, w, h)

            assertTrue("setting is off: must remain OFF during drag",
                waitFor { view.dofLastMode == DepthOfField.Mode.OFF })
            inject(view, MotionEvent.ACTION_CANCEL, downTime, w * 3 / 4, h * 3 / 4)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test
    fun settingEnabled_dragUsesHardwareBlur_thenCancelTurnsItOff() {
        UtilSettings(context).save(UtilSettings.KEY_DEPTH_OF_FIELD, true)

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var lensView: LensView? = null
            scenario.onActivity { lensView = it.findViewById(R.id.lensViews) }
            val view = requireNotNull(lensView)
            instrumentation.waitForIdleSync()
            var w = 0f
            var h = 0f
            instrumentation.runOnMainSync { w = view.width.toFloat(); h = view.height.toFloat() }

            val downTime = SystemClock.uptimeMillis()
            dragAcrossGrid(view, downTime, w, h)

            assertTrue("setting enabled: drag must reach hardware BLUR",
                waitFor { view.dofLastMode == DepthOfField.Mode.BLUR && view.dofLastTransition >= 1f })
            var bandTotal = 0
            var blurred = 0
            instrumentation.runOnMainSync {
                bandTotal = view.dofBandCounts.sum()
                blurred = view.dofBandCounts.drop(1).sum()
            }
            var drawn = 0
            instrumentation.runOnMainSync { drawn = RAppsSingleton.instance.apps?.size ?: 0 }
            assertEquals("every drawn icon lands in exactly one band", drawn, bandTotal)
            assertTrue("icons away from the finger are blurred", blurred > 0)

            inject(view, MotionEvent.ACTION_CANCEL, downTime, w * 3 / 4, h * 3 / 4)
            instrumentation.runOnMainSync { view.invalidate() }
            assertTrue(waitFor { view.dofLastMode == DepthOfField.Mode.OFF })
        }
    }

    @Test
    fun idleHome_noLensTouch_meansDepthOfFieldIsOff() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var lensView: LensView? = null
            scenario.onActivity { lensView = it.findViewById(R.id.lensViews) }
            val view = requireNotNull(lensView)
            instrumentation.runOnMainSync { view.invalidate() }
            instrumentation.waitForIdleSync()
            assertTrue(waitFor { view.dofLastMode == DepthOfField.Mode.OFF })
        }
    }
}
