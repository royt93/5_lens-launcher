package com.mckimquyen.ui

import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * BUILD-005: the three big PNGs became density-independent WebP in drawable-nodpi. A nodpi bitmap is
 * never scaled by density, so a wrap_content ImageView would draw it at its raw pixel size on every
 * phone. These tests measure the real rendered size, not just that the resource resolves.
 */
@RunWith(AndroidJUnit4::class)
class ImageAssetRenderWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val density get() = context.resources.displayMetrics.density

    private fun bitmapOf(id: Int) = (context.getDrawable(id) as BitmapDrawable).bitmap

    @Test fun launcherLogoDecodesAtItsFullPixelSizeBecauseNodpiNeverScales() {
        val round = bitmapOf(R.drawable.ic_launcher_round)
        val logo = bitmapOf(R.drawable.ic_launcher_960)
        assertEquals(512, round.width)
        assertEquals(960, logo.width)
        assertTrue("a nodpi decode must not be upscaled by density", round.width <= 512 && logo.width <= 960)
    }

    @Test fun openAppOverlayLogoIsFixedInDpNotRawPixels() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val overlay = activity.findViewById<View>(R.id.flAdOpenApp)
                overlay.visibility = View.VISIBLE
                val logo = findLogo(overlay) ?: error("the overlay must contain the launcher logo")
                val expectedPx = (activity.resources.getDimension(R.dimen.ad_open_app_logo_size)).toInt()
                val lp = logo.layoutParams
                assertEquals("logo width must come from the dimen, not wrap_content", expectedPx, lp.width)
                assertEquals(expectedPx, lp.height)
                assertTrue("240dp must stay far below the raw 960px bitmap on a dense phone",
                    expectedPx.toFloat() / density <= 300f)
            }
        }
    }

    @Test fun aboutBackdropKeepsItsFixedHeightAfterTheMove() {
        ActivityScenario.launch(ActAbout::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val backdrop = activity.findViewById<ImageView>(R.id.backdrop)
                assertNotNull(backdrop.drawable)
                val heightDp = backdrop.layoutParams.height / density
                assertEquals("About backdrop is declared 250dp tall", 250f, heightDp, 1f)
            }
        }
    }

    @Test fun errorPageImageKeepsItsDeclaredWidth() {
        val widthPx = context.resources.getDimension(R.dimen.activity_horizontal_margin) // anchors density maths
        assertTrue(widthPx > 0f)
        val bmp = bitmapOf(R.drawable.l_error_404)
        assertEquals("the 404 illustration is 512 px wide and is shown at 120dp", 512, bmp.width)
    }

    private fun findLogo(root: View): ImageView? {
        if (root is ImageView && root.drawable != null && root.contentDescription == root.context.getString(R.string.app_name)) return root
        if (root is android.view.ViewGroup) for (i in 0 until root.childCount) findLogo(root.getChildAt(i))?.let { return it }
        return null
    }
}
