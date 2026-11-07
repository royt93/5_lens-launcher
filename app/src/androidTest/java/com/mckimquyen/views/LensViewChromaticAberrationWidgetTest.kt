package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-040 touch-path proof on a bare LensView drawn into a software canvas (the DoF ALPHA fallback path). */
@RunWith(AndroidJUnit4::class)
class LensViewChromaticAberrationWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SIZE_PX = 800
        const val APP_COUNT = 40
        const val ICON_PX = 48
        const val PARTIAL_ANIMATION = 0.3f
        const val FULL_ANIMATION = 1f
        const val IDLE = -Float.MAX_VALUE
        const val OPAQUE = 255
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    /**
     * Pure white: it has all three channels, so the red-only and blue-only copies are visible. (A pure green icon would
     * be a bad probe: the red-only copy of green is black, and adding black changes nothing.)
     */
    private val iconBitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
    private lateinit var lensView: LensView // assigned in setUp before every test

    @Before
    fun setUp() {
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish040.app$i", name = "App$i", label = "App $i", iconCacheKey = "fish040#$i")
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

    private fun touchAt(x: Float, y: Float, animation: Float = FULL_ANIMATION, reduceMotion: Boolean = false) =
        lensView.setLensStateForTest(x, y, animation, reduceMotion)

    /** Centre of a real cell; the first render computes the grid cache. */
    private fun cellCentre(index: Int): Pair<Float, Float> {
        render().recycle()
        val bounds = Rect()
        var found = false
        instrumentation.runOnMainSync { found = lensView.getAppBounds(index, bounds) }
        assertTrue("cell $index must exist", found)
        return bounds.exactCenterX() to bounds.exactCenterY()
    }

    private fun touchAtCell(index: Int, animation: Float = FULL_ANIMATION, reduceMotion: Boolean = false) {
        val (x, y) = cellCentre(index)
        touchAt(x, y, animation, reduceMotion)
    }

    /**
     * The probe icon is white, so away from the original footprint the red-only copy paints pure red and the blue-only
     * copy pure blue; inside the footprint ADD saturates to white. A fringe pixel is therefore a strongly red (or blue)
     * pixel with the other two channels low.
     */
    private fun isRedFringe(c: Int) = Color.alpha(c) > 100 && Color.red(c) > 200 && Color.green(c) < 60 && Color.blue(c) < 60

    private fun isBlueFringe(c: Int) = Color.alpha(c) > 100 && Color.blue(c) > 200 && Color.red(c) < 60 && Color.green(c) < 60

    private fun pixelsOf(bitmap: Bitmap): IntArray {
        val pixels = IntArray(SIZE_PX * SIZE_PX)
        bitmap.getPixels(pixels, 0, SIZE_PX, 0, 0, SIZE_PX, SIZE_PX)
        bitmap.recycle()
        return pixels
    }

    private fun countReddish(bitmap: Bitmap): Int = pixelsOf(bitmap).count { isRedFringe(it) }

    private fun countBluish(bitmap: Bitmap): Int = pixelsOf(bitmap).count { isBlueFringe(it) }

    @Test fun defaultIsOffSoNoIconIsFringed() {
        touchAtCell(0)
        render().recycle()
        assertEquals(0, lensView.chromaIconsForTest)
        assertFalse(lensView.chromaticAberrationEnabled)
    }

    @Test fun withTheSettingOnAtMostFourFarthestOutsideZoneIconsAreFringed() {
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0)
        render().recycle()
        val n = lensView.chromaIconsForTest
        assertTrue("some far icons are fringed, was $n", n >= 1)
        assertTrue("never more than the cap, was $n", n <= ChromaticAberration.MAX_ICONS)
        assertEquals(n, lensView.chromaIndicesForTest.size)
    }

    @Test fun theFringedIconsAreTheFarthestOnesFromTheTouch() {
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0)                                   // finger on the first cell: far cells are the last ones
        render().recycle()
        val fringed = lensView.chromaIndicesForTest.toList()
        assertTrue(fringed.isNotEmpty())
        assertFalse("the icon under the finger is never fringed", 0 in fringed)
        assertTrue("fringed icons come from the far half of the grid, was $fringed", fringed.all { it >= APP_COUNT / 2 })
    }

    @Test fun aSharpIconIsExcludedEvenWhenTheFarthestCapHasUnusedSlots() {
        val two = ArrayList(lensView.displayedApps.take(2))
        instrumentation.runOnMainSync { lensView.setApps(two) }
        render().recycle()                            // realize the two-cell grid
        val sharpBounds = Rect()
        var hasBounds = false
        instrumentation.runOnMainSync { hasBounds = lensView.getAppBounds(0, sharpBounds) }
        assertTrue(hasBounds)
        val x = sharpBounds.exactCenterX()
        val y = sharpBounds.exactCenterY()
        val sharpDistance = DepthOfField.focusDistance(x, y, x, y, SIZE_PX.toFloat(), SIZE_PX.toFloat())
        assertTrue("the touched icon is inside the sharp zone ($sharpDistance)", sharpDistance <= DepthOfField.FOCUS_ZONE)
        lensView.chromaticAberrationEnabled = true
        touchAt(x, y)
        render().recycle()
        assertTrue("the far second icon should be fringed", lensView.chromaIconsForTest >= 1)
        assertFalse("sharp cell 0 must not be fringed even though the 4-slot cap has room", 0 in lensView.chromaIndicesForTest)
        assertTrue("the cap has unused capacity", lensView.chromaIconsForTest < ChromaticAberration.MAX_ICONS)
    }

    @Test fun redAndBlueFringePixelsAppearWhenEnabledAndNotWhenDisabled() {
        touchAtCell(0)
        val offRed = countReddish(render())
        val offBlue = countBluish(render())
        lensView.chromaticAberrationEnabled = true
        val onRed = countReddish(render())
        val onBlue = countBluish(render())
        assertTrue("red fringe adds pixels ($onRed vs $offRed)", onRed > offRed)
        assertTrue("blue fringe adds pixels ($onBlue vs $offBlue)", onBlue > offBlue)
    }

    @Test fun theOffsetStaysWithinOneToThreeDpOfTheDensity() {
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0)
        render().recycle()
        val density = context.resources.displayMetrics.density
        val px = lensView.chromaOffsetPxForTest
        assertTrue("offset $px >= 1dp", px >= ChromaticAberration.MIN_OFFSET_DP * density - 0.01f)
        assertTrue("offset $px <= 3dp", px <= ChromaticAberration.MAX_OFFSET_DP * density + 0.01f)
    }

    /**
     * Direction proof on one far icon: the red copy lies on the side AWAY from the touch and the blue copy on the side
     * TOWARD it. The icon is pure green, so a pixel that has gained red (or blue) is a fringe pixel; it is classified by
     * which side of the icon's drawn centre it lies on, along the touch-to-icon axis.
     */
    @Test fun theRedFringeLiesOutwardAndTheBlueFringeInwardAlongTheTouchAxis() {
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0)
        val (touchX, touchY) = cellCentre(0)
        render().recycle()
        val farCell = lensView.chromaIndicesForTest.maxOrNull() ?: error("no icon was fringed")
        val rect = lensView.drawnRectsForTest()[farCell]
        val dx = rect.centerX() - touchX
        val dy = rect.centerY() - touchY
        val length = kotlin.math.hypot(dx, dy)
        assertTrue("far icon is away from the touch", length > 1f)
        val ux = dx / length
        val uy = dy / length
        val bitmap = render()
        val pixels = IntArray(SIZE_PX * SIZE_PX)
        bitmap.getPixels(pixels, 0, SIZE_PX, 0, 0, SIZE_PX, SIZE_PX)
        bitmap.recycle()
        var redOutward = 0; var redInward = 0; var blueOutward = 0; var blueInward = 0
        val x0 = (rect.left - 12f).toInt().coerceAtLeast(0); val x1 = (rect.right + 12f).toInt().coerceAtMost(SIZE_PX - 1)
        val y0 = (rect.top - 12f).toInt().coerceAtLeast(0); val y1 = (rect.bottom + 12f).toInt().coerceAtMost(SIZE_PX - 1)
        for (y in y0..y1) for (x in x0..x1) {
            val c = pixels[y * SIZE_PX + x]
            val side = (x - rect.centerX()) * ux + (y - rect.centerY()) * uy      // > 0 means away from the touch
            if (isRedFringe(c)) { if (side > 0) redOutward++ else redInward++ }
            if (isBlueFringe(c)) { if (side > 0) blueOutward++ else blueInward++ }
        }
        assertTrue("red lies outward ($redOutward out vs $redInward in)", redOutward > redInward)
        assertTrue("blue lies inward ($blueInward in vs $blueOutward out)", blueInward > blueOutward)
    }

    @Test fun anIdleLensDrawsNoFringeEvenWhenEnabled() {
        lensView.chromaticAberrationEnabled = true
        touchAt(IDLE, IDLE, animation = 0f)
        render().recycle()
        assertEquals(0, lensView.chromaIconsForTest)
    }

    /** The lens fades out after release: the touch is gone but the animation multiplier is still above zero. */
    @Test fun aReleasedTouchWithALingeringAnimationDrawsNoFringe() {
        lensView.chromaticAberrationEnabled = true
        touchAt(IDLE, IDLE, animation = PARTIAL_ANIMATION)
        render().recycle()
        assertEquals("no live touch, no fringe, even while the lens still fades", 0, lensView.chromaIconsForTest)
    }

    /**
     * ADD never leaves a pure-channel pixel on top of the white original: red + white stays white. A SRC_OVER copy would
     * paint pure red/blue across the icon's own footprint, where the shifted copies overlap it.
     */
    @Test fun theFringeIsAddedSoNoPureChannelPixelCoversTheIconItself() {
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0)
        render().recycle()
        val farCell = lensView.chromaIndicesForTest.maxOrNull() ?: error("no icon was fringed")
        val rect = lensView.drawnRectsForTest()[farCell]
        val pixels = pixelsOf(render())
        var inside = 0
        var pureChannel = 0
        for (y in (rect.top + 1f).toInt()..(rect.bottom - 1f).toInt()) {
            for (x in (rect.left + 1f).toInt()..(rect.right - 1f).toInt()) {
                inside++
                val c = pixels[y * SIZE_PX + x]
                if (isRedFringe(c) || isBlueFringe(c)) pureChannel++
            }
        }
        assertTrue("footprint is large enough to judge ($inside px, rect $rect)", inside > 0)
        assertEquals("no pure red/blue pixel may sit on the icon ($pureChannel of $inside)", 0, pureChannel)
    }

    @Test fun aTouchWhoseTransitionIsStillZeroDrawsNothingYet() {
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0, animation = 0f)
        render().recycle()
        assertEquals(0, lensView.chromaIconsForTest)
        assertEquals(0f, lensView.chromaOffsetPxForTest, 0f)
    }

    @Test fun theOffsetFadesWithTheLensAndSnapsUnderReducedMotion() {
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0, animation = PARTIAL_ANIMATION)
        render().recycle()
        val eased = lensView.chromaOffsetPxForTest
        touchAtCell(0, animation = PARTIAL_ANIMATION, reduceMotion = true)
        render().recycle()
        val snapped = lensView.chromaOffsetPxForTest
        assertTrue("snapped offset $snapped is larger than the eased one $eased", snapped > eased)
    }

    @Test fun cleanLensModeDoesNotHideTheEffect() {
        UtilSettings(context).save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0)
        render().recycle()
        assertTrue(lensView.chromaIconsForTest >= 1)
    }

    @Test fun theFringeAlphaIsTheIconAlphaNeverTheProductWithTheVignette() {
        lensView.chromaticAberrationEnabled = true
        lensView.lensVignetteEnabled = false
        touchAtCell(0)
        render().recycle()
        assertEquals("without the vignette the fringe is opaque", OPAQUE, lensView.chromaAlphaForTest)
        lensView.lensVignetteEnabled = true
        render().recycle()
        val withVignette = lensView.chromaAlphaForTest
        assertTrue("the vignette lowers the fringe alpha ($withVignette)", withVignette < OPAQUE)
        assertTrue("never below the vignette floor", withVignette >= LensVignette.alpha(1f))
    }

    @Test fun geometryHitTestAndGridCacheAreIdenticalWithAndWithoutTheEffect() {
        val field = LensView::class.java.getDeclaredField("mGridCache").apply { isAccessible = true }
        val cache = field.get(lensView) as LensGridCache
        val step = SIZE_PX / 8
        for (x in step until SIZE_PX step step) for (y in step until SIZE_PX step step) {
            touchAt(x.toFloat(), y.toFloat())
            lensView.chromaticAberrationEnabled = false
            render().recycle()
            val rectsOff = lensView.drawnRectsForTest()
            val indexOff = lensView.selectedIndexForTest
            val touchOff = lensView.touchCoordinatesForTest()
            val recomputes = cache.recomputeCount
            lensView.chromaticAberrationEnabled = true
            render().recycle()
            assertEquals(rectsOff, lensView.drawnRectsForTest())
            assertEquals(indexOff, lensView.selectedIndexForTest)
            assertEquals(touchOff, lensView.touchCoordinatesForTest())
            assertEquals(recomputes, cache.recomputeCount)
        }
    }

    @Test fun afterATouchedFrameAnIdleFrameDrawsNoFringe() {
        lensView.chromaticAberrationEnabled = true
        touchAtCell(0)
        render().recycle()
        assertTrue(lensView.chromaIconsForTest >= 1)
        touchAt(IDLE, IDLE, animation = 0f)
        render().recycle()
        assertEquals(0, lensView.chromaIconsForTest)
    }

    @Test fun theIconPaintAlphaIsRestoredAfterAFringedFrame() {
        touchAtCell(0)
        lensView.chromaticAberrationEnabled = false
        val before = countOpaqueWhite(render())
        lensView.chromaticAberrationEnabled = true
        render().recycle()
        lensView.chromaticAberrationEnabled = false
        assertEquals(before, countOpaqueWhite(render()))
    }

    private fun countOpaqueWhite(bitmap: Bitmap): Int =
        pixelsOf(bitmap).count { Color.alpha(it) > 240 && Color.red(it) > 240 && Color.green(it) > 240 && Color.blue(it) > 240 }

    private fun countOpaqueBlack(bitmap: Bitmap): Int =
        pixelsOf(bitmap).count { Color.alpha(it) > 200 && Color.red(it) < 8 && Color.green(it) < 8 && Color.blue(it) < 8 }

    /**
     * A red-only copy of a green icon is black. Drawn with ADD onto a transparent layer, a black source would still make
     * an OPAQUE black pixel, i.e. a black halo around every icon that lacks the red (or blue) channel. The fringe must
     * stay invisible where the icon has none of that channel.
     */
    @Test fun aFringeNeverPaintsOpaqueBlackAroundAnIconThatLacksThatChannel() {
        val green = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        (0 until APP_COUNT).forEach { RAppsSingleton.instance.setAppIcon("fish040#$it", green) }
        touchAtCell(0)
        lensView.chromaticAberrationEnabled = false
        val off = countOpaqueBlack(render())
        lensView.chromaticAberrationEnabled = true
        val on = countOpaqueBlack(render())
        assertTrue("fringes were drawn for the green icons", lensView.chromaIconsForTest >= 1)
        assertEquals("no black halo may appear ($on opaque black pixels vs $off without the effect)", off, on)
    }
}
