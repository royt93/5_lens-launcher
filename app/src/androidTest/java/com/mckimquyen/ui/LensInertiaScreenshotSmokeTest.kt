package com.mckimquyen.ui

import android.content.Intent
import android.graphics.Bitmap
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
import com.mckimquyen.views.LensInertia
import com.mckimquyen.views.LensView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * FISH-041 evidence smoke: real touch input (UiAutomation.injectInputEvent) on the real screens, with a screenshot of
 * each state saved under the app's filesDir/fish041 (pull with `run-as ... cat`). No production code is exercised that
 * the unit/widget tests do not already cover; this adds the two pieces of visual evidence adb alone could not produce:
 * the Settings switch toggled by a real touch, and the lagging lens centre during a real drag.
 */
@RunWith(AndroidJUnit4::class)
class LensInertiaScreenshotSmokeTest {
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

    @Before fun releaseAnyStaleFinger() {
        runCatching { releasePointer(1f, 1f) }
    }

    private fun releasePointer(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_UP, now, now, x, y)
    }

    @Test fun theSettingsSwitchIsTurnedOnByARealTouchAndPersists() {
        UtilSettings(context).save(UtilSettings.KEY_LENS_INERTIA, false)
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            var sw: SwitchCompat? = null
            scenario.onFragment { fragment ->
                val root = fragment.requireView() as NestedScrollView
                sw = root.findViewById(R.id.swLensInertia)
                val target = requireNotNull(sw) { "swLensInertia not found" }
                // Bring the switch to the middle of the screen: at the bottom it sits under the navigation bar.
                val onScreen = IntArray(2)
                target.getLocationOnScreen(onScreen)
                val rootOnScreen = IntArray(2)
                root.getLocationOnScreen(rootOnScreen)
                val wanted = rootOnScreen[1] + root.height / 2
                root.scrollTo(0, root.scrollY + (onScreen[1] - wanted))
            }
            val switchView = requireNotNull(sw) { "swLensInertia not found" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(SETTLE_MS)
            assertFalse("starts off", switchView.isChecked)
            shot("settings-1-off")

            val (x, y) = centreOnScreen(switchView)
            tapAt(x, y)
            waitUntil("the real touch turned the switch on") { switchView.isChecked }
            assertTrue("persisted by the real touch", UtilSettings(context).getBoolean(UtilSettings.KEY_LENS_INERTIA))
            SystemClock.sleep(SETTLE_MS)
            shot("settings-2-on")

            tapAt(x, y)
            waitUntil("the second touch turned it off") { !switchView.isChecked }
            assertFalse(UtilSettings(context).getBoolean(UtilSettings.KEY_LENS_INERTIA))
        }
    }

    /** Real installed launcher apps with their real icons, so the screenshot looks like the real Home. */
    private fun seedRealApps(): ArrayList<App> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seeded = ArrayList<App>()
        for (info in pm.queryIntentActivities(launcher, 0).take(SEED_APPS)) {
            val activity = info.activityInfo
            val key = "fish041#${activity.packageName}"
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

    private fun awaitGrid(scenario: ActivityScenario<ActHome>): LensView {
        var found: LensView? = null
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < deadline && (found == null || found?.displayedApps?.size ?: 0 < MIN_APPS)) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { found = it.findViewById(R.id.lensViews) }
            SystemClock.sleep(POLL_MS)
        }
        val view = requireNotNull(found) { "lens view not found" }
        assertTrue("need a real grid, had ${view.displayedApps.size} apps", view.displayedApps.size >= MIN_APPS)
        return view
    }

    @Test fun aRealDragLagsTheVisualCentreBehindTheFingerThenSettlesAndReleaseReturnsToIdle() {
        UtilSettings(context).save(UtilSettings.KEY_LENS_INERTIA, true)
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        val original = RAppsSingleton.instance.apps
        RAppsSingleton.instance.apps = seedRealApps()
        var downTime = 0L
        var lastX = 0f
        var lastY = 0f
        var down = false
        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                val view = awaitGrid(scenario)
                SystemClock.sleep(SETTLE_MS)
                shot("home-1-idle")
                val loc = IntArray(2)
                instrumentation.runOnMainSync { view.getLocationOnScreen(loc) }
                val startX = loc[0] + view.width * START_X_FRACTION
                val startY = loc[1] + view.height * START_Y_FRACTION
                val endX = loc[0] + view.width * END_FRACTION
                val endY = loc[1] + view.height * END_FRACTION
                downTime = SystemClock.uptimeMillis()
                lastX = startX
                lastY = startY
                inject(MotionEvent.ACTION_DOWN, downTime, downTime, startX, startY)
                down = true
                var maxGap = 0f
                for (i in 1..DRAG_STEPS) {
                    val t = i / DRAG_STEPS.toFloat()
                    lastX = startX + (endX - startX) * t
                    lastY = startY + (endY - startY) * t
                    inject(MotionEvent.ACTION_MOVE, downTime, SystemClock.uptimeMillis(), lastX, lastY)
                    var gap = 0f
                    instrumentation.runOnMainSync {
                        val v = view.visualCentreForTest()
                        val f = view.touchCoordinatesForTest()
                        if (v.first > 0f && f.first > 0f) gap = kotlin.math.hypot(f.first - v.first, f.second - v.second)
                    }
                    if (gap > maxGap) maxGap = gap
                    SystemClock.sleep(STEP_DELAY_MS)
                }
                assertTrue("the visual centre lagged the finger by at least a few px during the drag, was $maxGap", maxGap > LAG_MIN_PX)
                shot("home-2-drag")
                waitUntil("the visual centre settles on the held finger") {
                    val v = view.visualCentreForTest()
                    val f = view.touchCoordinatesForTest()
                    LensInertia.isSettled(v.first, v.second, f.first, f.second)
                }
                inject(MotionEvent.ACTION_UP, downTime, SystemClock.uptimeMillis(), lastX, lastY)
                down = false
                waitUntil("the touch clears after release") { view.touchCoordinatesForTest().first == -Float.MAX_VALUE }
                SystemClock.sleep(SETTLE_MS)
                shot("home-3-released")
            }
        } finally {
            if (down) runCatching { releasePointer(lastX, lastY) }
            RAppsSingleton.instance.apps = original
            UtilSettings(context).save(UtilSettings.KEY_LENS_INERTIA, false)
        }
    }

    private companion object {
        const val SHOT_DIR = "fish041"
        const val PNG_QUALITY = 100
        const val WAIT_MS = 10_000L
        const val POLL_MS = 100L
        const val SETTLE_MS = 700L
        const val TAP_HOLD_MS = 90L
        const val DRAG_STEPS = 28
        const val STEP_DELAY_MS = 16L
        const val START_X_FRACTION = 0.2f
        const val START_Y_FRACTION = 0.25f
        const val END_FRACTION = 0.5f
        const val MIN_APPS = 12
        const val LAG_MIN_PX = 4f
        const val SEED_APPS = 60
        const val SEED_ICON_PX = 96
    }
}
