package com.mckimquyen.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.graphics.drawable.toBitmap
import androidx.core.widget.NestedScrollView
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import com.mckimquyen.views.LensView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * FISH-039 evidence smoke: real touch input (UiAutomation.injectInputEvent) on the real screens, with a screenshot of
 * each state saved under the app's filesDir/fish039 (pull with `run-as ... cat`). No production code is exercised that
 * the unit/widget tests do not already cover; this adds the two pieces of visual evidence adb alone could not produce:
 * the Settings switch toggled by a real touch, and the ring on a focus pinned by a real double tap.
 */
@RunWith(AndroidJUnit4::class)
class FocusPeakingScreenshotSmokeTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun shot(name: String) {
        val bitmap: Bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "no screenshot for $name" }
        val dir = File(context.filesDir, SHOT_DIR).apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    }

    private fun inject(action: Int, downTime: Long, eventTime: Long, x: Float, y: Float) {
        val e = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        e.source = InputDevice.SOURCE_TOUCHSCREEN
        check(instrumentation.uiAutomation.injectInputEvent(e, true)) { "device rejected MotionEvent action=$action" }
        e.recycle()
    }

    /** A held touch (a frame between DOWN and UP, like a finger). */
    private fun tapAt(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, down, down, x, y)
        SystemClock.sleep(TAP_HOLD_MS)
        inject(MotionEvent.ACTION_UP, down, SystemClock.uptimeMillis(), x, y)
    }

    private fun centreOnScreen(view: View): Pair<Float, Float> {
        val loc = IntArray(2)
        instrumentation.runOnMainSync { view.getLocationOnScreen(loc) }
        return (loc[0] + view.width / 2f) to (loc[1] + view.height / 2f)
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            var ok = false
            instrumentation.runOnMainSync { ok = condition() }
            if (ok) return
            SystemClock.sleep(POLL_MS)
        }
        assertTrue("timed out: $what", false)
    }

    @Test fun theSettingsSwitchIsTurnedOnByARealTouchAndPersists() {
        UtilSettings(context).save(UtilSettings.KEY_FOCUS_PEAKING, false)
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            var sw: SwitchCompat? = null
            scenario.onFragment { fragment ->
                val root = fragment.requireView() as NestedScrollView
                sw = root.findViewById(R.id.swFocusPeaking)
                val target = requireNotNull(sw) { "swFocusPeaking not found" }
                // Bring the switch to the middle of the screen: at the bottom it sits under the navigation bar.
                val onScreen = IntArray(2)
                target.getLocationOnScreen(onScreen)
                val rootOnScreen = IntArray(2)
                root.getLocationOnScreen(rootOnScreen)
                val wanted = rootOnScreen[1] + root.height / 2
                root.scrollTo(0, root.scrollY + (onScreen[1] - wanted))
            }
            val switchView = requireNotNull(sw) { "swFocusPeaking not found" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(SETTLE_MS)
            assertFalse("starts off", switchView.isChecked)
            shot("settings-1-off")

            val (x, y) = centreOnScreen(switchView)
            tapAt(x, y)
            waitUntil("the real touch turned the switch on") { switchView.isChecked }
            assertTrue("persisted by the real touch", UtilSettings(context).getBoolean(UtilSettings.KEY_FOCUS_PEAKING))
            SystemClock.sleep(SETTLE_MS)
            shot("settings-2-on")

            tapAt(x, y)
            waitUntil("the second touch turned it off") { !switchView.isChecked }
            assertFalse(UtilSettings(context).getBoolean(UtilSettings.KEY_FOCUS_PEAKING))
        }
    }

    /** Scans the view for a point that no icon cell covers, nearest to the centre. */
    private fun emptySpot(view: LensView): Pair<Float, Float> {
        val rects = ArrayList<Rect>()
        instrumentation.runOnMainSync {
            var i = 0
            while (true) {
                val r = Rect()
                if (!view.getAppBounds(i, r)) break
                rects.add(r)
                i++
            }
        }
        var best: Pair<Float, Float>? = null
        var bestDist = Float.MAX_VALUE
        val cx = view.width / 2f
        val cy = view.height / 2f
        var y = SCAN_STEP_PX
        while (y < view.height - SCAN_STEP_PX) {
            var x = SCAN_STEP_PX
            while (x < view.width - SCAN_STEP_PX) {
                if (rects.none { it.contains(x, y) }) {
                    val d = (x - cx) * (x - cx) + (y - cy) * (y - cy)
                    if (d < bestDist) { bestDist = d.toFloat(); best = x.toFloat() to y.toFloat() }
                }
                x += SCAN_STEP_PX
            }
            y += SCAN_STEP_PX
        }
        return requireNotNull(best) { "no empty space on the grid to double-tap" }
    }

    /** Real installed launcher apps with their real icons, so the screenshot looks like the real Home. */
    private fun seedRealApps(): ArrayList<App> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seeded = ArrayList<App>()
        for (info in pm.queryIntentActivities(launcher, 0).take(SEED_APPS)) {
            val activity = info.activityInfo
            val key = "fish039#${activity.packageName}"
            val icon = info.loadIcon(pm).toBitmap(SEED_ICON_PX, SEED_ICON_PX)
            RAppsSingleton.instance.setAppIcon(key, icon)
            seeded.add(
                App(
                    id = seeded.size, packageName = activity.packageName, name = activity.name,
                    label = info.loadLabel(pm).toString(), iconCacheKey = key, isVisible = true
                )
            )
        }
        return seeded
    }

    @Test fun aRealDoubleTapPinsFocusAndTheClusterIsOutlinedThenReleased() {
        UtilSettings(context).save(UtilSettings.KEY_FOCUS_PEAKING, true)
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        val original = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = seedRealApps()
        try {
            pinAndReleaseOnHome()
        } finally {
            RAppsSingleton.instance.apps = original
        }
    }

    private fun pinAndReleaseOnHome() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var found: LensView? = null
            val deadline = System.currentTimeMillis() + WAIT_MS
            while (System.currentTimeMillis() < deadline && (found == null || found?.displayedApps?.size ?: 0 < MIN_APPS)) {
                instrumentation.waitForIdleSync()
                scenario.onActivity { found = it.findViewById(R.id.lensViews) }
                SystemClock.sleep(POLL_MS)
            }
            val view = requireNotNull(found) { "lens view not found" }
            assertTrue("need a real grid, had ${view.displayedApps.size} apps", view.displayedApps.size >= MIN_APPS)
            SystemClock.sleep(SETTLE_MS)
            shot("home-1-idle")

            val (sx, sy) = emptySpot(view)
            val loc = IntArray(2)
            instrumentation.runOnMainSync { view.getLocationOnScreen(loc) }
            val screenX = loc[0] + sx
            val screenY = loc[1] + sy
            tapAt(screenX, screenY)
            SystemClock.sleep(DOUBLE_TAP_GAP_MS)
            tapAt(screenX, screenY)
            waitUntil("focus pinned by the real double tap") { view.isFocusPinnedForTest() }
            waitUntil("the pinned cluster is outlined") { view.focusPeakingRingsForTest >= 1 }
            assertEquals("a pin has no animation, so the ring is fully opaque", 255, view.focusPeakingAlphaForTest)
            assertTrue("only the sharp cluster, not the whole grid", view.focusPeakingRingsForTest < view.displayedApps.size)
            SystemClock.sleep(SETTLE_MS)
            shot("home-2-pinned")

            tapAt(screenX, screenY)
            SystemClock.sleep(DOUBLE_TAP_GAP_MS)
            tapAt(screenX, screenY)
            waitUntil("focus released by the second real double tap") { !view.isFocusPinnedForTest() }
            waitUntil("the ring is gone") { view.focusPeakingRingsForTest == 0 }
            SystemClock.sleep(SETTLE_MS)
            shot("home-3-released")
            assertNotNull(view.displayedApps)
        }
    }

    private companion object {
        const val SHOT_DIR = "fish039"
        const val PNG_QUALITY = 100
        const val WAIT_MS = 10_000L
        const val POLL_MS = 100L
        const val SETTLE_MS = 700L
        const val TAP_HOLD_MS = 90L
        const val DOUBLE_TAP_GAP_MS = 80L
        const val SCAN_STEP_PX = 12
        const val MIN_APPS = 12
        const val SEED_APPS = 60
        const val SEED_ICON_PX = 96
    }
}
