package com.mckimquyen.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibrationEffect
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.util.HapticIntensity
import com.mckimquyen.util.LensPhysicsPolicy
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-020: LensView uses VibrationEffect with duration/amplitude per HapticIntensity level.
 * Tests verify that haptics fire when conditions allow, and gates (switches, reduced motion) work.
 * Note: VibrationEffect details cannot be inspected post-creation; tests verify calls only occur
 * when they should (gates, seams). Full duration/amplitude verification happens in smoke test
 * via dumpsys vibrator_manager observation.
 */
@RunWith(AndroidJUnit4::class)
class LensViewHapticIntensityIntegrationTest {

    private companion object {
        const val VIEW_WIDTH = 1080
        const val VIEW_HEIGHT = 1920
        const val FIRST_INDEX = 0
    }

    private lateinit var context: Context
    private lateinit var lensView: LensView
    private lateinit var settings: UtilSettings
    private var hapticFireCount = 0

    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun setup() {
        context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext, R.style.AppTheme
        )
        rawPrefs().edit().clear().commit()
        settings = UtilSettings(context)
        hapticFireCount = 0

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView = LensView(context)
            lensView.setApps(
                arrayListOf(
                    App(id = 1, label = "App 1", packageName = "com.test1", name = "Act1"),
                    App(id = 2, label = "App 2", packageName = "com.test2", name = "Act2")
                )
            )
            lensView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, android.view.View.MeasureSpec.EXACTLY)
            )
            lensView.layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)
            lensView.draw(Canvas())

            // Track onHapticPerformed calls to verify haptics fired (without inspecting VibrationEffect details)
            lensView.onHapticPerformed = { hapticFireCount++ }
        }
    }

    @After
    fun tearDown() {
        rawPrefs().edit().clear().commit()
        hapticFireCount = 0
    }

    private fun hoverFirstIcon() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val bounds = Rect()
            assertTrue("grid must have drawn", lensView.getAppBounds(FIRST_INDEX, bounds))
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_DOWN,
                bounds.exactCenterX(), bounds.exactCenterY(), 0
            )
            lensView.dispatchTouchEvent(down)
            down.recycle()
            lensView.draw(Canvas())
        }
    }

    private fun releaseOnFirstIcon() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val bounds = Rect()
            lensView.getAppBounds(FIRST_INDEX, bounds)
            val now = SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(
                now, now + 10, MotionEvent.ACTION_UP,
                bounds.exactCenterX(), bounds.exactCenterY(), 0
            )
            lensView.dispatchTouchEvent(up)
            up.recycle()
        }
    }

    @Test
    fun hover_light_firesHaptic() {
        assumeFalse(LensPhysicsPolicy.shouldReduceLensMotion(context))
        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        settings.saveHapticIntensity(HapticIntensity.LIGHT)

        val beforeCount = hapticFireCount
        hoverFirstIcon()

        assertTrue("hover should trigger haptic when switch is on", hapticFireCount > beforeCount)
    }

    @Test
    fun hover_medium_firesHaptic() {
        assumeFalse(LensPhysicsPolicy.shouldReduceLensMotion(context))
        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        settings.saveHapticIntensity(HapticIntensity.MEDIUM)

        val beforeCount = hapticFireCount
        hoverFirstIcon()

        assertTrue("hover should trigger haptic when switch is on", hapticFireCount > beforeCount)
    }

    @Test
    fun hover_strong_firesHaptic() {
        assumeFalse(LensPhysicsPolicy.shouldReduceLensMotion(context))
        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        settings.saveHapticIntensity(HapticIntensity.STRONG)

        val beforeCount = hapticFireCount
        hoverFirstIcon()

        assertTrue("hover should trigger haptic when switch is on", hapticFireCount > beforeCount)
    }

    @Test
    fun switchesOff_preventsHaptic() {
        assumeFalse(LensPhysicsPolicy.shouldReduceLensMotion(context))
        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        settings.save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, false)
        settings.saveHapticIntensity(HapticIntensity.STRONG)

        val beforeCount = hapticFireCount
        hoverFirstIcon()
        releaseOnFirstIcon()

        assertEquals("haptics must not fire when switches are off", beforeCount, hapticFireCount)
    }

    @Test
    fun reducedMotion_preventsHaptic() {
        assumeTrue(LensPhysicsPolicy.shouldReduceLensMotion(context))
        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        settings.saveHapticIntensity(HapticIntensity.STRONG)

        val beforeCount = hapticFireCount
        hoverFirstIcon()

        assertEquals("haptics must not fire under reduced motion", beforeCount, hapticFireCount)
    }

    @Test
    fun hookIsReleasedWhenTheHostingActivityIsDestroyed() {
        val scenario = androidx.test.core.app.ActivityScenario.launch(com.mckimquyen.ui.ActHome::class.java)
        var homeLens: LensView? = null
        scenario.onActivity {
            val found = requireNotNull(it.findViewById<LensView>(R.id.lensViews)) {
                "lensViews must be found while the Activity is alive"
            }
            found.onHapticPerformed = { }
            assertNotNull("hook must be non-null before destroy", found.onHapticPerformed)
            homeLens = found
        }
        scenario.close()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val lensAfterDestroy = requireNotNull(homeLens) {
            "homeLens must have been set by onActivity before scenario.close()"
        }
        assertNull("hook must be null after destroy", lensAfterDestroy.onHapticPerformed)
    }

    @Test
    fun vibratorSeamIsNulledOnDetach() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)

        lensView.vibratorProvider = { context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator }
        assertNotNull("seam must be set before detach", lensView.vibratorProvider)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.onDetachedFromWindow()
        }

        assertNull("seam must be null after detach", lensView.vibratorProvider)
    }
}
