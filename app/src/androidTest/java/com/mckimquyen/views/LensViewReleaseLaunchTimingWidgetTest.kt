package com.mckimquyen.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lifting the finger on an icon opens that app, but only once a frame has been drawn under the finger: the selected icon is refreshed by the draw
 * pass, so a touch released before the next frame has no selection and launches nothing. Measured on a TECNO KJ7 with a release build: a touch held 1 or 5 ms
 * did nothing, 10 ms and longer opened the app. This pins both sides of that, so a change to either is a decision and not an accident. The launch is observed
 * through the [LensView.appLauncherForTest] seam, not by starting a real activity.
 */
@RunWith(AndroidJUnit4::class)
class LensViewReleaseLaunchTimingWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val VIEW_W = 1080
        const val VIEW_H = 1920
        const val APP_PACKAGE = "com.test.release"
        const val APP_NAME = "Main"
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var lensView: LensView
    private val launched = ArrayList<String>()

    @Before fun setup() {
        val context: Context = ContextThemeWrapper(instrumentation.targetContext, R.style.AppTheme)
        instrumentation.runOnMainSync {
            lensView = LensView(context)
            lensView.setPackageManager(context.packageManager)
            lensView.appLauncherForTest = { pkg, _ -> launched.add(pkg) }
            lensView.setApps(arrayListOf(App(id = 1, label = "Release App", packageName = APP_PACKAGE, name = APP_NAME)))
            lensView.measure(View.MeasureSpec.makeMeasureSpec(VIEW_W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(VIEW_H, View.MeasureSpec.EXACTLY))
            lensView.layout(0, 0, VIEW_W, VIEW_H)
            lensView.draw(Canvas())
        }
    }

    private fun touch(action: Int, x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        lensView.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun iconCentre(): Pair<Float, Float> {
        var centre = 0f to 0f
        instrumentation.runOnMainSync {
            val bounds = Rect()
            assertTrue("positive control: the single icon has bounds", lensView.getAppBounds(0, bounds))
            centre = bounds.exactCenterX() to bounds.exactCenterY()
        }
        return centre
    }

    @Test fun liftingAfterAFrameWasDrawnUnderTheFingerOpensTheApp() {
        val (x, y) = iconCentre()
        instrumentation.runOnMainSync {
            touch(MotionEvent.ACTION_DOWN, x, y)
            lensView.draw(Canvas())
            touch(MotionEvent.ACTION_UP, x, y)
        }
        assertEquals("a frame was drawn while the finger was down, so lifting opens the app", listOf(APP_PACKAGE), launched)
    }

    @Test fun liftingBeforeAnyFrameWasDrawnLaunchesNothing() {
        val (x, y) = iconCentre()
        instrumentation.runOnMainSync {
            touch(MotionEvent.ACTION_DOWN, x, y)
            touch(MotionEvent.ACTION_UP, x, y)
        }
        assertTrue("released before the next frame: no icon was selected yet, so nothing launches (got $launched)", launched.isEmpty())
    }

    @Test fun theSamePressLaunchesOnceNotTwice() {
        val (x, y) = iconCentre()
        instrumentation.runOnMainSync {
            touch(MotionEvent.ACTION_DOWN, x, y)
            lensView.draw(Canvas())
            lensView.draw(Canvas())
            touch(MotionEvent.ACTION_UP, x, y)
        }
        assertEquals(1, launched.size)
    }
}
