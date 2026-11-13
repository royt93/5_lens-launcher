package com.mckimquyen.views

import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.search.AppSearchEngine
import com.mckimquyen.search.SearchHistoryStore
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** FISH-048 reachability: real DOWN/MOVE through dispatchTouchEvent and a real draw; no dot, index or pan hook. */
@RunWith(AndroidJUnit4::class)
class LensViewRecentDotsReachabilityTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val VIEW = 1_000
        const val APPS = 100
        const val PAN_DP = 40f
        const val FRAMES = 4
        const val FRAME_MS = 16L
        const val TARGET_CELL = 52
        const val ONE_MINUTE_MS = 60_000L
    }

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val density = ctx.resources.displayMetrics.density
    private val apps = ArrayList((0 until APPS).map { App(id = it, packageName = "com.f048.r$it", name = "R$it", label = "L$it") })

    @Before fun setUp() {
        SearchHistoryStore(ctx).clear()
        UtilSettings(ctx).save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
    }

    @After fun tearDown() {
        SearchHistoryStore(ctx).clear()
        UtilSettings(ctx).apply {
            save(UtilSettings.KEY_RECENT_DOTS, false)
            save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
        }
    }

    private fun keyOf(i: Int) = AppSearchEngine.componentKey(apps[i])

    /** A real pan onto the centre of [TARGET_CELL]; returns the most dots drawn in one frame while the finger is down. */
    private fun run(switchOn: Boolean, clean: Boolean = false, pan: Boolean = true): Int {
        UtilSettings(ctx).apply { save(UtilSettings.KEY_RECENT_DOTS, switchOn); save(UtilSettings.KEY_CLEAN_LENS_MODE, clean) }
        var most = 0
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { sc ->
            var v: LensView? = null
            sc.onActivity { a ->
                v = LensView(a).apply {
                    setApps(ArrayList(apps))
                    layoutParams = FrameLayout.LayoutParams(VIEW, VIEW); a.setContentView(this)
                    measure(View.MeasureSpec.makeMeasureSpec(VIEW, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(VIEW, View.MeasureSpec.EXACTLY))
                    layout(0, 0, VIEW, VIEW); draw(Canvas())
                }
            }
            val t = requireNotNull(v)
            val down = SystemClock.uptimeMillis()
            fun send(action: Int, x: Float, y: Float) = inst.runOnMainSync {
                val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0); t.dispatchTouchEvent(e); e.recycle()
            }
            val cell = t.baseRectsForTest()[TARGET_CELL]
            val x = cell.centerX(); val y = cell.centerY()
            send(MotionEvent.ACTION_DOWN, x - PAN_DP * density, y + PAN_DP * density)
            if (pan) {
                send(MotionEvent.ACTION_MOVE, x - PAN_DP * density / 2f, y + PAN_DP * density / 2f)
                send(MotionEvent.ACTION_MOVE, x, y)
            }
            repeat(FRAMES) {
                inst.runOnMainSync { t.draw(Canvas()); most = maxOf(most, t.recentDotsDrawnForTest) }
                SystemClock.sleep(FRAME_MS)
            }
            send(MotionEvent.ACTION_UP, x, y)
        }
        return most
    }

    @Test fun threeRecentLaunchesGetThreeDotsDuringARealPan() {
        listOf(10, 20, 30).forEach { SearchHistoryStore(ctx).recordLaunch(keyOf(it)) }
        assertEquals(3, run(switchOn = true))
    }

    @Test fun onlyTheThreeNewestOfFiveGetDots() {
        listOf(1, 2, 3, 4, 5).forEach { SearchHistoryStore(ctx).recordLaunch(keyOf(it)); SystemClock.sleep(5) }
        assertEquals(3, run(switchOn = true))
    }

    @Test fun aLaunchOlderThanTwentyFourHoursGetsNoDot() {
        var now = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(25)
        val old = SearchHistoryStore(ctx) { now }
        old.recordLaunch(keyOf(7))
        now = System.currentTimeMillis()
        SearchHistoryStore(ctx) { now }.recordLaunch(keyOf(8))
        assertEquals("only the fresh launch is marked", 1, run(switchOn = true))
    }

    @Test fun anEmptyHistoryDrawsNoDots() {
        assertEquals(0, run(switchOn = true))
    }

    @Test fun theSwitchOffDrawsNoDotsEvenWithHistory() {
        listOf(10, 20, 30).forEach { SearchHistoryStore(ctx).recordLaunch(keyOf(it)) }
        assertEquals(0, run(switchOn = false))
    }

    @Test fun cleanLensModeDrawsNoDots() {
        listOf(10, 20, 30).forEach { SearchHistoryStore(ctx).recordLaunch(keyOf(it)) }
        assertEquals(0, run(switchOn = true, clean = true))
    }

    @Test fun aTouchThatNeverPansDrawsNoDots() {
        listOf(10, 20, 30).forEach { SearchHistoryStore(ctx).recordLaunch(keyOf(it)) }
        assertEquals("positive control for the pan gate: the same history draws 3 once the finger pans", 3, run(switchOn = true))
        assertEquals(0, run(switchOn = true, pan = false))
    }

    @Test fun theDotsAreGoneAfterTheFingerLiftsAndAFreshTouchRecomputesThem() {
        listOf(10, 20, 30).forEach { SearchHistoryStore(ctx).recordLaunch(keyOf(it)) }
        assertEquals(3, run(switchOn = true))
        SearchHistoryStore(ctx).clear()
        assertEquals("a new touch reads the store again", 0, run(switchOn = true))
    }
}
