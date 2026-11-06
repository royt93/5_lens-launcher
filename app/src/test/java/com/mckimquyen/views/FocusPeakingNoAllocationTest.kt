package com.mckimquyen.views

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** FISH-039: the focus-peaking hot path must not allocate; checked on the source of the two new functions. */
class FocusPeakingNoAllocationTest {
    private val source: String by lazy {
        val dir = File(requireNotNull(System.getProperty("user.dir")))
        val file = sequenceOf(
            File(dir, "src/main/java/com/mckimquyen/views/LensView.kt"),
            File(dir, "app/src/main/java/com/mckimquyen/views/LensView.kt")
        ).first { it.isFile }
        file.readText()
    }

    private fun bodyOf(signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("$signature not found in LensView.kt", start >= 0)
        val open = source.indexOf('{', start)
        var depth = 0
        var i = open
        while (i < source.length) {
            if (source[i] == '{') depth++
            if (source[i] == '}') { depth--; if (depth == 0) return source.substring(open, i + 1) }
            i++
        }
        error("unbalanced braces after $signature")
    }

    private val forbidden = listOf("Paint(", "RectF(", "Rect(", "Path(", "ArrayList", "listOf(", "mutableListOf", "String.format", "Array(")

    @Test fun theRingDrawingFunctionDoesNotAllocate() {
        val body = bodyOf("private fun drawFocusPeakingRing(")
        forbidden.forEach { assertTrue("drawFocusPeakingRing must not contain '$it'", !body.contains(it)) }
        assertTrue("it draws exactly one circle per call", body.split("drawCircle(").size == 2)
    }

    @Test fun thePaintPreparationFunctionDoesNotAllocate() {
        val body = bodyOf("private fun prepareFocusPeakingPaint(")
        forbidden.forEach { assertTrue("prepareFocusPeakingPaint must not contain '$it'", !body.contains(it)) }
    }
}
