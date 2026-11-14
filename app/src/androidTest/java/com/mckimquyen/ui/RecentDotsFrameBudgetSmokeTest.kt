package com.mckimquyen.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.InputDevice
import android.view.MotionEvent
import androidx.core.graphics.drawable.toBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import com.mckimquyen.views.LensView
import com.mckimquyen.views.RecentDots
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * FISH-048 frame budget on a real screen: a real drag (UiAutomation.injectInputEvent) over the real Home with real launcher
 * apps, identical with the switch off and on. Frame times come from FrameMetrics of the Home window. The drag keeps the finger
 * moving the whole time, so the lens is really panning; the test also proves that, with the switch on, recent-app dots were
 * drawn while measuring (positive control) and that none were drawn with it off.
 */
@RunWith(AndroidJUnit4::class)
class RecentDotsFrameBudgetSmokeTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private class Run(val frames: Int, val meanMs: Float, val p90Ms: Float, val p99Ms: Float, val maxMs: Float, val overBudget: Int, val labelsSeen: Int)

    @Before fun releaseAnyStaleFinger() {
        runCatching {
            val now = SystemClock.uptimeMillis()
            inject(MotionEvent.ACTION_UP, now, now, 1f, 1f)
        }
    }

    private fun inject(action: Int, downTime: Long, eventTime: Long, x: Float, y: Float) {
        val e = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        e.source = InputDevice.SOURCE_TOUCHSCREEN
        check(instrumentation.uiAutomation.injectInputEvent(e, true)) { "device rejected MotionEvent action=$action" }
        e.recycle()
    }

    private fun shot(name: String) {
        val bitmap: Bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "no screenshot for $name" }
        val dir = File(context.filesDir, SHOT_DIR).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    }

    /** Real installed launcher apps with their real icons, so the lens looks like the real Home. */
    private fun seedRealApps(): ArrayList<App> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seeded = ArrayList<App>()
        for (info in pm.queryIntentActivities(launcher, 0).take(SEED_APPS)) {
            val activity = info.activityInfo
            val key = "fish048#${activity.packageName}"
            RAppsSingleton.instance.setAppIcon(key, info.loadIcon(pm).toBitmap(SEED_ICON_PX, SEED_ICON_PX))
            seeded.add(App(id = seeded.size, packageName = activity.packageName, name = activity.name, label = info.loadLabel(pm).toString(), iconCacheKey = key, isVisible = true))
        }
        return seeded
    }

    private fun measure(switchOn: Boolean, screenshotName: String?): Run {
        UtilSettings(context).apply {
            save(UtilSettings.KEY_RECENT_DOTS, switchOn)
            save(UtilSettings.KEY_SHOW_NAME_APP_HOVER, true)
            save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
        }
        val durationsNs = ArrayList<Long>()
        val thread = HandlerThread("fish047-frames").apply { start() }
        var labelsSeen = 0
        var downTime = 0L
        var lastX = 0f
        var lastY = 0f
        var down = false
        var shotTaken = false
        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                var view: LensView? = null
                val deadline = System.currentTimeMillis() + WAIT_MS
                while (System.currentTimeMillis() < deadline && (view?.displayedApps?.size ?: 0) < MIN_APPS) {
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { view = it.findViewById(R.id.lensViews) }
                    SystemClock.sleep(POLL_MS)
                }
                val lens = requireNotNull(view) { "lens view not found" }
                assertTrue("need a real grid, had ${lens.displayedApps.size} apps", lens.displayedApps.size >= MIN_APPS)
                SystemClock.sleep(SETTLE_MS)
                val listener = Window_OnFrameMetrics { metrics -> synchronized(durationsNs) { durationsNs.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION)) } }
                scenario.onActivity { it.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
                val loc = IntArray(2)
                instrumentation.runOnMainSync { lens.getLocationOnScreen(loc) }
                val left = loc[0] + lens.width * SWEEP_LEFT
                val right = loc[0] + lens.width * SWEEP_RIGHT
                val top = loc[1] + lens.height * SWEEP_TOP
                val bottom = loc[1] + lens.height * SWEEP_BOTTOM
                downTime = SystemClock.uptimeMillis()
                lastX = left
                lastY = top
                inject(MotionEvent.ACTION_DOWN, downTime, downTime, lastX, lastY)
                down = true
                val start = SystemClock.uptimeMillis()
                var step = 0
                while (SystemClock.uptimeMillis() - start < RUN_MS) {
                    // Triangle wave along a diagonal band. A near-horizontal sweep is read by LensView as a page swipe
                    // (|dx| > 2 * |dy|, see isHorizontalSwipeIntent), hands the gesture to the pager and never pans the lens,
                    // so the path has to climb as fast as it travels sideways. The finger never rests.
                    val phase = ((SystemClock.uptimeMillis() - start) % SWEEP_PERIOD_MS) / SWEEP_PERIOD_MS.toFloat()
                    val t = if (phase < HALF) phase * 2f else (1f - phase) * 2f
                    lastX = left + (right - left) * t
                    lastY = top + (bottom - top) * t
                    inject(MotionEvent.ACTION_MOVE, downTime, SystemClock.uptimeMillis(), lastX, lastY)
                    step++
                    var labelsNow = 0
                    if (step % SAMPLE_EVERY == 0 || (screenshotName != null && !shotTaken)) {
                        instrumentation.runOnMainSync {
                            labelsNow = lens.recentDotsDrawnForTest
                            labelsSeen = maxOf(labelsSeen, labelsNow)
                        }
                    }
                    // Screenshot only in a frame whose labels are really drawn right now (switch on): the finger is often over
                    // the gap between icons, where nothing is selected, so a historical maximum would photograph an empty frame.
                    if (screenshotName != null && !shotTaken && step > SHOT_STEP && (!switchOn || labelsNow > 0)) {
                        shot(screenshotName)
                        shotTaken = true
                    }
                    SystemClock.sleep(STEP_DELAY_MS)
                }
                scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
                // CANCEL, not UP: releasing over an icon would launch that app.
                inject(MotionEvent.ACTION_CANCEL, downTime, SystemClock.uptimeMillis(), lastX, lastY)
                down = false
            }
        } finally {
            if (down) runCatching { inject(MotionEvent.ACTION_CANCEL, downTime, SystemClock.uptimeMillis(), lastX, lastY) }
            thread.quitSafely()
        }
        val ms = synchronized(durationsNs) { durationsNs.map { it / NS_PER_MS } }.sorted()
        val budgetMs = 1000f / (context.getSystemService(android.view.WindowManager::class.java).defaultDisplay.refreshRate)
        require(ms.isNotEmpty()) { "no frames were measured" }
        fun pct(p: Float) = ms[minOf(ms.size - 1, (ms.size * p).toInt())]
        return Run(ms.size, ms.average().toFloat(), pct(P90), pct(P99), ms.last(), ms.count { it > budgetMs }, labelsSeen)
    }

    // FrameMetrics.OnFrameMetricsAvailableListener is a single-method interface with a 3-arg callback; adapt it.
    private fun Window_OnFrameMetrics(block: (FrameMetrics) -> Unit) =
        android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ -> block(metrics) }

    @Test fun aRealDragWithTheSwitchOnDrawsNeighbourLabelsAndTheFrameCostIsRecorded() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        val original = RAppsSingleton.instance.apps
        val seeded = seedRealApps()
        RAppsSingleton.instance.apps = seeded
        // Three of the real seeded apps count as opened a moment ago; the original history is put back afterwards.
        val history = com.mckimquyen.search.SearchHistoryStore(context)
        val originalKeys = history.recentKeys()
        history.clear()
        seeded.drop(RECENT_FROM).take(RecentDots.MAX_DOTS).forEach { history.recordLaunch(com.mckimquyen.search.AppSearchEngine.componentKey(it)) }
        try {
            val off = ArrayList<Run>()
            val on = ArrayList<Run>()
            // The first run after a cold start is slow whichever setting it has (JIT, icon loads, first layout): run one, keep nothing.
            measure(false, null)
            for (pair in 1..PAIRS) {
                // Alternate who goes first: the first run after a cold start is slower whichever setting it has.
                if (pair % 2 == 1) {
                    off.add(measure(false, if (pair == 1) "labels-off" else null))
                    on.add(measure(true, if (pair == 1) "labels-on" else null))
                } else {
                    on.add(measure(true, null))
                    off.add(measure(false, null))
                }
            }
            fun line(name: String, r: List<Run>) = "$name frames=${r.sumOf { it.frames }} mean=${"%.2f".format(r.map { it.meanMs }.average())}ms " +
                "p90=${"%.2f".format(r.map { it.p90Ms }.average())}ms p99=${"%.2f".format(r.map { it.p99Ms }.average())}ms max=${"%.2f".format(r.maxOf { it.maxMs })}ms " +
                "overBudget=${r.sumOf { it.overBudget }} labelsSeen=${r.map { it.labelsSeen }}"
            Log.e(TAG, line("OFF", off))
            Log.e(TAG, line("ON ", on))
            off.forEachIndexed { i, r -> Log.e(TAG, "off-${i + 1}: frames=${r.frames} mean=${"%.2f".format(r.meanMs)} p90=${"%.2f".format(r.p90Ms)} p99=${"%.2f".format(r.p99Ms)} max=${"%.2f".format(r.maxMs)} over=${r.overBudget}") }
            on.forEachIndexed { i, r -> Log.e(TAG, "on-${i + 1}: frames=${r.frames} mean=${"%.2f".format(r.meanMs)} p90=${"%.2f".format(r.p90Ms)} p99=${"%.2f".format(r.p99Ms)} max=${"%.2f".format(r.maxMs)} over=${r.overBudget}") }
            assertTrue("positive control: recent-app dots were really drawn with the switch on, saw ${on.map { it.labelsSeen }}", on.all { it.labelsSeen > 0 })
            assertTrue("with the switch off none are drawn, saw ${off.map { it.labelsSeen }}", off.all { it.labelsSeen == 0 })
            assertTrue("enough frames to mean something, had ${on.map { it.frames }}", on.all { it.frames >= MIN_FRAMES })
        } finally {
            RAppsSingleton.instance.apps = original
            history.clear()
            originalKeys.asReversed().forEach { history.recordLaunch(it) }
        }
    }

    private companion object {
        const val TAG = "F048PERF"
        const val SHOT_DIR = "fish048"
        const val PNG_QUALITY = 100
        const val WAIT_MS = 10_000L
        const val POLL_MS = 100L
        const val SETTLE_MS = 700L
        const val RUN_MS = 6_000L
        const val PAIRS = 4
        const val STEP_DELAY_MS = 16L
        const val SWEEP_LEFT = 0.15f
        const val SWEEP_RIGHT = 0.85f
        const val SWEEP_TOP = 0.35f
        const val SWEEP_BOTTOM = 0.65f
        const val SWEEP_PERIOD_MS = 2_400L
        const val HALF = 0.5f
        const val SAMPLE_EVERY = 12
        const val SHOT_STEP = 60
        const val MIN_APPS = 12
        // 6 s of real drawing; a 60 Hz low-end phone (Samsung A50s) manages ~165 frames after warm-up, so 60 still means about a second of frames.
        const val MIN_FRAMES = 60
        const val SEED_APPS = 60
        // Apps near the middle of the grid, where a diagonal drag passes over them.
        const val RECENT_FROM = 20
        const val SEED_ICON_PX = 96
        const val NS_PER_MS = 1_000_000f
        const val P90 = 0.90f
        const val P99 = 0.99f
    }
}
