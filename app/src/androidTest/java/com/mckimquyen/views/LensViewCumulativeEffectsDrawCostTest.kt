package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.util.BitmapCache
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cumulative attribution benchmark, not a gate: median CPU time of drawGrid (software canvas, real icons, real pan)
 * as the optical effects are switched on one after another. Log tag F044CUM. Only asserts that every stage ran and
 * that all effects together stay within [MAX_TOTAL_TO_BASE_RATIO] of the bare lens.
 */
@RunWith(AndroidJUnit4::class)
class LensViewCumulativeEffectsDrawCostTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val VIEW_SIZE_PX = 1_000
        const val APP_COUNT = 120
        const val ICON_PX = 48
        const val WARMUP_DRAWS = 200
        const val DRAWS_PER_ROUND = 200
        const val ROUNDS = 7
        const val NANOS_PER_MICRO = 1_000L
        const val TAG = "F044CUM"
        const val MAX_TOTAL_TO_BASE_RATIO = 6.0
        const val PAN_START_DP = 40f
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val density = context.resources.displayMetrics.density

    private val stages = listOf(
        "BASE (no effects)" to emptyList<String>(),
        "+DoF" to listOf(UtilSettings.KEY_DEPTH_OF_FIELD),
        "+Vignette" to listOf(UtilSettings.KEY_LENS_VIGNETTE),
        "+Peaking" to listOf(UtilSettings.KEY_FOCUS_PEAKING),
        "+Chromatic" to listOf(UtilSettings.KEY_CHROMATIC_ABERRATION),
        "+Inertia" to listOf(UtilSettings.KEY_LENS_INERTIA),
        "+Snap" to listOf(UtilSettings.KEY_FOCUS_SNAP),
        "+ClusterHaptic" to listOf(UtilSettings.KEY_VIBRATE_APP_HOVER),
    )
    private val allKeys = stages.flatMap { it.second }

    @After fun tearDown() {
        val us = UtilSettings(context)
        allKeys.forEach { us.save(it, false) }
        BitmapCache.clear()
    }

    @Test fun cumulativeCostAsEffectsAreAddedOneByOne() {
        val icon = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.cum.e$i", name = "E$i", label = "E $i", iconCacheKey = "cum#$i")
                .also { RAppsSingleton.instance.setAppIcon(it.iconCacheKey, icon) }
        })
        val medians = LinkedHashMap<String, Long>()
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            var view: LensView? = null
            scenario.onActivity { activity ->
                view = LensView(activity).apply {
                    setApps(apps)
                    layoutParams = FrameLayout.LayoutParams(VIEW_SIZE_PX, VIEW_SIZE_PX)
                    activity.setContentView(this)
                    measure(View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY))
                    layout(0, 0, VIEW_SIZE_PX, VIEW_SIZE_PX)
                    draw(Canvas())
                }
            }
            val target = requireNotNull(view)
            val downTime = SystemClock.uptimeMillis()
            fun send(action: Int, x: Float, y: Float) = instrumentation.runOnMainSync {
                val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                target.dispatchTouchEvent(e); e.recycle()
            }
            // One real pan held still for the whole benchmark: the lens is in its PANNING state, the finger off any icon centre.
            send(MotionEvent.ACTION_DOWN, 300f, 700f)
            send(MotionEvent.ACTION_MOVE, 300f + PAN_START_DP * density, 700f - PAN_START_DP * density)
            send(MotionEvent.ACTION_MOVE, 300f + PAN_START_DP * density + 3f, 700f - PAN_START_DP * density - 3f)

            val us = UtilSettings(context)
            val enabled = ArrayList<String>()
            for ((name, keys) in stages) {
                keys.forEach { us.save(it, true); enabled.add(it) }
                // The effect flags are sampled when a lens is shown, so re-sample through a fresh gesture start.
                send(MotionEvent.ACTION_UP, 300f, 700f)
                send(MotionEvent.ACTION_DOWN, 300f, 700f)
                send(MotionEvent.ACTION_MOVE, 300f + PAN_START_DP * density, 700f - PAN_START_DP * density)
                send(MotionEvent.ACTION_MOVE, 300f + PAN_START_DP * density + 3f, 700f - PAN_START_DP * density - 3f)
                val canvas = Canvas(Bitmap.createBitmap(VIEW_SIZE_PX, VIEW_SIZE_PX, Bitmap.Config.ARGB_8888))
                instrumentation.runOnMainSync { repeat(WARMUP_DRAWS) { target.draw(canvas) } }
                val rounds = ArrayList<Long>()
                repeat(ROUNDS) {
                    var micros = 0L
                    instrumentation.runOnMainSync {
                        val start = SystemClock.elapsedRealtimeNanos()
                        repeat(DRAWS_PER_ROUND) { target.draw(canvas) }
                        micros = (SystemClock.elapsedRealtimeNanos() - start) / DRAWS_PER_ROUND / NANOS_PER_MICRO
                    }
                    rounds.add(micros)
                }
                medians[name] = rounds.sorted()[rounds.size / 2]
                Log.i(TAG, "$name: median ${medians[name]} us/draw rounds=$rounds enabledKeys=${enabled.size}")
            }
        }
        val base = medians.values.first()
        val total = medians.values.last()
        Log.i(TAG, "TOTAL/BASE = ${"%.2f".format(total.toDouble() / base)}")
        assertTrue(medians.size == stages.size)
        assertTrue("all effects $total us vs base $base us", total < base * MAX_TOTAL_TO_BASE_RATIO)
    }
}
