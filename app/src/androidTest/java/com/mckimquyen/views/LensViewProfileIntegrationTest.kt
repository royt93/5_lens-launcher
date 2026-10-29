package com.mckimquyen.views

import android.graphics.Canvas
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.util.LensProfile
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-027: the profile changes where icons are drawn, and the hit-test uses that same rect. */
@RunWith(AndroidJUnit4::class)
class LensViewProfileIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private lateinit var settings: UtilSettings
    private lateinit var lensView: LensView
    private val lensId = "fish027-profile-lens"

    private companion object {
        /** A finger on an icon's own centre leaves it in place, so the warp tests aim off-centre. */
        const val FINGER_OFFSET_PX = 150f
    }

    @Before fun setup() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<android.content.Context>(), R.style.AppTheme)
        settings = UtilSettings(context)
        settings.saveDistortionFactor(lensId, 2.5f)
        settings.saveLensProfile(lensId, LensProfile.FISHEYE)
        onMain {
            lensView = LensView(context)
            lensView.lensId = lensId
            lensView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
            )
            lensView.layout(0, 0, 1080, 1920)
            lensView.setApps(ArrayList((0 until 25).map { App(id = it, label = "A$it", packageName = "p.$it", name = "N$it") }))
        }
    }

    @After fun tearDown() = settings.deleteLensSettings(lensId)

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    /** Draws one frame with the finger at (x, y) and returns every icon rect as drawn (after the warp). */
    private fun drawnRects(x: Float, y: Float, multiplier: Float = 1f, reduceMotion: Boolean = false): List<android.graphics.RectF> {
        var rects: List<android.graphics.RectF> = emptyList()
        onMain {
            lensView.setLensStateForTest(x, y, multiplier, reduceMotion)
            lensView.draw(Canvas())
            rects = lensView.drawnRectsForTest()
        }
        return rects
    }

    /** The idle (unwarped) rects, which are also the base grid. */
    private fun baseRects(): List<android.graphics.RectF> =
        drawnRects(-Float.MAX_VALUE, -Float.MAX_VALUE, multiplier = 0f)

    /** The icon whose base cell is [index], drawn while the finger is at base-centre + (dx, dy). */
    private fun warped(index: Int, dx: Float, dy: Float): android.graphics.RectF {
        val base = baseRects()
        val fx = base[index].centerX() + dx
        val fy = base[index].centerY() + dy
        return drawnRects(fx, fy)[index]
    }

    @Test fun profileIsReadFromTheLensOwnKey() {
        settings.saveLensProfile(lensId, LensProfile.ANAMORPHIC)
        assertEquals(LensProfile.ANAMORPHIC, settings.getLensProfile(lensId))
    }

    @Test fun anIdleLensDrawsEveryProfileOnTheBaseGrid() {
        val base = baseRects()
        for (profile in LensProfile.entries) {
            settings.saveLensProfile(lensId, profile)
            val idle = drawnRects(-Float.MAX_VALUE, -Float.MAX_VALUE, multiplier = 0f, reduceMotion = true)
            assertEquals(profile.name, base, idle)
        }
    }

    @Test fun fisheyeMovesIconsOnBothAxes() {
        settings.saveLensProfile(lensId, LensProfile.FISHEYE)
        val base = baseRects()[4]
        val moved = warped(4, dx = FINGER_OFFSET_PX, dy = FINGER_OFFSET_PX)
        assertTrue("fisheye must move the icon horizontally", Math.abs(moved.centerX() - base.centerX()) > 1f)
        assertTrue("fisheye must move the icon vertically", Math.abs(moved.centerY() - base.centerY()) > 1f)
    }

    @Test fun anamorphicMovesOnlyTheHorizontalAxis() {
        settings.saveLensProfile(lensId, LensProfile.ANAMORPHIC)
        val base = baseRects()[4]
        val moved = warped(4, dx = FINGER_OFFSET_PX, dy = FINGER_OFFSET_PX)
        assertTrue("horizontal axis must move", Math.abs(moved.centerX() - base.centerX()) > 1f)
        assertEquals("vertical axis must stay on the base row", base.centerY(), moved.centerY(), 0.01f)
    }

    @Test fun cylindricalMovesOnlyTheVerticalAxis() {
        settings.saveLensProfile(lensId, LensProfile.CYLINDRICAL)
        val base = baseRects()[4]
        val moved = warped(4, dx = FINGER_OFFSET_PX, dy = FINGER_OFFSET_PX)
        assertTrue("vertical axis must move", Math.abs(moved.centerY() - base.centerY()) > 1f)
        assertEquals("horizontal axis must stay on the base column", base.centerX(), moved.centerX(), 0.01f)
    }

    @Test fun everyProfileKeepsIconsWithARealSize() {
        for (profile in LensProfile.entries) {
            settings.saveLensProfile(lensId, profile)
            val rects = drawnRects(540f, 960f)
            assertTrue("${profile.name}: every icon needs a positive size",
                rects.take(25).all { it.width() > 1f && it.height() > 1f })
        }
    }

    @Test fun theHitTestSelectsTheSameRectThatIsDrawnUnderTheFinger() {
        for (profile in LensProfile.entries) {
            settings.saveLensProfile(lensId, profile)
            val drawn = drawnRects(540f, 960f)
            var selected: android.graphics.RectF? = null
            onMain { selected = lensView.selectedRectForTest() }
            val chosen = selected
            if (chosen != null) {
                assertTrue("${profile.name}: the selected rect must be one of the drawn rects and contain the finger",
                    drawn.any { it == chosen } && chosen.contains(540f, 960f))
            }
        }
    }
}
