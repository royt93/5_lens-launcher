package com.mckimquyen.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.util.LensPhysicsPolicy
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.hypot

@RunWith(AndroidJUnit4::class)
class LensViewFocusSnapWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val VIEW_SIZE_PX = 1_000
        const val APP_COUNT = 40
    }

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val density get() = context.resources.displayMetrics.density

    @Before fun clearPrefs() {
        UtilSettings(context).save(UtilSettings.KEY_FOCUS_SNAP, false)
    }

    @After fun tearDown() {
        UtilSettings(context).save(UtilSettings.KEY_FOCUS_SNAP, false)
    }

    private fun buildView(activity: android.app.Activity, appsCount: Int = APP_COUNT): LensView {
        val apps = ArrayList((0 until appsCount).map { i ->
            App(id = i, packageName = "com.fish044.snap$i", name = "Snap$i", label = "Snap $i")
        })
        val view = LensView(activity).apply {
            setApps(apps)
            layoutParams = FrameLayout.LayoutParams(VIEW_SIZE_PX, VIEW_SIZE_PX)
            activity.setContentView(this)
            measure(
                View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, VIEW_SIZE_PX, VIEW_SIZE_PX)
            draw(Canvas())
        }
        return view
    }

    @Test fun withTheSnapSwitchOffTheOpticalCentreMatchesTheFinger() {
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = buildView(activity)
                view.focusSnapEnabled = false
                view.setSnapStateForTest(enabled = false, panning = true, speedDpPerS = 0f)
                view.setLensStateForTest(touchX = 500f, touchY = 500f, animationMultiplier = 1f, reduceMotion = false)
                view.draw(Canvas())
                val optic = view.opticalCentreForTest()
                assertEquals(500f, optic.first, 1e-3f)
                assertEquals(500f, optic.second, 1e-3f)
            }
        }
    }

    @Test fun reducedMotionSuppressesSnapEvenWhenEnabledAndSlow() {
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = buildView(activity)
                view.setSnapStateForTest(enabled = true, panning = true, speedDpPerS = 0f)
                view.setLensStateForTest(touchX = 500f, touchY = 500f, animationMultiplier = 1f, reduceMotion = true)
                view.draw(Canvas())
                val optic = view.opticalCentreForTest()
                assertEquals("reduced motion keeps finger x", 500f, optic.first, 1e-3f)
                assertEquals("reduced motion keeps finger y", 500f, optic.second, 1e-3f)
            }
        }
    }

    @Test fun withoutARealPanNoSnapApplies() {
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = buildView(activity)
                view.setSnapStateForTest(enabled = true, panning = false, speedDpPerS = 0f)
                view.setLensStateForTest(touchX = 500f, touchY = 500f, animationMultiplier = 1f, reduceMotion = false)
                view.draw(Canvas())
                val optic = view.opticalCentreForTest()
                assertEquals(500f, optic.first, 1e-3f)
                assertEquals(500f, optic.second, 1e-3f)
            }
        }
    }

    @Test fun slowPanMovesOpticalCentreTowardTheNearestIconWithinThe16DpCap() {
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = buildView(activity)
                // Pick a finger point slightly offset from cell 0's centre
                val rects = view.baseRectsForTest()
                assertTrue(rects.isNotEmpty())
                val fingerX = rects[0].centerX() + 30f * density
                val fingerY = rects[0].centerY() + 40f * density
                // The pull goes to the icon nearest the finger, which need not be cell 0.
                val target = rects[FocusSnap.nearestApp(fingerX, fingerY, rects, APP_COUNT)]
                view.setSnapStateForTest(enabled = true, panning = true, speedDpPerS = 0f)
                view.setLensStateForTest(touchX = fingerX, touchY = fingerY, animationMultiplier = 1f, reduceMotion = false)
                view.draw(Canvas())
                val optic = view.opticalCentreForTest()
                val pull = hypot(optic.first - fingerX, optic.second - fingerY)
                val maxPx = FocusSnap.MAX_PULL_DP * density
                assertTrue("pull $pull must be > 0", pull > 1f)
                assertTrue("pull $pull must not exceed cap $maxPx", pull <= maxPx + 1e-2f)
                // Optical centre moved closer to the target than the finger
                val initialDist = hypot(fingerX - target.centerX(), fingerY - target.centerY())
                val newDist = hypot(optic.first - target.centerX(), optic.second - target.centerY())
                assertTrue("optical centre is closer to target: $newDist < $initialDist", newDist < initialDist)
            }
        }
    }

    @Test fun fastPanAtOrAboveThresholdProducesZeroSnap() {
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = buildView(activity)
                view.setSnapStateForTest(enabled = true, panning = true, speedDpPerS = FocusSnap.SPEED_THRESHOLD_DP_PER_S)
                view.setLensStateForTest(touchX = 500f, touchY = 500f, animationMultiplier = 1f, reduceMotion = false)
                view.draw(Canvas())
                val optic = view.opticalCentreForTest()
                assertEquals(500f, optic.first, 1e-3f)
                assertEquals(500f, optic.second, 1e-3f)
            }
        }
    }

    @Test fun selectionStaysOnTheRealFingerCentreEvenWhenOpticalCentreIsSnapped() {
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = buildView(activity)
                val rects = view.baseRectsForTest()
                val target = rects[0]
                // Finger is right on target's centre, selection must pick cell 0
                val fingerX = target.centerX()
                val fingerY = target.centerY()
                view.setSnapStateForTest(enabled = true, panning = true, speedDpPerS = 0f)
                view.setLensStateForTest(touchX = fingerX, touchY = fingerY, animationMultiplier = 1f, reduceMotion = false)
                view.draw(Canvas())
                assertEquals("finger centre drives selection", 0, view.selectedIndexForTest)
            }
        }
    }
}
