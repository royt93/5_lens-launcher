package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActHome
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensViewChromaticAberrationIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SIZE_PX = 1_000
        const val APP_COUNT = 40
        const val ICON_PX = 48
        const val DRAG_STEPS = 12
        const val STEP_DELAY_MS = 16L
        const val SETTLE_TIMEOUT_MS = 3_000L
        const val POLL_MS = 50L
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    /**
     * Every app needs a cached icon bitmap: the fringe pass skips an icon without one, which would make every
     * "draws no fringe" assertion below pass for the wrong reason.
     */
    private fun withView(block: (LensView) -> Unit) {
        val icon = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish040.int$i", name = "Int$i", label = "Int $i", iconCacheKey = "fish040int#$i")
        })
        apps.forEach { RAppsSingleton.instance.setAppIcon(it.iconCacheKey, icon) }
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            var view: LensView? = null
            scenario.onActivity { activity ->
                view = LensView(activity).apply {
                    setApps(apps)
                    layoutParams = FrameLayout.LayoutParams(SIZE_PX, SIZE_PX)
                    activity.setContentView(this)
                    measure(
                        View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY)
                    )
                    layout(0, 0, SIZE_PX, SIZE_PX)
                    draw(Canvas())
                }
            }
            instrumentation.waitForIdleSync()
            try {
                block(requireNotNull(view) { "assigned in onActivity above" })
            } finally {
                com.mckimquyen.util.BitmapCache.clear()
            }
        }
    }

    private fun draw(view: LensView) = instrumentation.runOnMainSync { view.draw(Canvas()) }
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun cellCentre(view: LensView, index: Int): Pair<Float, Float> {
        val bounds = Rect()
        var found = false
        onMain { found = view.getAppBounds(index, bounds) }
        assertTrue("cell $index must exist", found)
        return bounds.exactCenterX() to bounds.exactCenterY()
    }

    @Test fun aPinnedFocusWithoutATouchDrawsNoFringe() = withView { view ->
        onMain { view.chromaticAberrationEnabled = true }
        val (x, y) = cellCentre(view, APP_COUNT / 2)
        onMain { assertTrue(view.toggleFocusLockAtForTest(x, y)) }
        draw(view)
        assertEquals("the effect is touch-only, a pin has no transition", 0, view.chromaIconsForTest)
    }

    @Test fun tiltNeverFringesAnything() = withView { view ->
        onMain { view.chromaticAberrationEnabled = true; view.setTiltOffset(120f, 80f) }
        draw(view)
        assertEquals(0, view.chromaIconsForTest)
    }

    @Test fun aLiveTouchOnTheAttachedViewFringesFarIconsAndReleaseClearsThem() = withView { view ->
        onMain { view.chromaticAberrationEnabled = true }
        val (x, y) = cellCentre(view, 0)
        onMain { view.setLensStateForTest(x, y, 1f, reduceMotion = false) }
        draw(view)
        assertTrue(view.chromaIconsForTest in 1..ChromaticAberration.MAX_ICONS)
        onMain { view.setLensStateForTest(-Float.MAX_VALUE, -Float.MAX_VALUE, 0f, reduceMotion = false) }
        draw(view)
        assertEquals(0, view.chromaIconsForTest)
    }

    @Test fun theSettingIsReadWhenTheLensAnimationStartsNotEveryFrame() = withView { view ->
        UtilSettings(context).save(UtilSettings.KEY_CHROMATIC_ABERRATION, true)
        assertTrue("the view has not seen the stored setting yet", !view.chromaticAberrationEnabled)
        val down = SystemClock.uptimeMillis()
        val (x, y) = cellCentre(view, 0)
        onMain {
            val e = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0)
            view.dispatchTouchEvent(e); e.recycle()
            val m = MotionEvent.obtain(down, down + 8, MotionEvent.ACTION_MOVE, x + 40f, y + 40f, 0)
            view.dispatchTouchEvent(m); m.recycle()
        }
        assertTrue("starting the lens animation samples the stored setting", view.chromaticAberrationEnabled)
        onMain {
            val c = MotionEvent.obtain(down, down + 16, MotionEvent.ACTION_CANCEL, x, y, 0)
            view.dispatchTouchEvent(c); c.recycle()
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    @Test fun onTheRealHomeWithTheHardwareBlurPathFringesStillDrawForTheFarIcons() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        RAppsSingleton.instance.apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish040.home$i", name = "Home$i", label = "Home $i")
        })
        UtilSettings(context).save(UtilSettings.KEY_DEPTH_OF_FIELD, true)
        UtilSettings(context).save(UtilSettings.KEY_CHROMATIC_ABERRATION, true)
        try {
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                var found: LensView? = null
                scenario.onActivity { found = it.findViewById(R.id.lensViews) }
                val view = requireNotNull(found) { "assigned in onActivity above" }
                instrumentation.waitForIdleSync()
                var w = 0f
                var h = 0f
                onMain { w = view.width.toFloat(); h = view.height.toFloat() }
                val down = SystemClock.uptimeMillis()
                fun send(action: Int, x: Float, y: Float) = onMain {
                    val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                    view.dispatchTouchEvent(e); e.recycle()
                }
                send(MotionEvent.ACTION_DOWN, w / 4, h / 4)
                for (i in 1..DRAG_STEPS) {
                    send(MotionEvent.ACTION_MOVE, w / 4 + w / 2 * i / DRAG_STEPS, h / 4 + h / 2 * i / DRAG_STEPS)
                    SystemClock.sleep(STEP_DELAY_MS)
                }
                val deadline = SystemClock.uptimeMillis() + SETTLE_TIMEOUT_MS
                var ok = false
                while (SystemClock.uptimeMillis() < deadline && !ok) {
                    onMain { ok = view.dofLastMode == DepthOfField.Mode.BLUR && view.chromaIconsForTest >= 1 }
                    if (!ok) SystemClock.sleep(POLL_MS)
                }
                assertTrue("BLUR path active and far icons are still fringed", ok)
                send(MotionEvent.ACTION_CANCEL, w * 3 / 4, h * 3 / 4)
            }
        } finally {
            prefs.edit().clear().commit()
            RAppsSingleton.instance.clearAllData()
        }
    }
}
