package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.search.QuickShortcut
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-031 Task 5: direct CPU drawing cost measured via nanoTime on a real LensView.
 * Runs 300 draw() passes without a ring, then 300 draw() passes with the ring active,
 * measuring the exact time spent inside the view's onDraw per frame.
 */
@RunWith(AndroidJUnit4::class)
class FocusRingPerformanceSmokeTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val shortcuts = listOf(
        QuickShortcut("1", "pkg", "Label 1", null),
        QuickShortcut("2", "pkg", "Label 2", null),
        QuickShortcut("3", "pkg", "Label 3", null)
    )

    @Test
    fun measureDirectDrawCostWithAndWithoutRing() {
        val inst = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            var lv: LensView? = null
            scenario.onActivity { a ->
                val v = LensView(a)
                v.setApps(ArrayList((0..35).map { App(id = it, label = "App $it", packageName = "pkg.$it", name = "Main") }))
                v.shortcutProviderForTest = { _, _ -> shortcuts }
                v.shortcutLauncherForTest = { _, _, _ -> true }
                a.setContentView(v, FrameLayout.LayoutParams(1080, 1920))
                lv = v
            }
            inst.waitForIdleSync()
            val view = requireNotNull(lv)
            val bmp = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // Warm up layout/cache
            inst.runOnMainSync {
                view.draw(canvas)
                val r = Rect()
                assertTrue(view.getAppBounds(5, r))
            }

            fun sample(active: Boolean, iterations: Int = 300): LongArray {
                val times = LongArray(iterations)
                inst.runOnMainSync {
                    // Hover icon 5
                    view.setLensStateForTest(540f, 960f, 1f, false)
                    if (active) {
                        // Raise ring via reflection test hook
                        val method = LensView::class.java.getDeclaredMethod("raiseFocusRing", Int::class.javaPrimitiveType, List::class.java)
                        method.isAccessible = true
                        method.invoke(view, 5, shortcuts)
                    } else {
                        val reset = LensView::class.java.getDeclaredMethod("resetFocusRing")
                        reset.isAccessible = true
                        reset.invoke(view)
                    }
                    // Prove the state really is what the label says, so the comparison cannot silently be ring-off twice.
                    assertTrue("ring state must match the sampled mode", view.isFocusRingActiveForTest() == active)
                    // Warm up JIT for this state
                    repeat(10) { view.draw(canvas) }
                    for (i in 0 until iterations) {
                        canvas.drawColor(Color.WHITE)
                        val t0 = System.nanoTime()
                        view.draw(canvas)
                        times[i] = System.nanoTime() - t0
                    }
                }
                return times
            }

            val offTimes = sample(active = false)
            val onTimes = sample(active = true)

            fun report(label: String, arr: LongArray) {
                arr.sort()
                val meanMs = arr.average() / 1_000_000.0
                val p50Ms = arr[(arr.size * 0.50).toInt()] / 1_000_000.0
                val p95Ms = arr[(arr.size * 0.95).toInt()] / 1_000_000.0
                val maxMs = arr.last() / 1_000_000.0
                println("FISH031_BENCH: $label (N=${arr.size}): mean=${"%.3f".format(meanMs)}ms p50=${"%.3f".format(p50Ms)}ms p95=${"%.3f".format(p95Ms)}ms max=${"%.3f".format(maxMs)}ms")
            }

            report("RING_OFF", offTimes)
            report("RING_ON", onTimes)

            // Pass condition: drawing the ring must not blow through the 16ms budget
            val p95OnMs = onTimes[(onTimes.size * 0.95).toInt()] / 1_000_000.0
            assertTrue("95th percentile draw time with ring ($p95OnMs ms) must be well below 16ms (1/60s frame budget)", p95OnMs < 16.0)
        }
    }
}
