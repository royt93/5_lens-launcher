package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-007 widget proof on a bare LensView drawn into a software (Bitmap) canvas - i.e. the
 * non-RenderEffect ALPHA fallback path. The hardware BLUR path is covered by
 * LensViewDepthOfFieldIntegrationTest on a real attached window.
 */
@RunWith(AndroidJUnit4::class)
class LensViewDepthOfFieldWidgetTest {

    private companion object {
        const val SIZE_PX = 800
        const val APP_COUNT = 25
        const val ICON_PX = 48
        const val PARTIAL_ANIMATION = 0.3f
        const val FULL_ANIMATION = 1f
        const val EPS = 1e-4f
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val iconBitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
    private lateinit var lensView: LensView // assigned in setUp before every test

    @Before
    fun setUp() {
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish007.app$i", name = "App$i", label = "App $i", iconCacheKey = "fish007#$i")
        })
        apps.forEach { RAppsSingleton.instance.setAppIcon(it.iconCacheKey, iconBitmap) }
        instrumentation.runOnMainSync {
            lensView = LensView(context).apply {
                layout(0, 0, SIZE_PX, SIZE_PX)
                setApps(apps)
            }
        }
    }

    @After
    fun tearDown() {
        RAppsSingleton.instance.clearAllData()
    }

    private fun render(): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync { lensView.draw(Canvas(bitmap)) }
        return bitmap
    }

    private fun alphaSum(bitmap: Bitmap): Long {
        val pixels = IntArray(SIZE_PX * SIZE_PX)
        bitmap.getPixels(pixels, 0, SIZE_PX, 0, 0, SIZE_PX, SIZE_PX)
        bitmap.recycle()
        return pixels.sumOf { Color.alpha(it).toLong() }
    }

    @Test
    fun defaultIsOff_newViewDrawsEveryIconSharp() {
        lensView.setLensStateForTest(0f, 0f, FULL_ANIMATION, reduceMotion = false)
        render().recycle()
        assertEquals(DepthOfField.Mode.OFF, lensView.dofLastMode)
        assertEquals(APP_COUNT, lensView.dofBandCounts[0])
    }

    @Test
    fun softwareCanvas_usesAlphaFallback_andFadesFarIcons() {
        lensView.setLensStateForTest(0f, 0f, FULL_ANIMATION, reduceMotion = false)
        lensView.depthOfFieldEnabled = false
        val sharpAlpha = alphaSum(render())
        lensView.depthOfFieldEnabled = true
        val dofAlpha = alphaSum(render())

        assertEquals(DepthOfField.Mode.ALPHA, lensView.dofLastMode)
        assertTrue("some icons must be in a blurred band", lensView.dofBandCounts.drop(1).sum() > 0)
        assertTrue("fallback must visibly fade far icons ($dofAlpha vs $sharpAlpha)", dofAlpha < sharpAlpha)
    }

    @Test
    fun paintAlphaIsRestored_soANextFrameWithoutDofIsFullyOpaque() {
        lensView.setLensStateForTest(0f, 0f, FULL_ANIMATION, reduceMotion = false)
        lensView.depthOfFieldEnabled = false
        val before = alphaSum(render())
        lensView.depthOfFieldEnabled = true
        render().recycle()
        lensView.depthOfFieldEnabled = false
        assertEquals(before, alphaSum(render()))
    }

    @Test
    fun lensHidden_noBlurAtAll() {
        lensView.depthOfFieldEnabled = true
        lensView.setLensStateForTest(-Float.MAX_VALUE, -Float.MAX_VALUE, 0f, reduceMotion = false)
        render().recycle()
        assertEquals(DepthOfField.Mode.OFF, lensView.dofLastMode)
        assertEquals(APP_COUNT, lensView.dofBandCounts[0])
    }

    @Test
    fun reducedMotion_blurSnapsInstead_ofFollowingTheLensAnimation() {
        lensView.depthOfFieldEnabled = true
        lensView.setLensStateForTest(0f, 0f, PARTIAL_ANIMATION, reduceMotion = true)
        render().recycle()
        assertEquals("no intermediate transition under reduced motion", 1f, lensView.dofLastTransition, EPS)
        val snapped = lensView.dofBandCounts.copyOf()

        lensView.setLensStateForTest(0f, 0f, PARTIAL_ANIMATION, reduceMotion = false)
        render().recycle()
        assertEquals(PARTIAL_ANIMATION, lensView.dofLastTransition, EPS)
        assertNotEquals("normal motion eases blur in", snapped.toList(), lensView.dofBandCounts.toList())
    }

    @Test
    fun hitTest_isIdenticalWithAndWithoutDepthOfField_atEveryProbePoint() {
        val step = SIZE_PX / 8
        for (x in step until SIZE_PX step step) for (y in step until SIZE_PX step step) {
            lensView.setLensStateForTest(x.toFloat(), y.toFloat(), FULL_ANIMATION, reduceMotion = false)
            lensView.depthOfFieldEnabled = false
            render().recycle()
            val sharpIndex = lensView.selectedIndexForTest
            val sharpRect = lensView.selectedRectForTest()
            lensView.depthOfFieldEnabled = true
            render().recycle()
            assertEquals("index at ($x,$y)", sharpIndex, lensView.selectedIndexForTest)
            assertArrayEquals(
                "rect at ($x,$y)",
                sharpRect?.let { floatArrayOf(it.left, it.top, it.right, it.bottom) },
                lensView.selectedRectForTest()?.let { floatArrayOf(it.left, it.top, it.right, it.bottom) },
                EPS
            )
        }
    }
}
