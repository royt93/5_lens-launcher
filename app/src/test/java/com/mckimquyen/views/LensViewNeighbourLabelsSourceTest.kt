package com.mckimquyen.views

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LensViewNeighbourLabelsSourceTest {
    private val dir = File(requireNotNull(System.getProperty("user.dir")))
    private val source by lazy {
        sequenceOf(File(dir, "src/main/java/com/mckimquyen/views/LensView.kt"), File(dir, "app/src/main/java/com/mckimquyen/views/LensView.kt"))
            .first { it.isFile }.readText()
    }
    private val drawAppName by lazy {
        val start = source.indexOf("private fun drawAppName(")
        assertTrue(start >= 0)
        // Up to the next function declaration, so helpers added after drawAppName are not scanned as part of it.
        source.substring(start, source.indexOf("\n    private fun ", start + 10))
    }

    @Test fun settingSampledFromTheKey() {
        assertTrue(source.contains("neighbourLabelsEnabled = mUtilSettings?.getBoolean(UtilSettings.KEY_NEIGHBOUR_LABELS)"))
    }

    @Test fun theGateUsesTheSwitchTheNameSettingCleanModeAndMoving() {
        assertTrue(drawAppName.contains("NeighbourLabels.shouldApply("))
        val gate = drawAppName.substring(drawAppName.indexOf("NeighbourLabels.shouldApply("))
        val head = gate.take(420)
        assertTrue(head.contains("neighbourLabelsEnabled"))
        assertTrue(head.contains("KEY_SHOW_NAME_APP_HOVER"))
        assertTrue(head.contains("KEY_CLEAN_LENS_MODE"))
        assertTrue(head.contains("mMoving"))
    }

    @Test fun theMainLabelIsStillDrawnFirstAndUnchanged() {
        val main = drawAppName.indexOf("app.label as String")
        val neighbours = drawAppName.indexOf("NeighbourLabels.neighbours(")
        assertTrue(main in 0 until neighbours)
        assertTrue(drawAppName.contains("r.top - resources.getDimension(R.dimen.margin_lens_text)"))
    }

    @Test fun neighbourLabelsUseTheDrawnRectsAndTheFitRuleAndRestoreThePaintAlpha() {
        assertTrue(drawAppName.contains("mDrawnRectsForTest"))
        assertTrue(drawAppName.contains("NeighbourLabels.fits("))
        assertTrue(drawAppName.contains("NeighbourLabels.ALPHA"))
        assertTrue("alpha is put back after the neighbours", drawAppName.contains("p.alpha = previousAlpha"))
    }

    @Test fun hitTestAndSelectionNeverReadTheNeighbourState() {
        val frame = source.substring(source.indexOf("private fun drawGrid("))
        val hit = frame.indexOf("UtilCalculator.isInsideRect(mTouchX, mTouchY, selectRect)")
        assertTrue(hit >= 0)
        assertFalse(frame.substring(0, hit).contains("NeighbourLabels"))
    }

    @Test fun theNeighbourPathCreatesNoTemporaryObjects() {
        val start = drawAppName.indexOf("NeighbourLabels.neighbours(")
        assertTrue(start >= 0)
        val block = drawAppName.substring(start)
        listOf("RectF(", "FloatArray(", "IntArray(", "Array(", "Pair(", "listOf(", "mutableListOf(", "String.format").forEach {
            assertFalse("neighbour block contains $it", block.contains(it))
        }
    }
}
