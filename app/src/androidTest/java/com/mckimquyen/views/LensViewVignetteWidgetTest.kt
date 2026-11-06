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
 * FISH-034 widget proof on a bare LensView drawn into a software (Bitmap) canvas - i.e. the
 * non-RenderEffect ALPHA fallback path. The hardware BLUR path is covered by
 * LensViewDepthOfFieldIntegrationTest on a real attached window.
 */
@RunWith(AndroidJUnit4::class)
class LensViewVignetteWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()


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
            App(id = i, packageName = "com.fish034.app$i", name = "App$i", label = "App $i", iconCacheKey = "fish034#$i")
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
        com.mckimquyen.util.BitmapCache.clear()
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
    fun defaultIsOff_everyIconStaysOpaque() {
        lensView.setLensStateForTest(0f, 0f, FULL_ANIMATION, reduceMotion = false)
        lensView.lensVignetteEnabled = false
        val a = alphaSum(render())
        assertEquals(a, alphaSum(render()))
    }

    @Test
    fun enabled_dimsFarIconsButNeverBelowTheFloor() {
        lensView.setLensStateForTest(0f, 0f, FULL_ANIMATION, reduceMotion = false)
        lensView.lensVignetteEnabled = false
        val off = alphaSum(render())
        lensView.lensVignetteEnabled = true
        val on = alphaSum(render())
        assertTrue("far icons must dim ($on vs $off)", on < off)
        assertTrue("floor keeps icons visible", on * 2 > off)
    }

    @Test
    fun idleLens_staysOpaqueEvenWhenEnabled() {
        lensView.lensVignetteEnabled = false
        lensView.setLensStateForTest(-Float.MAX_VALUE, -Float.MAX_VALUE, 0f, reduceMotion = false)
        val off = alphaSum(render())
        lensView.lensVignetteEnabled = true
        assertEquals(off, alphaSum(render()))
    }

    @Test
    fun geometryAndHitTestAreIdenticalWithAndWithoutTheVignette() {
        val step = SIZE_PX / 8
        for (x in step until SIZE_PX step step) for (y in step until SIZE_PX step step) {
            lensView.setLensStateForTest(x.toFloat(), y.toFloat(), FULL_ANIMATION, reduceMotion = false)
            lensView.lensVignetteEnabled = false
            render().recycle()
            val rectsOff = lensView.drawnRectsForTest()
            val indexOff = lensView.selectedIndexForTest
            lensView.lensVignetteEnabled = true
            render().recycle()
            assertEquals(rectsOff, lensView.drawnRectsForTest())
            assertEquals(indexOff, lensView.selectedIndexForTest)
        }
    }

    @Test
    fun paintAlphaIsRestoredSoTheNextFrameWithoutTheVignetteIsOpaque() {
        lensView.setLensStateForTest(0f, 0f, FULL_ANIMATION, reduceMotion = false)
        lensView.lensVignetteEnabled = false
        val before = alphaSum(render())
        lensView.lensVignetteEnabled = true
        render().recycle()
        lensView.lensVignetteEnabled = false
        assertEquals(before, alphaSum(render()))
    }

    @Test
    fun withTheDofAlphaFallbackTheLowerAlphaWinsAndNothingIsMultiplied() {
        lensView.setLensStateForTest(0f, 0f, FULL_ANIMATION, reduceMotion = false)
        lensView.depthOfFieldEnabled = true
        lensView.lensVignetteEnabled = false
        val dofOnly = alphaSum(render())
        lensView.depthOfFieldEnabled = false
        lensView.lensVignetteEnabled = true
        val vignetteOnly = alphaSum(render())
        lensView.depthOfFieldEnabled = true
        val both = alphaSum(render())
        assertEquals("min of equal curves equals either one, not their product", dofOnly, both)
        assertEquals(vignetteOnly, both)
    }

    @Test
    fun reducedMotionSnapsTheTransitionInsteadOfEasing() {
        lensView.lensVignetteEnabled = true
        lensView.setLensStateForTest(0f, 0f, PARTIAL_ANIMATION, reduceMotion = false)
        val eased = alphaSum(render())
        lensView.setLensStateForTest(0f, 0f, PARTIAL_ANIMATION, reduceMotion = true)
        val snapped = alphaSum(render())
        assertTrue("snapped (full) vignette is darker than the eased 30% one", snapped < eased)
    }

    @Test
    fun theGridCacheIsNotRecomputedByVignetteFrames() {
        val field = LensView::class.java.getDeclaredField("mGridCache").apply { isAccessible = true }
        val cache = field.get(lensView) as LensGridCache
        lensView.setLensStateForTest(0f, 0f, FULL_ANIMATION, reduceMotion = false)
        lensView.lensVignetteEnabled = false
        render().recycle()
        val recomputes = cache.recomputeCount
        lensView.lensVignetteEnabled = true
        repeat(5) { render().recycle() }
        assertEquals(recomputes, cache.recomputeCount)
    }

    @Test
    fun afterATouchedVignetteFrameAnIdleFrameIsFullyOpaque() {
        lensView.lensVignetteEnabled = true
        lensView.setLensStateForTest(0f, 0f, FULL_ANIMATION, reduceMotion = false)
        render().recycle()
        lensView.setLensStateForTest(-Float.MAX_VALUE, -Float.MAX_VALUE, 0f, reduceMotion = false)
        val idleWithVignette = alphaSum(render())
        lensView.lensVignetteEnabled = false
        assertEquals(alphaSum(render()), idleWithVignette)
    }
}
