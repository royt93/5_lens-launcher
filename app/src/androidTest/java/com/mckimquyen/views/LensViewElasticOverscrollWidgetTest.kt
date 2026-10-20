package com.mckimquyen.views

import android.os.SystemClock
import android.view.MotionEvent
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.ui.ActHome
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensViewElasticOverscrollWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()


    private companion object {
        const val DRAG_PAST_EDGE_PX = 200f
        const val DRAG_STEP_MS = 50L
        const val MAX_OVERSCROLL_DP = 28f
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val settings get() = UtilSettings(context)
    private var hadSetting = false
    private var originalSetting = true

    @Before
    fun setup() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        hadSetting = prefs.contains(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL)
        originalSetting = settings.getLensElasticOverscroll()
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
            if (hadSetting) putBoolean(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL, originalSetting)
            else remove(UtilSettings.KEY_LENS_ELASTIC_OVERSCROLL)
        }.commit()
    }

    /** Drags from the centre to (endX, endY) and returns the lens centre the view settled on. */
    private fun dragAndReadTouch(
        enabled: Boolean,
        endCoordinate: (Float, Float) -> Pair<Float, Float>
    ): Pair<Float, Float> {
        var result = Pair(0f, 0f)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            var lensView: LensView? = null
            scenario.onActivity { lensView = it.findViewById(R.id.lensViews) }
            val view = requireNotNull(lensView)
            val width = view.width.toFloat()
            val height = view.height.toFloat()
            settings.saveLensElasticOverscroll(enabled)
            instrumentation.runOnMainSync {
                val downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, width * 0.5f, height * 0.5f, 0)
                view.dispatchTouchEvent(down)
                down.recycle()
                val move = MotionEvent.obtain(
                    downTime, downTime + DRAG_STEP_MS, MotionEvent.ACTION_MOVE,
                    endCoordinate(width, height).first, endCoordinate(width, height).second, 0
                )
                view.dispatchTouchEvent(move)
                move.recycle()
                result = view.touchCoordinatesForTest()
                val cancel = MotionEvent.obtain(downTime, downTime + 2 * DRAG_STEP_MS, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
                view.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
        }
        return result
    }

    @Test
    fun disabled_clampsHardOnEveryEdge() {
        settings.saveLensElasticOverscroll(false)
        val (rightX, bottomY) = dragAndReadTouch(enabled = false) { width, height -> Pair(width + DRAG_PAST_EDGE_PX, height + DRAG_PAST_EDGE_PX) }
        val view = lensSize()
        assertEquals("right edge must clamp hard", view.first, rightX, 0.5f)
        assertEquals("bottom edge must clamp hard", view.second, bottomY, 0.5f)
    }

    @Test
    fun enabled_pastRightAndBottomEdge_overscrollsButStaysBounded() {
        settings.saveLensElasticOverscroll(true)
        val (x, y) = dragAndReadTouch(enabled = true) { width, height -> Pair(width + DRAG_PAST_EDGE_PX, height + DRAG_PAST_EDGE_PX) }
        val (width, height) = lensSize()
        val limit = MAX_OVERSCROLL_DP * context.resources.displayMetrics.density
        assertTrue("x must overscroll past $width, was $x", x > width)
        assertTrue("x must stay within $limit of the edge, was $x", x <= width + limit)
        assertTrue("y must overscroll past $height, was $y", y > height)
        assertTrue("y must stay within $limit of the edge, was $y", y <= height + limit)
    }

    @Test
    fun enabled_pastLeftAndTopEdge_lensStaysActiveAndBounded() {
        settings.saveLensElasticOverscroll(true)
        val (x, y) = dragAndReadTouch(enabled = true) { _, _ -> Pair(-DRAG_PAST_EDGE_PX, -DRAG_PAST_EDGE_PX) }
        val limit = MAX_OVERSCROLL_DP * context.resources.displayMetrics.density
        assertTrue("x must overscroll before the left edge, was $x", x < 0f)
        assertTrue("x must stay within $limit of the edge, was $x", x >= -limit)
        assertTrue("y must overscroll before the top edge, was $y", y < 0f)
        assertTrue("y must stay within $limit of the edge, was $y", y >= -limit)
        assertTrue("lens must still read as active (not the hidden sentinel)", x > -Float.MAX_VALUE / 2)
    }

    private fun lensSize(): Pair<Float, Float> {
        var size = Pair(0f, 0f)
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val v = it.findViewById<LensView>(R.id.lensViews)
                size = Pair(v.width.toFloat(), v.height.toFloat())
            }
        }
        return size
    }
}
