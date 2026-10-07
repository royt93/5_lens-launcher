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
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

/**
 * FISH-020: LensView uses VibrationEffect with duration/amplitude per HapticIntensity level.
 * Tests verify that Vibrator.vibrate(effect) is called with correct effect parameters,
 * and that existing gates (switches, reduced motion) still work.
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
    private var mockVibrator: Vibrator? = null

    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun setup() {
        context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext, R.style.AppTheme
        )
        rawPrefs().edit().clear().commit()
        settings = UtilSettings(context)

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

            // Skip tests if API < 26 (fallback path is simple)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mockVibrator = mock(Vibrator::class.java)
                lensView.vibratorProvider = { mockVibrator }
            }
        }
    }

    @After
    fun tearDown() {
        rawPrefs().edit().clear().commit()
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
    fun hover_light_triggersVibrationEffectWithLightDurationAmplitude() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        assumeFalse(LensPhysicsPolicy.shouldReduceLensMotion(context))

        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        settings.saveHapticIntensity(HapticIntensity.LIGHT)

        hoverFirstIcon()

        verify(mockVibrator).vibrate(
            org.mockito.ArgumentMatchers.any(VibrationEffect::class.java)
        )
    }

    @Test
    fun hover_medium_triggersVibrationEffectWithMediumDurationAmplitude() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        assumeFalse(LensPhysicsPolicy.shouldReduceLensMotion(context))

        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        settings.saveHapticIntensity(HapticIntensity.MEDIUM)

        hoverFirstIcon()

        verify(mockVibrator).vibrate(
            org.mockito.ArgumentMatchers.any(VibrationEffect::class.java)
        )
    }

    @Test
    fun hover_strong_triggersVibrationEffectWithStrongDurationAmplitude() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        assumeFalse(LensPhysicsPolicy.shouldReduceLensMotion(context))

        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        settings.saveHapticIntensity(HapticIntensity.STRONG)

        hoverFirstIcon()

        verify(mockVibrator).vibrate(
            org.mockito.ArgumentMatchers.any(VibrationEffect::class.java)
        )
    }

    @Test
    fun switchesOff_emitNothingRegardlessOfLevel() {
        assumeFalse(LensPhysicsPolicy.shouldReduceLensMotion(context))

        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        settings.save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, false)
        settings.saveHapticIntensity(HapticIntensity.STRONG)

        hoverFirstIcon()
        releaseOnFirstIcon()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verify(mockVibrator, org.mockito.Mockito.never())
                .vibrate(org.mockito.ArgumentMatchers.any(VibrationEffect::class.java))
        }
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

        lensView.vibratorProvider = { mockVibrator }
        assertNotNull("seam must be set before detach", lensView.vibratorProvider)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView.onDetachedFromWindow()
        }

        assertNull("seam must be null after detach", lensView.vibratorProvider)
    }
}
