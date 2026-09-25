package com.mckimquyen.util

import android.content.Context
import com.mckimquyen.enums.SortType
import com.mckimquyen.model.Grid
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilCalculatorTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun testCalculateGridBasic() {
        // Given
        val screenWidth = 1080
        val screenHeight = 1920
        val itemCount = 100
        val iconSizeDp = 24.0f

        // When
        val grid: Grid = UtilCalculator.calculateGrid(context, screenWidth, screenHeight, itemCount, iconSizeDp)

        // Then
        assertNotNull(grid)
        assertTrue(grid.itemCountHorizontal > 0)
        assertTrue(grid.itemCountVertical > 0)
        assertTrue(grid.itemSize > 0f)
        assertTrue(grid.spacingHorizontal >= 0f)
        assertTrue(grid.spacingVertical >= 0f)
    }

    @Test
    fun testOptimalSquareSizeCalculation() {
        // Given
        val width = 1000
        val height = 2000
        val count = 50

        // When
        val size = UtilCalculator.calculateOptimalSquareSize(width, height, count)

        // Then
        assertTrue(size > 0)
        assertTrue(size <= width)
        assertTrue(size <= height)
    }

    @Test
    fun testShiftPointInsideFisheyeLens() {
        // Given
        val lensPosition = 500f
        val itemPosition = 450f
        val boundary = 1000f
        val multiplier = 1.0f
        val distortionFactor = 2.5f

        // When
        val shifted = UtilCalculator.shiftPoint(lensPosition, itemPosition, boundary, multiplier, distortionFactor)

        // Then
        // The shifted position should move away or towards the lens center depending on factors.
        // For a lens center at 500f and item at 450f:
        // since lensPosition >= itemPosition, shifted value returns lensPosition - newDistanceFromCenter
        assertTrue(shifted >= 0f)
        assertTrue(shifted <= boundary)
    }

    @Test
    fun shouldReportFullyDrawn_firstNonEmptyContent_reports() {
        assertTrue(UtilCalculator.shouldReportFullyDrawn(false, true))
    }

    @Test
    fun shouldReportFullyDrawn_alreadyReported_doesNotReportAgain() {
        assertFalse(UtilCalculator.shouldReportFullyDrawn(true, true))
    }

    @Test
    fun shouldReportFullyDrawn_emptyColdStartFrame_doesNotReport() {
        assertFalse(UtilCalculator.shouldReportFullyDrawn(false, false))
    }

    @Test
    fun shouldReportFullyDrawn_alreadyReportedAndEmpty_doesNotReport() {
        assertFalse(UtilCalculator.shouldReportFullyDrawn(true, false))
    }

    @Test
    fun testScalePointInsideFisheyeLens() {
        // Given
        val lensPosition = 500f
        val itemPosition = 450f
        val itemSize = 100f
        val boundary = 1000f
        val multiplier = 1.0f
        val scaleFactor = 1.5f
        val distortionFactor = 2.5f

        // When
        val scaledSize = UtilCalculator.scalePoint(lensPosition, itemPosition, itemSize, boundary, multiplier, scaleFactor, distortionFactor)

        // Then
        assertTrue(scaledSize > 0f)
    }

    @Test
    fun testCalculateRectInPlaceMatchesAllocatingOverload() {
        // Given
        val centerX = 320f
        val centerY = 540f
        val size = 96f

        // When
        val allocated = UtilCalculator.calculateRect(centerX, centerY, size)
        val dest = android.graphics.RectF(1f, 2f, 3f, 4f) // pre-populated with unrelated values
        UtilCalculator.calculateRect(dest, centerX, centerY, size)

        // Then: PERF-001's in-place overload must produce bit-for-bit identical geometry to the
        // allocating one it replaces in LensView's hot path, only without the allocation.
        assertEquals(allocated.left, dest.left, 0.0001f)
        assertEquals(allocated.top, dest.top, 0.0001f)
        assertEquals(allocated.right, dest.right, 0.0001f)
        assertEquals(allocated.bottom, dest.bottom, 0.0001f)
    }

    @Test
    fun testCalculateRectInPlaceReusedAcrossCalls() {
        // Given a single reused RectF instance (as LensView's mScratchRect is)
        val dest = android.graphics.RectF()

        // When set for one cell then overwritten for another
        UtilCalculator.calculateRect(dest, 100f, 100f, 50f)
        val firstLeft = dest.left
        UtilCalculator.calculateRect(dest, 500f, 500f, 50f)

        // Then the second call's values fully replace the first's, nothing stale remains
        assertEquals(475f, dest.left, 0.0001f)
        assertTrue(dest.left != firstLeft)
    }

    @Test
    fun testShiftPointInvalidLensPosition() {
        // Given
        val lensPosition = -1f
        val itemPosition = 300f
        val boundary = 1000f
        val multiplier = 1.0f
        val distortionFactor = 2.5f

        // When
        val shifted = UtilCalculator.shiftPoint(lensPosition, itemPosition, boundary, multiplier, distortionFactor)

        // Then: should return original position
        assertEquals(itemPosition, shifted, 0.01f)
    }

    // UI-020: home_content_max_width clamp - a phone screen never engages it (0 extra margin),
    // a tablet/landscape screen wider than the cap gets exactly half the excess on each side.
    @Test
    fun `calculateContentMaxWidthMargin returns zero when screen is narrower than the cap`() {
        assertEquals(0, UtilCalculator.calculateContentMaxWidthMargin(1080, 2160))
    }

    @Test
    fun `calculateContentMaxWidthMargin returns zero when screen exactly equals the cap`() {
        assertEquals(0, UtilCalculator.calculateContentMaxWidthMargin(2160, 2160))
    }

    @Test
    fun `calculateContentMaxWidthMargin splits the excess evenly on a wider screen`() {
        // 2160px cap, 3200px screen -> 1040px excess -> 520px margin each side
        assertEquals(520, UtilCalculator.calculateContentMaxWidthMargin(3200, 2160))
    }

    @Test
    fun `calculateContentMaxWidthMargin matches the observed S24 Ultra landscape width (no clamp)`() {
        // Regression fixture from live S24 Ultra measurement: landscape width ~668dp at 3x
        // density = 2004px, under the 720dp (2160px at 3x) cap - must not clamp a normal phone.
        assertEquals(0, UtilCalculator.calculateContentMaxWidthMargin(2004, 2160))
    }
}
