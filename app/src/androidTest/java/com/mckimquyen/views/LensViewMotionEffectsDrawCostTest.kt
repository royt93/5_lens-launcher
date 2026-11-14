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
import com.mckimquyen.util.LensPhysicsPolicy
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.hypot

/**
 * Cost of the four MOTION effects (Snap, Inertia, Hover Magnify, cluster haptic) that [LensViewCumulativeEffectsDrawCostTest]
 * cannot reach: it holds the finger still, so none of them runs there. Here the finger really pans (dispatchTouchEvent, 1 dp per
 * 16 ms per axis, about 88 dp/s, under Snap's 120 dp/s gate) onto an icon and then rests on it past the Magnify dwell. Each
 * effect has its own positive control, otherwise its number is not evidence (FISH-045). Benchmark, not a gate: log tag F078MOT.
 * Opt-in (`-e motionBench true`, about 4 and 6 minutes) and skipped when the animator scale is 0 (reduced-motion), where the
 * lens does not run these effects at all. Deltas under about 2 ms are inside the run-to-run noise (see the drift control).
 */
@RunWith(AndroidJUnit4::class)
class LensViewMotionEffectsDrawCostTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val VIEW_SIZE_PX = 1_000
        const val APP_COUNT = 120
        const val ICON_PX = 48
        const val TARGET_CELL = 55
        const val PAN_STEPS = 120
        const val STEP_DP = 1f
        const val FRAME_MS = 16L
        const val ROUNDS = 5
        const val HOLD_EXTRA_FRAMES = 20
        const val NANOS_PER_MICRO = 1_000L
        const val TAG = "F078MOT"
        const val BENCH_ARG = "motionBench"
        const val MIN_GAP_PX = 1f
        const val JITTER_SPEED_FRACTION = 0.4f
        const val MS_PER_S = 1000f
        val BASE_KEYS = listOf(UtilSettings.KEY_DEPTH_OF_FIELD, UtilSettings.KEY_LENS_VIGNETTE, UtilSettings.KEY_FOCUS_PEAKING, UtilSettings.KEY_CHROMATIC_ABERRATION)
    }

    /** [baseKeys] are the always-on lens effects of this stage, [keys] the motion effects; every other key of either kind is off. */
    private class Stage(val name: String, val keys: List<String>, val baseKeys: List<String> = BASE_KEYS)

    // Isolated, not cumulative: with Snap on, the Inertia centre is already the snapped target, so a cumulative stage could not
    // tell lag from pull (both gaps read 36 px on the first A50s run). Each effect is measured alone, then all four together.
    private val stages = listOf(
        Stage("BASE (dof+vignette+peaking+chromatic)", emptyList()),
        Stage("ONLY Snap", listOf(UtilSettings.KEY_FOCUS_SNAP)),
        Stage("ONLY Inertia", listOf(UtilSettings.KEY_LENS_INERTIA)),
        Stage("ONLY Magnify", listOf(UtilSettings.KEY_FOCUS_MAGNIFY)),
        Stage("ONLY ClusterHaptic", listOf(UtilSettings.KEY_VIBRATE_APP_HOVER)),
        Stage("ALL four motion effects", listOf(UtilSettings.KEY_FOCUS_SNAP, UtilSettings.KEY_LENS_INERTIA, UtilSettings.KEY_FOCUS_MAGNIFY, UtilSettings.KEY_VIBRATE_APP_HOVER)),
    )
    private val motionKeys = stages.flatMap { it.keys }.distinct()

    // Attribution of the pan-cost step seen on A50s (47 ms with no Snap/Inertia, 19 ms with either): which base effect is it,
    // and is it order drift? The first stage is repeated last as a drift control.
    private val attributionStages = listOf(
        Stage("A all base, no motion", emptyList()),
        Stage("B all base + Snap", listOf(UtilSettings.KEY_FOCUS_SNAP)),
        Stage("C base without DoF", emptyList(), BASE_KEYS - UtilSettings.KEY_DEPTH_OF_FIELD),
        Stage("D base without vignette", emptyList(), BASE_KEYS - UtilSettings.KEY_LENS_VIGNETTE),
        Stage("E base without peaking", emptyList(), BASE_KEYS - UtilSettings.KEY_FOCUS_PEAKING),
        Stage("F base without chromatic", emptyList(), BASE_KEYS - UtilSettings.KEY_CHROMATIC_ABERRATION),
        Stage("G no base effects at all", emptyList(), emptyList()),
        Stage("H no base effects + Snap", listOf(UtilSettings.KEY_FOCUS_SNAP), emptyList()),
        Stage("A2 all base, no motion (drift control)", emptyList()),
    )

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val density = context.resources.displayMetrics.density

    @After fun tearDown() {
        val us = UtilSettings(context)
        (BASE_KEYS + motionKeys).distinct().forEach { us.save(it, false) }
        BitmapCache.clear()
    }

    private class Observed(val panMedianUs: Long, val holdMedianUs: Long, val snapGapPx: Float, val inertiaGapPx: Float, val magnifiedDrawn: Int, val haptics: Int)

    /** Skipped, not failed: the run takes minutes and is meaningless on a reduced-motion device (suites there must stay green). */
    private fun assumeBenchRunnable() {
        assumeTrue("opt-in benchmark, minutes long: pass `-e $BENCH_ARG true`", InstrumentationRegistry.getArguments().getString(BENCH_ARG) == "true")
        assumeFalse(
            "lens is in reduced-motion (animator scale 0, battery saver or thermal): none of these effects can run; set animator_duration_scale 1",
            LensPhysicsPolicy.shouldReduceLensMotion(context),
        )
    }

    @Test fun baseEffectAttributionOfThePanCost() {
        assumeBenchRunnable()
        val r = runStages(attributionStages)
        val a = r.getValue("A all base, no motion").panMedianUs.toDouble()
        val a2 = r.getValue("A2 all base, no motion (drift control)").panMedianUs.toDouble()
        Log.i(TAG, "DRIFT A2/A = ${"%.2f".format(a2 / a)}")
        assertTrue("snap control in B, gap=${r.getValue("B all base + Snap").snapGapPx}px", r.getValue("B all base + Snap").snapGapPx > MIN_GAP_PX)
        assertTrue("snap control in H, gap=${r.getValue("H no base effects + Snap").snapGapPx}px", r.getValue("H no base effects + Snap").snapGapPx > MIN_GAP_PX)
        assertTrue("A must have no snap gap, saw ${r.getValue("A all base, no motion").snapGapPx}px", r.getValue("A all base, no motion").snapGapPx <= MIN_GAP_PX)
    }

    @Test fun motionEffectsCostWithRealPanAndRest() {
        assumeBenchRunnable()
        val results = runStages(stages)
        val base = results.values.first()
        val total = results.values.last()
        Log.i(TAG, "PAN TOTAL/BASE = ${"%.2f".format(total.panMedianUs.toDouble() / base.panMedianUs)} HOLD TOTAL/BASE = ${"%.2f".format(total.holdMedianUs.toDouble() / base.holdMedianUs)}")
        // Positive controls come last so every number above is logged even when one fails.
        assertTrue("base must not already show a snap gap, saw ${base.snapGapPx}px", base.snapGapPx <= MIN_GAP_PX)
        assertTrue("snap control: optical centre must leave the finger, gap=${results.getValue("ONLY Snap").snapGapPx}px", results.getValue("ONLY Snap").snapGapPx > MIN_GAP_PX)
        assertTrue("inertia control: visual centre must lag the finger, gap=${results.getValue("ONLY Inertia").inertiaGapPx}px", results.getValue("ONLY Inertia").inertiaGapPx > MIN_GAP_PX)
        assertTrue("magnify control: an icon must be drawn magnified, saw ${results.getValue("ONLY Magnify").magnifiedDrawn}", results.getValue("ONLY Magnify").magnifiedDrawn > 0)
        assertTrue("haptic control: the cluster haptic must fire during the pan, saw ${results.getValue("ONLY ClusterHaptic").haptics}", results.getValue("ONLY ClusterHaptic").haptics > 0)
        assertTrue("all four together: every control must still hold, saw $total", total.snapGapPx > MIN_GAP_PX && total.inertiaGapPx > MIN_GAP_PX && total.magnifiedDrawn > 0 && total.haptics > 0)
        // Cross-checks that the stages really are isolated: a stage must not show another effect's signature.
        assertTrue("magnify must not draw with only Snap on, saw ${results.getValue("ONLY Snap").magnifiedDrawn}", results.getValue("ONLY Snap").magnifiedDrawn == 0)
        assertTrue("haptic must not fire with only Inertia on, saw ${results.getValue("ONLY Inertia").haptics}", results.getValue("ONLY Inertia").haptics == 0)
    }

    private fun runStages(stages: List<Stage>): LinkedHashMap<String, Observed> {
        val icon = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.mot.e$i", name = "E$i", label = "E $i", iconCacheKey = "mot#$i")
                .also { RAppsSingleton.instance.setAppIcon(it.iconCacheKey, icon) }
        })
        val us = UtilSettings(context)
        val results = LinkedHashMap<String, Observed>()
        val haptics = ArrayList<Int>()
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
                    onHapticPerformed = { haptics.add(it) }
                }
            }
            val target = requireNotNull(view)
            val canvas = Canvas(Bitmap.createBitmap(VIEW_SIZE_PX, VIEW_SIZE_PX, Bitmap.Config.ARGB_8888))
            val cell = target.baseRectsForTest()[TARGET_CELL]
            val stepPx = STEP_DP * density
            val jitterPx = HoverMagnify.STILL_SPEED_DP_PER_S * JITTER_SPEED_FRACTION * density * (FRAME_MS / MS_PER_S) / 2f
            for (stage in stages) {
                BASE_KEYS.forEach { us.save(it, it in stage.baseKeys) }
                motionKeys.forEach { us.save(it, it in stage.keys) }
                val pan = ArrayList<Long>()
                val hold = ArrayList<Long>()
                var snapGap = 0f
                var inertiaGap = 0f
                var magnified = 0
                haptics.clear()
                // One discarded round per stage: the first gesture after a settings change pays JIT and first-use costs (the first A50s run read BASE 44 ms against 18 ms for later stages).
                repeat(ROUNDS + 1) { round ->
                    val discard = round == 0
                    val downTime = SystemClock.uptimeMillis()
                    fun send(action: Int, x: Float, y: Float) = instrumentation.runOnMainSync {
                        val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                        target.dispatchTouchEvent(e); e.recycle()
                    }
                    fun timedDraw(into: MutableList<Long>) = instrumentation.runOnMainSync {
                        val start = SystemClock.elapsedRealtimeNanos()
                        target.draw(canvas)
                        if (!discard) into.add((SystemClock.elapsedRealtimeNanos() - start) / NANOS_PER_MICRO)
                    }
                    // Effect flags are sampled when a gesture starts, so every round is a fresh gesture. Diagonal on purpose:
                    // a near-horizontal drag reads as a page swipe and never pans the lens.
                    val sx = cell.centerX() - PAN_STEPS * stepPx
                    val sy = cell.centerY() + PAN_STEPS * stepPx
                    send(MotionEvent.ACTION_DOWN, sx, sy)
                    for (i in 1..PAN_STEPS) {
                        send(MotionEvent.ACTION_MOVE, sx + i * stepPx, sy - i * stepPx)
                        timedDraw(pan)
                        instrumentation.runOnMainSync {
                            val t = target.touchCoordinatesForTest()
                            val o = target.opticalCentreForTest()
                            val v = target.visualCentreForTest()
                            // Idle centres are -Float.MAX_VALUE sentinels: a gap against them is meaningless (and infinite).
                            if (t.first >= 0f && o.first >= 0f) snapGap = maxOf(snapGap, hypot(o.first - t.first, o.second - t.second))
                            if (t.first >= 0f && v.first >= 0f) inertiaGap = maxOf(inertiaGap, hypot(v.first - t.first, v.second - t.second))
                        }
                        SystemClock.sleep(FRAME_MS)
                    }
                    // Rest on the icon past the Magnify dwell and ramp; ~1 px of jitter keeps it a hold, not a lift.
                    val holdUntil = SystemClock.uptimeMillis() + HoverMagnify.DWELL_MS + HoverMagnify.RAMP_MS + HOLD_EXTRA_FRAMES * FRAME_MS
                    var k = 0
                    while (SystemClock.uptimeMillis() < holdUntil) {
                        send(MotionEvent.ACTION_MOVE, cell.centerX() + if (k++ % 2 == 0) jitterPx else -jitterPx, cell.centerY())
                        timedDraw(hold)
                        instrumentation.runOnMainSync { magnified = maxOf(magnified, target.magnifiedIconDrawnForTest) }
                        SystemClock.sleep(FRAME_MS)
                    }
                    // CANCEL, not UP: releasing over an icon would launch that app.
                    send(MotionEvent.ACTION_CANCEL, cell.centerX(), cell.centerY())
                }
                val o = Observed(pan.sorted()[pan.size / 2], hold.sorted()[hold.size / 2], snapGap, inertiaGap, magnified, haptics.size)
                results[stage.name] = o
                Log.i(TAG, "${stage.name}: panMedian=${o.panMedianUs}us holdMedian=${o.holdMedianUs}us snapGap=${"%.1f".format(o.snapGapPx)}px " +
                    "inertiaGap=${"%.1f".format(o.inertiaGapPx)}px magnifiedDrawn=${o.magnifiedDrawn} haptics=${o.haptics} motionKeysOn=${stage.keys.size} baseKeysOn=${stage.baseKeys.size}")
            }
        }
        return results
    }
}
