package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.util.BitmapCache
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.hypot

/**
 * TECHNICAL DEBT CLEARANCE: Tests ALL 7 optical lens effects running together in a single real gesture on device:
 * 1. Depth of Field blur (depth_of_field_blur)
 * 2. Lens Vignette (lens_vignette)
 * 3. Focus Peaking (focus_peaking)
 * 4. Chromatic Aberration rim (chromatic_aberration)
 * 5. Lens Inertia (lens_inertia)
 * 6. Focus Snap-to-icon (focus_snap)
 * 7. Sharp Cluster Haptic (vibrate_app_hover)
 */
@RunWith(AndroidJUnit4::class)
class LensViewAllOpticalEffectsCombinedTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val VIEW_SIZE_PX = 1_000
        const val APP_COUNT = 120
        const val ICON_PX = 48
        const val LIGHT_AMPLITUDE = 80
        const val SLOW_SWEEP_STEPS = 60
        const val SWEEP_STEP_PX = 5f
        const val FRAME_DELAY_MS = 16L
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val density = context.resources.displayMetrics.density

    private fun setAllEffects(enabled: Boolean) {
        val us = UtilSettings(context)
        us.save(UtilSettings.KEY_DEPTH_OF_FIELD, enabled)
        us.save(UtilSettings.KEY_LENS_VIGNETTE, enabled)
        us.save(UtilSettings.KEY_FOCUS_PEAKING, enabled)
        us.save(UtilSettings.KEY_CHROMATIC_ABERRATION, enabled)
        us.save(UtilSettings.KEY_LENS_INERTIA, enabled)
        us.save(UtilSettings.KEY_FOCUS_SNAP, enabled)
        us.save(UtilSettings.KEY_VIBRATE_APP_HOVER, enabled)
    }

    @Before fun setUp() {
        setAllEffects(true)
    }

    @After fun tearDown() {
        setAllEffects(false)
        BitmapCache.clear()
    }

    @Test fun allSevenOpticalEffectsOperateSimultaneouslyWithoutCrashingOrStarvingEachOther() {
        // Seed real white bitmap icons so chromatic aberration and icon render paths activate
        val testIcon = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.all.opt$i", name = "Opt$i", label = "Opt $i", iconCacheKey = "opt#$i").also {
                RAppsSingleton.instance.setAppIcon(it.iconCacheKey, testIcon)
            }
        })

        val haptics = mutableListOf<Int>()
        var view: LensView? = null

        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                view = LensView(activity).apply {
                    setApps(apps)
                    layoutParams = FrameLayout.LayoutParams(VIEW_SIZE_PX, VIEW_SIZE_PX)
                    activity.setContentView(this)
                    measure(
                        View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(VIEW_SIZE_PX, View.MeasureSpec.EXACTLY)
                    )
                    layout(0, 0, VIEW_SIZE_PX, VIEW_SIZE_PX)
                    onHapticPerformed = { haptics.add(it) }
                    draw(Canvas())
                }
            }
            val target = requireNotNull(view)

            var sawDofBands = false
            var sawPeakingRings = false
            var sawChromaIcons = false
            var sawInertiaLag = false
            var sawSnapPull = false
            var maxClusterSize = 0
            var sawSelection = false

            val downTime = SystemClock.uptimeMillis()
            fun send(action: Int, x: Float, y: Float) = instrumentation.runOnMainSync {
                val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
                target.dispatchTouchEvent(e)
                e.recycle()
            }

            var x = 200f
            var y = 700f
            send(MotionEvent.ACTION_DOWN, x, y)
            // Real pan breakout
            x += 40f * density
            y -= 40f * density
            send(MotionEvent.ACTION_MOVE, x, y)

            repeat(SLOW_SWEEP_STEPS) {
                x += SWEEP_STEP_PX
                y -= SWEEP_STEP_PX
                send(MotionEvent.ACTION_MOVE, x, y)

                instrumentation.runOnMainSync {
                    target.draw(Canvas())
                    // 1. DoF
                    if (target.dofBandCounts.sum() > 0) sawDofBands = true
                    // 2. Peaking
                    if (target.focusPeakingRingsForTest > 0) sawPeakingRings = true
                    // 3. Chromatic aberration
                    if (target.chromaIconsForTest > 0) sawChromaIcons = true
                    // 4. Inertia: visual centre lags the snapped target
                    val visual = target.visualCentreForTest()
                    val touch = target.touchCoordinatesForTest()
                    if (hypot(visual.first - touch.first, visual.second - touch.second) > 0.5f) {
                        sawInertiaLag = true
                    }
                    // 5. Focus snap: optical centre offset from raw touch
                    val optic = target.opticalCentreForTest()
                    if (hypot(optic.first - touch.first, optic.second - touch.second) > 0.5f) {
                        sawSnapPull = true
                    }
                    // 6. Sharp cluster tracker
                    maxClusterSize = maxOf(maxClusterSize, target.sharpClusterSizeForTest)
                    if (target.selectedIndexForTest >= 0) sawSelection = true
                }
                SystemClock.sleep(FRAME_DELAY_MS)
            }

            send(MotionEvent.ACTION_UP, x, y)

            // Assertions prove every effect was truly active during the continuous pan
            assertTrue("1. Depth-of-field bands were classified", sawDofBands)
            assertTrue("2. Focus peaking rings were drawn", sawPeakingRings)
            assertTrue("3. Chromatic aberration fringed icons", sawChromaIcons)
            assertTrue("4. Lens inertia lagged the visual centre", sawInertiaLag)
            assertTrue("5. Focus snap pulled the optical centre", sawSnapPull)
            assertTrue("6. Sharp cluster counted icons (max=$maxClusterSize)", maxClusterSize >= 3)
            assertTrue("7. Haptics emitted at least one LIGHT pulse during the sweep (total=${haptics.size})",
                haptics.contains(LIGHT_AMPLITUDE))
            assertTrue("Hit-test selection was resolved during the pan", sawSelection)

            // After ACTION_UP: clean teardown, no lingering state
            instrumentation.runOnMainSync {
                target.draw(Canvas())
                assertEquals("Selection cleared on UP", -1, target.selectedIndexForTest)
                assertEquals("Cluster tracker reset on UP", 0, target.sharpClusterSizeForTest)
            }
        }
    }
}
