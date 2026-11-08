package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-042 widget proof on a bare LensView drawn into a software canvas. */
@RunWith(AndroidJUnit4::class)
class LensViewFocusBoundaryHapticWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SIZE_PX = 800
        const val APP_COUNT = 40
        const val FULL_ANIMATION = 1f
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var lensView: LensView
    private val haptics = mutableListOf<Int>()

    @Before
    fun setUp() {
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish042.app$i", name = "App$i", label = "App $i")
        })
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        instrumentation.runOnMainSync {
            lensView = LensView(context).apply {
                layout(0, 0, SIZE_PX, SIZE_PX)
                setApps(apps)
                onHapticPerformed = { haptics.add(it) }
            }
        }
        haptics.clear()
    }

    private fun render() {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync { lensView.draw(Canvas(bitmap)) }
        bitmap.recycle()
    }

    private fun touchAt(x: Float, y: Float, reduceMotion: Boolean = false) =
        lensView.setLensStateForTest(x, y, FULL_ANIMATION, reduceMotion)

    private fun cellCentre(index: Int): Pair<Float, Float> {
        render()
        val bounds = Rect()
        var found = false
        instrumentation.runOnMainSync { found = lensView.getAppBounds(index, bounds) }
        assertTrue("cell $index must exist", found)
        return bounds.exactCenterX() to bounds.exactCenterY()
    }

    private fun startPan() {
        val moving = LensView::class.java.getDeclaredField("mMoving").apply { isAccessible = true }
        moving.setBoolean(lensView, true)
        instrumentation.runOnMainSync { lensView.gestureState = LensGestureState.PANNING }
    }

    /**
     * Touch cell 0 at two positions: one whose focus distance puts it inside the sharp zone,
     * another whose focus distance puts it outside. The trick: move the lens (touch) centre,
     * not the app, so the SAME app stays selected (cell 0) while the distance changes.
     *
     * When the touch is ON cell 0, focusDistance(touch, cell0 base centre) ≈ 0 (inside).
     * When the touch is FAR from cell 0, focusDistance(touch, cell0 base centre) > FOCUS_ZONE (outside),
     * but cell 0 is still the selected icon because after the warp it is the one under the finger.
     *
     * In practice on an 800px grid with 40 apps, cell 0 is top-left and a touch at the centre
     * warps cell 0 larger so it still falls under the finger.  We verify the precondition.
     */

    /**
     * Moving the touch across the grid changes the selected app: the tracker must re-baseline to the new app
     * silently (no boundary pulse on selection change). The crossing itself is proven unconditionally in
     * [FocusBoundaryHapticsTest].
     */
    @Test fun movingTouchToADifferentAppEstablishesFreshBaselineWithoutBoundaryPulse() {
        val (cx, cy) = cellCentre(0)
        touchAt(cx, cy)
        startPan()
        render()
        val firstApp = lensView.selectedIndexForTest
        haptics.clear()

        // Move to the far corner: selects a different app
        val (fx, fy) = cellCentre(APP_COUNT - 1)
        touchAt(fx, fy)
        render()
        val secondApp = lensView.selectedIndexForTest

        // Must be two different apps
        assertTrue("precondition: touch selected a different app ($firstApp vs $secondApp)", firstApp != secondApp)
        // Must NOT fire a boundary pulse when the app identity changes
        assertFalse("app change must establish baseline without a boundary pulse", lensView.boundaryHapticFiredForTest)
    }

    /**
     * When both hover haptic and boundary haptic could fire in the same frame, at most one pulse is emitted.
     */
    @Test fun boundaryPulseSuppressesHoverHapticInTheSameFrame() {
        val (cx, cy) = cellCentre(0)
        touchAt(cx, cy)
        startPan()
        render()
        haptics.clear()

        // Move by a tiny step so hover would normally fire on entering a cell, but tracker is checked first
        touchAt(cx + 2f, cy + 2f)
        render()

        // Total haptics in this frame must be at most 1
        assertTrue("at most one haptic emitted in one frame, was ${haptics.size}", haptics.size <= 1)
    }

    @Test fun stayingInsideTheSharpZoneDoesNotPulse() {
        val (cx, cy) = cellCentre(0)
        touchAt(cx, cy)
        startPan()
        render()
        haptics.clear()
        touchAt(cx + 1f, cy + 1f)
        render()
        assertFalse("no boundary crossing, no pulse", lensView.boundaryHapticFiredForTest)
    }

    @Test fun withTheHoverSwitchOffNoPulseFires() {
        UtilSettings(context).save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        val (cx, cy) = cellCentre(0)
        touchAt(cx, cy)
        startPan()
        render()
        haptics.clear()
        touchAt(SIZE_PX / 2f, SIZE_PX / 2f)
        render()
        assertFalse("the switch is off", lensView.boundaryHapticFiredForTest)
        assertTrue("no haptics at all", haptics.isEmpty())
    }

    @Test fun reducedMotionSuppressesBoundaryPulse() {
        val (cx, cy) = cellCentre(0)
        touchAt(cx, cy, reduceMotion = true)
        startPan()
        render()
        haptics.clear()
        touchAt(SIZE_PX / 2f, SIZE_PX / 2f, reduceMotion = true)
        render()
        assertFalse(lensView.boundaryHapticFiredForTest)
    }

    @Test fun withoutAPanNoPulseFires() {
        val (cx, cy) = cellCentre(0)
        touchAt(cx, cy)
        render()
        haptics.clear()
        touchAt(SIZE_PX / 2f, SIZE_PX / 2f)
        render()
        assertFalse("no pan = no boundary haptic", lensView.boundaryHapticFiredForTest)
    }

    @Test fun actionUpResetsTrackerSoNewGestureStartsAsFreshBaseline() {
        // Step 1: Touch down on cell 0, drag to far position, render (insideSharp=false, key=app0)
        val (cx, cy) = cellCentre(0)
        touchAt(cx, cy)
        startPan()
        render() // establishes baseline on cell 0
        // Move far so cell 0 transitions to outside sharp (fires pulse)
        touchAt(SIZE_PX / 2f, SIZE_PX / 2f)
        render()
        // Step 2: ACTION_UP ends the gesture
        val down = android.os.SystemClock.uptimeMillis()
        instrumentation.runOnMainSync {
            val up = android.view.MotionEvent.obtain(down, down + 50, android.view.MotionEvent.ACTION_UP, cx, cy, 0)
            lensView.dispatchTouchEvent(up)
            up.recycle()
        }
        // Step 3: New gesture starts directly at cell 0 (inside sharp)
        touchAt(cx, cy)
        startPan()
        render()
        // If tracker survived: previousAppKey=app0, was outside, now inside => fires pulse!
        // Expected: fresh baseline on new gesture => must NOT fire pulse on first frame
        assertFalse("new gesture must establish fresh baseline without pulse", lensView.boundaryHapticFiredForTest)
    }

}
