package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RApplication
import com.mckimquyen.ui.ActHome
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class LensViewFocusHaloLiveVisualSmokeTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        // Extra pixels past the touch slop, so the move is unambiguously a pan yet stays on the icon.
        const val MOVE_MARGIN_PX = 2
    }


    @Test
    fun captureLiveHoverWithHalo() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // This smoke test needs the REAL installed apps in the live lens. RApplication switches the unsolicited startup
        // scan off under instrumentation (so it cannot evict icons other tests seed), so this test asks for one scan itself.
        val application = instrumentation.targetContext.applicationContext as RApplication
        application.appRefreshPipeline.execute()
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            var bitmap: Bitmap? = null
            val deadline = SystemClock.uptimeMillis() + 5_000L
            var hasApps = false
            while (SystemClock.uptimeMillis() < deadline && !hasApps) {
                scenario.onActivity { activity ->
                    hasApps = activity.findViewById<LensView>(R.id.lensViews)?.displayedApps?.isNotEmpty() == true
                }
                if (!hasApps) SystemClock.sleep(50L)
            }
            assertTrue("Installed apps must load into the live lens", hasApps)

            scenario.onActivity { activity ->
                val lensView = activity.findViewById<LensView>(R.id.lensViews)
                lensView.draw(Canvas(Bitmap.createBitmap(lensView.width, lensView.height, Bitmap.Config.ARGB_8888)))
                val bounds = Rect()
                val ok = lensView.getAppBounds(0, bounds)
                assertTrue("First app must have grid bounds", ok && bounds.width() > 0)

                val cx = bounds.exactCenterX()
                val cy = bounds.exactCenterY()
                val downTime = SystemClock.uptimeMillis()

                val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, cx, cy, 0)
                lensView.dispatchTouchEvent(down)
                down.recycle()

                // Move just past touch slop while staying inside the icon. A fixed 25px diagonal
                // move exits the default 36px icon from its centre on TECNO BG6, so hover clears.
                val touchSlop = android.view.ViewConfiguration.get(lensView.context).scaledTouchSlop
                val moveDelta = (touchSlop / kotlin.math.sqrt(2f)).toInt() + MOVE_MARGIN_PX
                assertTrue("move must cross touch slop but stay inside icon", moveDelta * 2 < minOf(bounds.width(), bounds.height()))
                val move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, cx + moveDelta, cy + moveDelta, 0)
                lensView.dispatchTouchEvent(move)
                move.recycle()

                val bmp = Bitmap.createBitmap(lensView.width.coerceAtLeast(100), lensView.height.coerceAtLeast(100), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)
                lensView.draw(canvas)
                assertTrue("Live hover path must call the halo drawing path", lensView.focusHaloDrawCallCount > 0)
                bitmap = bmp

                val cancel = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_CANCEL, cx + 25f, cy + 25f, 0)
                lensView.dispatchTouchEvent(cancel)
                cancel.recycle()
            }

            instrumentation.waitForIdleSync()
            bitmap?.let { b ->
                val outFile = java.io.File(instrumentation.targetContext.cacheDir, "fish024_live_halo_verified.png")
                val out = FileOutputStream(outFile)
                b.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.close()
            }
        }
    }
}
