package com.mckimquyen.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-017: a real LensView reads the saved level for BOTH the hover and the launch haptic,
 * keeps the old VIRTUAL_KEY behavior when nothing was saved, and emits nothing when the
 * existing gates (switch off, reduced motion) say no.
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
    private val performed = mutableListOf<Int>()

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
            lensView.onHapticPerformed = { performed += it }
        }
    }

    @After
    fun tearDown() {
        rawPrefs().edit().clear().commit()
    }

    /** Presses on the first icon and redraws so the hover is detected; returns after the draw. */
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

    private fun expected(level: HapticIntensity) = level.feedbackConstant

    /** One fresh LensView per test (from @Before): a second hover on the same icon never re-fires. */
    private fun assertHoverEmits(level: HapticIntensity) {
        assumeFalse(LensPhysicsPolicy.shouldReduceLensMotion(context))
        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        settings.saveHapticIntensity(level)
        hoverFirstIcon()
        assertEquals("level $level", listOf(expected(level)), performed.take(1))
    }

    @Test
    fun hover_light() = assertHoverEmits(HapticIntensity.LIGHT)

    @Test
    fun hover_medium() = assertHoverEmits(HapticIntensity.MEDIUM)

    @Test
    fun hover_strong() = assertHoverEmits(HapticIntensity.STRONG)

    @Test
    fun launch_usesTheSavedLevel() {
        assumeFalse(LensPhysicsPolicy.shouldReduceLensMotion(context))
        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        settings.save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, true)
        settings.saveHapticIntensity(HapticIntensity.STRONG)
        hoverFirstIcon()
        performed.clear()
        releaseOnFirstIcon()
        assertEquals(listOf(expected(HapticIntensity.STRONG)), performed.take(1))
    }

    @Test
    fun noSavedLevel_keepsTheOldVirtualKeyBehavior() {
        assumeFalse(LensPhysicsPolicy.shouldReduceLensMotion(context))
        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, true)
        hoverFirstIcon()
        assertEquals(
            listOf(android.view.HapticFeedbackConstants.VIRTUAL_KEY),
            performed.take(1)
        )
    }

    @Test
    fun switchesOff_emitNothingRegardlessOfLevel() {
        settings.save(UtilSettings.KEY_VIBRATE_APP_HOVER, false)
        settings.save(UtilSettings.KEY_VIBRATE_APP_LAUNCH, false)
        settings.saveHapticIntensity(HapticIntensity.STRONG)
        hoverFirstIcon()
        releaseOnFirstIcon()
        assertTrue("expected no haptic, got $performed", performed.isEmpty())
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
        scenario.close() // destroys the Activity, which detaches the view
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val lensAfterDestroy = requireNotNull(homeLens) {
            "homeLens must have been set by onActivity before scenario.close()"
        }
        assertEquals(null, lensAfterDestroy.onHapticPerformed)
    }
}
