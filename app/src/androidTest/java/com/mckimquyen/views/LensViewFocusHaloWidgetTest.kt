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
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()


    private companion object {
        const val WIDTH_PX = 1080
        const val HEIGHT_PX = 1920
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

    /**
     * Touches the centre of the one laid-out icon and draws a frame; true if the halo was drawn.
     * The old version scanned the view in 40px steps, but the default 18dp icon is only 36px at
     * density 2.0, so the scan could step over it: the positive test was red on TECNO BG6 and the two
     * "never drawn" tests passed without ever touching an icon. The icon's real rect comes from the
     * production test hook, so the coordinates cannot drift from the layout.
     */
    private fun haloDrawnWhileHoveringTheIcon(): Boolean {
        var iconCenter: Pair<Float, Float>? = null
        instrumentation.runOnMainSync {
            lensView.draw(Canvas())
            iconCenter = lensView.drawnRectsForTest().firstOrNull()?.let { it.centerX() to it.centerY() }
        }
        val (x, y) = requireNotNull(iconCenter) { "the lens must have laid out its icon" }
        val before = lensView.focusHaloDrawCallCount
        instrumentation.runOnMainSync {
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
            lensView.dispatchTouchEvent(down)
            down.recycle()
            lensView.draw(Canvas())
            val cancel = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_CANCEL, x, y, 0)
            lensView.dispatchTouchEvent(cancel)
            cancel.recycle()
        }
        return lensView.focusHaloDrawCallCount > before
    }

    @Test
    fun defaultSettings_haloIsDrawnForHoveredApp() {
        assertTrue("halo must draw by default (setting defaults to on)", haloDrawnWhileHoveringTheIcon())
    }

    @Test
    fun settingOff_haloIsNeverDrawn() {
        settings.save(UtilSettings.KEY_SHOW_FOCUS_HALO, false)
        val before = lensView.focusHaloDrawCallCount

        haloDrawnWhileHoveringTheIcon()

        assertEquals("disabled setting must suppress the halo", before, lensView.focusHaloDrawCallCount)
    }

    @Test
    fun cleanModeOn_haloIsNeverDrawn() {
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        val before = lensView.focusHaloDrawCallCount

        haloDrawnWhileHoveringTheIcon()

        assertEquals("clean mode must suppress the halo", before, lensView.focusHaloDrawCallCount)
    }
}
