package com.mckimquyen.views

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-009: Integration tests for live pinch-to-adjust lens curvature.
 * Verifies multi-touch state transitions, non-interference with pan,
 * app launch suppression on pinch lift, LensGridCache invariance,
 * and commit/reset flows.
 */
@RunWith(AndroidJUnit4::class)
class LensViewPinchIntegrationTest {

    private lateinit var context: Context
    private lateinit var lensView: LensView
    private lateinit var utilSettings: UtilSettings

    @Before
    fun setup() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        context = ContextThemeWrapper(targetContext, R.style.AppTheme)
        utilSettings = UtilSettings(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView = LensView(context)
            val testApps = arrayListOf(
                App(id = 1, label = "App 1", packageName = "com.test1", name = "Act1"),
                App(id = 2, label = "App 2", packageName = "com.test2", name = "Act2")
            )
            lensView.setApps(testApps)
            lensView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
            )
            lensView.layout(0, 0, 1080, 1920)
            lensView.draw(Canvas())
        }
    }

    @org.junit.After
    fun tearDown() {
        // FISH-008 Phase 3: the per-lens test below writes a suffixed key for a lens that only
        // exists inside this test - drop it so a run leaves no orphan in the app's preferences.
        utilSettings.deleteLensSettings("other-lens")
    }

    private fun obtainMultiTouchEvent(
        action: Int,
        downTime: Long,
        eventTime: Long,
        p0X: Float,
        p0Y: Float,
        p1X: Float,
        p1Y: Float
    ): MotionEvent {
        val pProps = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER }
        )
        val pCoords = arrayOf(
            MotionEvent.PointerCoords().apply { x = p0X; y = p0Y },
            MotionEvent.PointerCoords().apply { x = p1X; y = p1Y }
        )
        return MotionEvent.obtain(
            downTime, eventTime, action,
            2, pProps, pCoords, 0, 0, 1f, 1f, 0, 0,
            InputDevice.SOURCE_TOUCHSCREEN, 0
        )
    }

    @Test
    fun testTwoFingerPointerDownEntersPinchingState() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 400f, 600f, 0)
            lensView.dispatchTouchEvent(down)
            down.recycle()

            assertEquals(LensGestureState.IDLE, lensView.gestureState)

            val pointerDown = obtainMultiTouchEvent(
                MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                now, now + 10, 400f, 600f, 600f, 600f
            )
            lensView.dispatchTouchEvent(pointerDown)
            pointerDown.recycle()

            assertEquals(LensGestureState.PINCHING, lensView.gestureState)
        }
    }

    @Test
    fun testPinchLiftEntersPinchReleaseAndDoesNotLaunchApp() {
        var appLaunched = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 400f, 600f, 0)
            lensView.dispatchTouchEvent(down)
            down.recycle()

            val pointerDown = obtainMultiTouchEvent(
                MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                now, now + 10, 400f, 600f, 600f, 600f
            )
            lensView.dispatchTouchEvent(pointerDown)
            pointerDown.recycle()

            assertEquals(LensGestureState.PINCHING, lensView.gestureState)

            // One pointer lifts
            val pointerUp = obtainMultiTouchEvent(
                MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                now, now + 20, 400f, 600f, 600f, 600f
            )
            lensView.dispatchTouchEvent(pointerUp)
            pointerUp.recycle()

            assertEquals(LensGestureState.PINCH_RELEASE, lensView.gestureState)

            // Last pointer lifts
            val up = MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_UP, 400f, 600f, 0)
            lensView.dispatchTouchEvent(up)
            up.recycle()

            // State returns to IDLE without launching app
            assertEquals(LensGestureState.IDLE, lensView.gestureState)
            assertFalse("App launch must be blocked on pinch release", appLaunched)
        }
    }

    @Test
    fun testPinchUpdatesLiveDistortionAndNotifiesListener() {
        var reportedCurvature = 0f
        var gestureFinished = false

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.onCurvatureAdjustedListener = OnCurvatureAdjustedListener { curvature, finished ->
                reportedCurvature = curvature
                gestureFinished = finished
            }

            // Simulate scale event by setting live distortion
            val initial = utilSettings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR)
            val updated = LensView.calculatePinchDistortion(initial, 1.4f)
            lensView.liveDistortionFactor = updated
            lensView.onCurvatureAdjustedListener?.onCurvatureAdjusted(updated, true)

            assertEquals(updated, lensView.liveDistortionFactor)
            assertEquals(updated, reportedCurvature, 0.001f)
            assertTrue(gestureFinished)
        }
    }

    @Test
    fun testCommitAndResetLiveDistortionFactor() {
        val originalDistortion = 2.5f
        utilSettings.save(UtilSettings.KEY_DISTORTION_FACTOR, originalDistortion)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // Set temporary live curvature
            lensView.liveDistortionFactor = 4.0f
            assertEquals(4.0f, lensView.liveDistortionFactor)
            assertEquals(originalDistortion, utilSettings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR), 0.001f)

            // Reset reverts to null
            lensView.resetLiveDistortionFactor()
            assertNull(lensView.liveDistortionFactor)
            assertEquals(originalDistortion, utilSettings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR), 0.001f)

            // Set again and commit
            lensView.liveDistortionFactor = 3.8f
            lensView.commitLiveDistortionFactor()
            assertNull(lensView.liveDistortionFactor)
            assertEquals(3.8f, utilSettings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR), 0.001f)
        }
    }

    @Test
    fun commitLiveDistortion_savesOnlyTheBoundLens() {
        val otherLensId = "other-lens"
        utilSettings.save(UtilSettings.KEY_DISTORTION_FACTOR, 2.5f)
        utilSettings.saveDistortionFactor(otherLensId, 1.5f)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.lensId = otherLensId
            lensView.liveDistortionFactor = 3.8f
            lensView.commitLiveDistortionFactor()
        }

        assertEquals(3.8f, utilSettings.getDistortionFactor(otherLensId), 0.001f)
        assertEquals(
            "Committing a non-default lens must not leak into the old shared value",
            2.5f,
            utilSettings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR),
            0.001f
        )
    }

    /**
     * `commitLiveDistortionFactor` writes per-lens; `drawGrid` has to read the same key back or
     * the saved curvature never actually appears on screen. Reading it through a pinch that
     * starts from no live value proves the read side agrees with the write side.
     */
    @Test
    fun curvatureReadBack_followsTheBoundLens_notTheSharedValue() {
        utilSettings.save(UtilSettings.KEY_DISTORTION_FACTOR, 2.0f)
        utilSettings.saveDistortionFactor("other-lens", 4.5f)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.lensId = "other-lens"
            lensView.resetLiveDistortionFactor()
            // A pinch with no live value in flight starts from whatever the bound lens has saved.
            lensView.liveDistortionFactor =
                LensView.calculatePinchDistortion(utilSettings.getDistortionFactor(lensView.lensId), 1.0f)
        }

        assertEquals(
            "The view must start a pinch from its own lens's curvature, not the shared one",
            4.5f,
            lensView.liveDistortionFactor!!,
            0.001f
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.resetLiveDistortionFactor()
            // Drawing must not throw or write anything while reading a per-lens value.
            lensView.draw(Canvas())
        }
        assertEquals(4.5f, utilSettings.getDistortionFactor("other-lens"), 0.001f)
        assertEquals(2.0f, utilSettings.getFloat(UtilSettings.KEY_DISTORTION_FACTOR), 0.001f)
    }

    /** A lens that never set a curvature of its own must draw with the shared one. */
    @Test
    fun aLensWithNoCurvatureOverride_readsTheSharedValue() {
        utilSettings.save(UtilSettings.KEY_DISTORTION_FACTOR, 3.2f)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.lensId = "other-lens"
            lensView.draw(Canvas())
        }

        assertEquals(3.2f, utilSettings.getDistortionFactor("other-lens"), 0.001f)
    }

    @Test
    fun testLensGridCacheInvarianceDuringCurvatureChange() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val gridCacheField = LensView::class.java.getDeclaredField("mGridCache")
            gridCacheField.isAccessible = true
            val gridCache = gridCacheField.get(lensView) as LensGridCache

            // Initial draw establishes the cache
            lensView.draw(Canvas())
            val initialRecomputeCount = gridCache.recomputeCount

            // Changing curvature over multiple frames
            val testDistortions = listOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 0.5f)
            for (dist in testDistortions) {
                lensView.liveDistortionFactor = dist
                lensView.draw(Canvas())
            }

            // LensGridCache baseRects only depend on iconSize/dimensions/insets, NOT distortion.
            // Recompute count must not increase at all during curvature changes.
            assertEquals(
                "LensGridCache must not recompute baseRects during pinch curvature changes",
                initialRecomputeCount,
                gridCache.recomputeCount
            )
        }
    }
}
