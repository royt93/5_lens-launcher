package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-041 touch-path proof on a bare LensView drawn into a software canvas. */
@RunWith(AndroidJUnit4::class)
class LensViewInertiaWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SIZE_PX = 800
        const val APP_COUNT = 40
        const val FULL_ANIMATION = 1f
        const val IDLE = -Float.MAX_VALUE
        const val FAR = 700f
        const val EPS = 0.01f
        const val ICON_PX = 48
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var lensView: LensView // assigned in setUp before every test

    @Before
    fun setUp() {
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish041.app$i", name = "App$i", label = "App $i", iconCacheKey = "fish041#$i")
        })
        // The fringe pass skips an icon without a cached bitmap, so give every app a real one.
        val icon = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        apps.forEach { RAppsSingleton.instance.setAppIcon(it.iconCacheKey, icon) }
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

    private fun render() {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync { lensView.draw(Canvas(bitmap)) }
        bitmap.recycle()
    }

    private fun cellCentre(index: Int): Pair<Float, Float> {
        render()
        val bounds = Rect()
        var found = false
        instrumentation.runOnMainSync { found = lensView.getAppBounds(index, bounds) }
        assertTrue("cell $index must exist", found)
        return bounds.exactCenterX() to bounds.exactCenterY()
    }

    private fun touchAt(x: Float, y: Float, animation: Float = FULL_ANIMATION, reduceMotion: Boolean = false) =
        lensView.setLensStateForTest(x, y, animation, reduceMotion)

    private fun inertia(enabled: Boolean, panning: Boolean, vx: Float, vy: Float) =
        lensView.setInertiaStateForTest(enabled, panning, vx, vy)

    private fun rects(): List<RectF> = lensView.drawnRectsForTest().map { RectF(it) }

    /** The same finger position, drawn once with the effect off: the reference every case compares against. */
    private fun baseline(x: Float, y: Float): Pair<List<RectF>, Int> {
        inertia(false, true, x, y)
        touchAt(x, y)
        render()
        return rects() to lensView.selectedIndexForTest
    }

    @Test fun theSelectedIconStaysTheOneUnderTheFingerEvenWhenTheDrawnCentreLags() {
        val (x, y) = cellCentre(0)
        val (offRects, offIndex) = baseline(x, y)
        assertTrue("the finger must be on an icon", offIndex >= 0)
        inertia(true, true, FAR, FAR)
        touchAt(x, y)
        render()
        assertEquals("selection follows the finger, not the lagged centre", offIndex, lensView.selectedIndexForTest)
        assertNotEquals("the drawn rects did move to the lagged centre", offRects[offIndex], rects()[offIndex])
    }

    @Test fun theSelectionRectIsTheDrawnRectOfTheSelectedIcon() {
        val (x, y) = cellCentre(0)
        val (_, offIndex) = baseline(x, y)
        inertia(true, true, FAR, FAR)
        touchAt(x, y)
        render()
        val selected = requireNotNull(lensView.selectedRectForTest()) { "an icon must be selected" }
        val drawn = rects()[offIndex]
        assertEquals(drawn.left, selected.left, EPS); assertEquals(drawn.top, selected.top, EPS)
        assertEquals(drawn.right, selected.right, EPS); assertEquals(drawn.bottom, selected.bottom, EPS)
    }

    @Test fun whenTheCentresCoincideTheRectsAreIdenticalToTheEffectOff() {
        val (x, y) = cellCentre(0)
        val (offRects, _) = baseline(x, y)
        inertia(true, true, x, y)
        touchAt(x, y)
        render()
        offRects.zip(rects()).forEachIndexed { i, (a, b) -> assertEquals("cell $i", a, b) }
    }

    @Test fun theEffectIsOffByDefault() {
        assertFalse(lensView.lensInertiaEnabled)
        val (x, y) = cellCentre(0)
        val (offRects, _) = baseline(x, y)
        inertia(false, true, FAR, FAR)
        touchAt(x, y)
        render()
        offRects.zip(rects()).forEachIndexed { i, (a, b) -> assertEquals("cell $i", a, b) }
    }

    @Test fun withoutARealPanTheCentreSnapsToTheFinger() {
        val (x, y) = cellCentre(0)
        val (offRects, _) = baseline(x, y)
        inertia(true, false, FAR, FAR)
        touchAt(x, y)
        render()
        offRects.zip(rects()).forEachIndexed { i, (a, b) -> assertEquals("cell $i", a, b) }
        assertEquals(x, lensView.visualCentreForTest().first, EPS)
        assertEquals(y, lensView.visualCentreForTest().second, EPS)
    }

    @Test fun reducedMotionNeverLags() {
        val (x, y) = cellCentre(0)
        inertia(false, true, x, y)
        touchAt(x, y, reduceMotion = true)
        render()
        val offRects = rects()
        inertia(true, true, FAR, FAR)
        touchAt(x, y, reduceMotion = true)
        render()
        offRects.zip(rects()).forEachIndexed { i, (a, b) -> assertEquals("cell $i", a, b) }
    }

    @Test fun anIdleLensIsUnaffected() {
        touchAt(IDLE, IDLE, animation = 0f)
        render()
        val offRects = rects()
        inertia(true, true, FAR, FAR)
        touchAt(IDLE, IDLE, animation = 0f)
        render()
        offRects.zip(rects()).forEachIndexed { i, (a, b) -> assertEquals("cell $i", a, b) }
    }

    @Test fun theVisualCentreStepsTowardsTheFingerOnTheNextFrames() {
        val (x, y) = cellCentre(0)
        inertia(true, true, FAR, FAR)
        touchAt(x, y)
        render()                                    // first frame: dt is 0, the centre has not moved yet
        val first = lensView.visualCentreForTest()
        Thread.sleep(40)
        render()
        val second = lensView.visualCentreForTest()
        assertEquals(FAR, first.first, EPS)
        assertTrue("moved towards the finger: ${second.first} < ${first.first}", second.first < first.first)
        assertTrue("never past the finger", second.first > x)
    }

    /**
     * The optical effects follow the LAGGED centre: with the vignette on, the icon the finger is on stays sharp while
     * the icon the lagged centre sits on is the one that is not darkened. Comparing the per-icon alpha through the
     * fringe probe is not possible here, so the distance itself is the observable: the far-icon set moves with the lag.
     */
    @Test fun theOpticalDistanceFollowsTheLaggedCentreNotTheFinger() {
        lensView.chromaticAberrationEnabled = true
        val (x, y) = cellCentre(0)
        inertia(false, true, x, y)
        touchAt(x, y)
        render()
        val offSet = lensView.chromaIndicesForTest.toSet()
        assertTrue("the fringe set is computed with the effect off", offSet.isNotEmpty())
        inertia(true, true, FAR, FAR)                  // lagged centre far in the opposite corner
        touchAt(x, y)
        render()
        val onSet = lensView.chromaIndicesForTest.toSet()
        assertTrue("the fringed (farthest) icons move with the lagged centre: off=$offSet on=$onSet", offSet != onSet)
    }

    @Test fun geometryAndGridCacheAreIdenticalWithAndWithoutTheEffect() {
        val field = LensView::class.java.getDeclaredField("mGridCache").apply { isAccessible = true }
        val cache = field.get(lensView) as LensGridCache
        val (x, y) = cellCentre(0)
        baseline(x, y)
        val before = cache.baseRects.map { RectF(it) }
        inertia(true, true, FAR, FAR)
        touchAt(x, y)
        render()
        cache.baseRects.zip(before).forEachIndexed { i, (a, b) -> assertEquals("base rect $i", b, a) }
    }
}
