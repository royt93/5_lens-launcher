package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-039 touch-path proof on a bare LensView drawn into a software canvas (the DoF ALPHA fallback path). */
@RunWith(AndroidJUnit4::class)
class LensViewFocusPeakingWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SIZE_PX = 800
        const val APP_COUNT = 25
        const val ICON_PX = 48
        const val PARTIAL_ANIMATION = 0.3f
        const val FULL_ANIMATION = 1f
        const val OPAQUE = 255
        const val IDLE = -Float.MAX_VALUE
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val iconBitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
    private lateinit var lensView: LensView // assigned in setUp before every test

    @Before
    fun setUp() {
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish039.app$i", name = "App$i", label = "App $i", iconCacheKey = "fish039#$i")
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

    private fun highlightPixels(bitmap: Bitmap): Int {
        val highlight = UtilSettings(context).highlightColorInt() or (OPAQUE shl 24)
        val pixels = IntArray(SIZE_PX * SIZE_PX)
        bitmap.getPixels(pixels, 0, SIZE_PX, 0, 0, SIZE_PX, SIZE_PX)
        bitmap.recycle()
        return pixels.count { it == highlight }
    }

    private fun touchAt(x: Float, y: Float, animation: Float = FULL_ANIMATION, reduceMotion: Boolean = false) =
        lensView.setLensStateForTest(x, y, animation, reduceMotion)

    /** Centre of a real cell (never a gap between icons); the first render computes the grid cache. */
    private fun cellCentre(index: Int): Pair<Float, Float> {
        render().recycle()
        val bounds = Rect()
        var found = false
        instrumentation.runOnMainSync { found = lensView.getAppBounds(index, bounds) }
        assertTrue("cell $index must exist", found)
        return bounds.exactCenterX() to bounds.exactCenterY()
    }

    private fun touchAtCell(index: Int = APP_COUNT / 2, animation: Float = FULL_ANIMATION, reduceMotion: Boolean = false) {
        val (x, y) = cellCentre(index)
        touchAt(x, y, animation, reduceMotion)
    }

    @Test fun defaultIsOffSoNoRingIsDrawn() {
        touchAtCell()
        render().recycle()
        assertEquals(0, lensView.focusPeakingRingsForTest)
        assertEquals(false, lensView.focusPeakingEnabled)
    }

    @Test fun withTheSettingOnOnlyTheSharpClusterUnderTheFingerIsOutlined() {
        lensView.focusPeakingEnabled = true
        touchAtCell()
        render().recycle()
        val rings = lensView.focusPeakingRingsForTest
        assertTrue("at least the icon under the finger is outlined, was $rings", rings >= 1)
        assertTrue("far icons are not outlined ($rings of $APP_COUNT)", rings < APP_COUNT)
    }

    @Test fun aCornerCellTouchOutlinesOnlyTheIconsNearThatCorner() {
        lensView.focusPeakingEnabled = true
        touchAtCell(0)
        render().recycle()
        val corner = lensView.focusPeakingRingsForTest
        assertTrue("corner cluster is small but not empty, was $corner", corner in 1 until APP_COUNT / 2)
    }

    @Test fun ringsAreActuallyPaintedInTheHighlightColour() {
        touchAtCell()
        val off = highlightPixels(render())
        lensView.focusPeakingEnabled = true
        val on = highlightPixels(render())
        assertTrue("the 4dp outline adds highlight-coloured pixels ($on vs $off)", on > off)
    }

    @Test fun theOutlineIsThickerThanTheThinFocusHalo() {
        val density = context.resources.displayMetrics.density
        assertEquals(FocusPeaking.STROKE_DP * density, lensView.focusPeakingStrokePxForTest(), 0.01f)
        assertTrue(lensView.focusPeakingStrokePxForTest() > context.resources.getDimension(R.dimen.stroke_width_focus_halo))
    }

    @Test fun anIdleLensDrawsNoRingEvenWhenEnabled() {
        lensView.focusPeakingEnabled = true
        touchAt(IDLE, IDLE, animation = 0f)
        render().recycle()
        assertEquals(0, lensView.focusPeakingRingsForTest)
    }

    @Test fun aTouchWhoseTransitionIsStillZeroDrawsNothingYet() {
        lensView.focusPeakingEnabled = true
        touchAtCell(animation = 0f)
        render().recycle()
        assertEquals(0, lensView.focusPeakingAlphaForTest)
        assertEquals(0, lensView.focusPeakingRingsForTest)
    }

    @Test fun theOutlineFadesWithTheLensAndSnapsUnderReducedMotion() {
        lensView.focusPeakingEnabled = true
        touchAtCell(animation = PARTIAL_ANIMATION)
        render().recycle()
        assertEquals(FocusPeaking.alpha(pinnedOnly = false, transition = PARTIAL_ANIMATION), lensView.focusPeakingAlphaForTest)
        touchAtCell(animation = PARTIAL_ANIMATION, reduceMotion = true)
        render().recycle()
        assertEquals("reduced motion snaps to full", OPAQUE, lensView.focusPeakingAlphaForTest)
    }

    @Test fun cleanLensModeDoesNotHideTheAccessibilityAid() {
        UtilSettings(context).save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        lensView.focusPeakingEnabled = true
        touchAtCell()
        render().recycle()
        assertTrue(lensView.focusPeakingRingsForTest >= 1)
    }

    @Test fun depthOfFieldOnOrOffTheSameClusterIsOutlined() {
        lensView.focusPeakingEnabled = true
        touchAtCell()
        lensView.depthOfFieldEnabled = false
        render().recycle()
        val without = lensView.focusPeakingRingsForTest
        lensView.depthOfFieldEnabled = true
        render().recycle()
        assertEquals(without, lensView.focusPeakingRingsForTest)
        assertTrue(without >= 1)
    }

    @Test fun geometryAndHitTestAreIdenticalWithAndWithoutTheOutline() {
        val step = SIZE_PX / 8
        for (x in step until SIZE_PX step step) for (y in step until SIZE_PX step step) {
            touchAt(x.toFloat(), y.toFloat())
            lensView.focusPeakingEnabled = false
            render().recycle()
            val rectsOff = lensView.drawnRectsForTest()
            val indexOff = lensView.selectedIndexForTest
            val touchOff = lensView.touchCoordinatesForTest()
            lensView.focusPeakingEnabled = true
            render().recycle()
            assertEquals(rectsOff, lensView.drawnRectsForTest())
            assertEquals(indexOff, lensView.selectedIndexForTest)
            assertEquals(touchOff, lensView.touchCoordinatesForTest())
        }
    }

    @Test fun theGridCacheIsNotRecomputedByOutlineFrames() {
        val field = LensView::class.java.getDeclaredField("mGridCache").apply { isAccessible = true }
        val cache = field.get(lensView) as LensGridCache
        touchAtCell()
        lensView.focusPeakingEnabled = false
        render().recycle()
        val recomputes = cache.recomputeCount
        lensView.focusPeakingEnabled = true
        repeat(5) { render().recycle() }
        assertEquals(recomputes, cache.recomputeCount)
    }

    @Test fun afterATouchedFrameAnIdleFrameDrawsNoRing() {
        lensView.focusPeakingEnabled = true
        touchAtCell()
        render().recycle()
        assertTrue(lensView.focusPeakingRingsForTest >= 1)
        touchAt(IDLE, IDLE, animation = 0f)
        render().recycle()
        assertEquals(0, lensView.focusPeakingRingsForTest)
    }
}
