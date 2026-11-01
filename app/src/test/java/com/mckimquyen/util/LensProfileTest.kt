package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LensProfileTest {
    private val lens = 400f
    private val item = 700f
    private val boundary = 1000f
    private val multiplier = 1f
    private val distortion = 2.5f

    private fun legacy(position: Float) =
        UtilCalculator.shiftPoint(lens, position, boundary, multiplier, distortion)

    @Test fun fisheyeIsExactlyTheLegacyFormulaOnBothAxes() {
        assertEquals(legacy(item), LensProfile.FISHEYE.shiftX(lens, item, boundary, multiplier, distortion), 0f)
        assertEquals(legacy(item), LensProfile.FISHEYE.shiftY(lens, item, boundary, multiplier, distortion), 0f)
    }

    @Test fun anamorphicWarpsOnlyTheHorizontalAxis() {
        assertEquals(legacy(item), LensProfile.ANAMORPHIC.shiftX(lens, item, boundary, multiplier, distortion), 0f)
        assertEquals(item, LensProfile.ANAMORPHIC.shiftY(lens, item, boundary, multiplier, distortion), 0f)
    }

    @Test fun cylindricalWarpsOnlyTheVerticalAxis() {
        assertEquals(item, LensProfile.CYLINDRICAL.shiftX(lens, item, boundary, multiplier, distortion), 0f)
        assertEquals(legacy(item), LensProfile.CYLINDRICAL.shiftY(lens, item, boundary, multiplier, distortion), 0f)
    }

    @Test fun idleSentinelLeavesEveryProfileUntouched() {
        for (profile in LensProfile.entries) {
            assertEquals(item, profile.shiftX(-Float.MAX_VALUE, item, boundary, multiplier, distortion), 0f)
            assertEquals(item, profile.shiftY(-Float.MAX_VALUE, item, boundary, multiplier, distortion), 0f)
        }
    }

    @Test fun anIdleAnimationMultiplierLeavesEveryProfileUntouched() {
        for (profile in LensProfile.entries) {
            assertEquals(item, profile.shiftX(lens, item, boundary, 0f, distortion), 0.0001f)
            assertEquals(item, profile.shiftY(lens, item, boundary, 0f, distortion), 0.0001f)
        }
    }

    @Test fun scaledPointKeepsTheUnwarpedAxisAtItsBaseCentre() {
        // The scaled point of an unwarped axis must equal its base centre, so the icon size comes
        // only from the warped axis and does not collapse to zero.
        assertEquals(item, LensProfile.ANAMORPHIC.scaleY(lens, item, 80f, boundary, multiplier, 1.5f, distortion), 0f)
        assertEquals(item, LensProfile.CYLINDRICAL.scaleX(lens, item, 80f, boundary, multiplier, 1.5f, distortion), 0f)
    }

    @Test fun squareSizeUsesOnlyTheWarpedAxisWhenTheOtherIsPinned() {
        // A pinned axis has scaled == shifted == base, so the legacy min() would collapse to 0.
        assertEquals(60f, LensProfile.sizeFor(LensProfile.ANAMORPHIC, 130f, 100f, 500f, 500f), 0f)
        assertEquals(40f, LensProfile.sizeFor(LensProfile.CYLINDRICAL, 500f, 500f, 120f, 100f), 0f)
    }

    @Test fun fisheyeSizeIsExactlyTheLegacySquareSize() {
        assertEquals(
            UtilCalculator.calculateSquareScaledSize(130f, 100f, 120f, 95f),
            LensProfile.sizeFor(LensProfile.FISHEYE, 130f, 100f, 120f, 95f), 0f
        )
    }

    @Test fun pincushionReflectsFisheyeDisplacementAboutLensCenterOnBothAxes() {
        for (itemPos in listOf(150f, 399f, 401f, 750f)) {
            val fisheyeX = LensProfile.FISHEYE.shiftX(lens, itemPos, boundary, multiplier, distortion)
            val fisheyeY = LensProfile.FISHEYE.shiftY(lens, itemPos, boundary, multiplier, distortion)
            val expectedX = 2f * itemPos - fisheyeX
            val expectedY = 2f * itemPos - fisheyeY
            assertEquals("shiftX item $itemPos", expectedX, LensProfile.PINCUSHION.shiftX(lens, itemPos, boundary, multiplier, distortion), 0.001f)
            assertEquals("shiftY item $itemPos", expectedY, LensProfile.PINCUSHION.shiftY(lens, itemPos, boundary, multiplier, distortion), 0.001f)
        }
    }

    @Test fun pincushionWithZeroMultiplierLeavesEveryItemInPlace() {
        for (itemPos in listOf(0f, 150f, 399f, 401f, 750f, 1000f)) {
            assertEquals(itemPos, LensProfile.PINCUSHION.shiftX(lens, itemPos, boundary, 0f, distortion), 0.001f)
            assertEquals(itemPos, LensProfile.PINCUSHION.shiftY(lens, itemPos, boundary, 0f, distortion), 0.001f)
        }
    }

    @Test fun pincushionPreservesFisheyeSquareScaledSize() {
        val scX = LensProfile.PINCUSHION.scaleX(lens, item, 80f, boundary, multiplier, 1.5f, distortion)
        val shX = LensProfile.PINCUSHION.shiftX(lens, item, boundary, multiplier, distortion)
        val scY = LensProfile.PINCUSHION.scaleY(lens, item, 80f, boundary, multiplier, 1.5f, distortion)
        val shY = LensProfile.PINCUSHION.shiftY(lens, item, boundary, multiplier, distortion)
        val size = LensProfile.sizeFor(LensProfile.PINCUSHION, scX, shX, scY, shY)
        val fisheyeSize = LensProfile.sizeFor(
            LensProfile.FISHEYE,
            LensProfile.FISHEYE.scaleX(lens, item, 80f, boundary, multiplier, 1.5f, distortion),
            LensProfile.FISHEYE.shiftX(lens, item, boundary, multiplier, distortion),
            LensProfile.FISHEYE.scaleY(lens, item, 80f, boundary, multiplier, 1.5f, distortion),
            LensProfile.FISHEYE.shiftY(lens, item, boundary, multiplier, distortion)
        )
        assertEquals(fisheyeSize, size, 0.001f)
        assertTrue("size must stay positive", size > 0f)
    }

    @Test fun storedNameParsesPincushion() {
        assertEquals(LensProfile.PINCUSHION, LensProfile.fromStored("PINCUSHION"))
    }

    @Test fun storedNamesParseAndUnknownOrMissingFallBackToFisheye() {
        assertEquals(LensProfile.ANAMORPHIC, LensProfile.fromStored("ANAMORPHIC"))
        assertEquals(LensProfile.CYLINDRICAL, LensProfile.fromStored("CYLINDRICAL"))
        assertEquals(LensProfile.FISHEYE, LensProfile.fromStored("bogus"))
        assertEquals(LensProfile.FISHEYE, LensProfile.fromStored(null))
    }
}
