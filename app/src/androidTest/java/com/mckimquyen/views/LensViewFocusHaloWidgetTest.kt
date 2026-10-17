package com.mckimquyen.views

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-024: real LensView draw path. The halo draw counter is the production test hook (same
 * convention as badgeDrawCallCount). The touch is scanned across the view because the grid cell
 * position is a layout detail; the positive test proves the scan really lands on an icon.
 */
@RunWith(AndroidJUnit4::class)
class LensViewFocusHaloWidgetTest {

    private companion object {
        const val WIDTH_PX = 1080
        const val HEIGHT_PX = 1920
        const val SCAN_STEP_PX = 40
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var context: Context
    private lateinit var settings: UtilSettings
    private lateinit var lensView: LensView

    @Before
    fun setup() {
        val target = instrumentation.targetContext
        PreferenceManager.getDefaultSharedPreferences(target).edit().clear().commit()
        context = ContextThemeWrapper(target, R.style.AppTheme)
        settings = UtilSettings(context)
        instrumentation.runOnMainSync { lensView = LensView(context) }
        lensView.setApps(arrayListOf(App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")))
        lensView.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY)
        )
        lensView.layout(0, 0, WIDTH_PX, HEIGHT_PX)
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    /** Touches every scan point and draws a frame; true if the halo was drawn at least once. */
    private fun haloDrawnWhileHoveringAnywhere(): Boolean {
        val before = lensView.focusHaloDrawCallCount
        instrumentation.runOnMainSync {
            val downTime = SystemClock.uptimeMillis()
            for (y in 0 until HEIGHT_PX step SCAN_STEP_PX) {
                for (x in 0 until WIDTH_PX step SCAN_STEP_PX) {
                    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0)
                    lensView.dispatchTouchEvent(down)
                    down.recycle()
                    lensView.draw(Canvas())
                    val cancel = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_CANCEL, x.toFloat(), y.toFloat(), 0)
                    lensView.dispatchTouchEvent(cancel)
                    cancel.recycle()
                }
            }
        }
        return lensView.focusHaloDrawCallCount > before
    }

    @Test
    fun defaultSettings_haloIsDrawnForHoveredApp() {
        assertTrue("halo must draw by default (setting defaults to on)", haloDrawnWhileHoveringAnywhere())
    }

    @Test
    fun settingOff_haloIsNeverDrawn() {
        settings.save(UtilSettings.KEY_SHOW_FOCUS_HALO, false)
        val before = lensView.focusHaloDrawCallCount

        haloDrawnWhileHoveringAnywhere()

        assertEquals("disabled setting must suppress the halo", before, lensView.focusHaloDrawCallCount)
    }

    @Test
    fun cleanModeOn_haloIsNeverDrawn() {
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        val before = lensView.focusHaloDrawCallCount

        haloDrawnWhileHoveringAnywhere()

        assertEquals("clean mode must suppress the halo", before, lensView.focusHaloDrawCallCount)
    }
}
