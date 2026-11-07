package com.mckimquyen.views

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** FISH-040: the chromatic-aberration hot path must not allocate; checked on the source of the new LensView functions. */
class ChromaticAberrationNoAllocationTest {
    private val lensView: String by lazy {
        val dir = File(requireNotNull(System.getProperty("user.dir")))
        sequenceOf(
            File(dir, "src/main/java/com/mckimquyen/views/LensView.kt"),
            File(dir, "app/src/main/java/com/mckimquyen/views/LensView.kt")
        ).first { it.isFile }.readText()
    }

    private val math: String by lazy {
        val dir = File(requireNotNull(System.getProperty("user.dir")))
        sequenceOf(
            File(dir, "src/main/java/com/mckimquyen/views/ChromaticAberration.kt"),
            File(dir, "app/src/main/java/com/mckimquyen/views/ChromaticAberration.kt")
        ).first { it.isFile }.readText()
    }

    private fun bodyOf(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("$signature not found", start >= 0)
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

    private val forbidden = listOf("Paint(", "RectF(", "Rect(", "Path(", "ArrayList", "listOf(", "mutableListOf", "String.format", "Array(", "FloatArray(", "IntArray(", "ColorMatrix(", "ColorMatrixColorFilter(", "PorterDuffXfermode(")

    @Test fun theSecondPassDoesNotAllocate() {
        val body = bodyOf(lensView, "private fun drawChromaticFringes(")
        forbidden.forEach { assertTrue("drawChromaticFringes must not contain '$it'", !body.contains(it)) }
        assertEquals("two bitmap draws per fringed icon (red and blue)", 3, body.split("drawBitmap(").size)
    }

    @Test fun thePerFramePreparationDoesNotAllocate() {
        val body = bodyOf(lensView, "private fun prepareChromaticAberration(")
        forbidden.forEach { assertTrue("prepareChromaticAberration must not contain '$it'", !body.contains(it)) }
    }

    @Test fun theMathFunctionsDoNotAllocate() {
        listOf("fun offsetPx(", "fun direction(", "fun isFringed(", "fun shouldApply(").forEach { sig ->
            val body = bodyOf(math, sig)
            forbidden.forEach { assertTrue("$sig must not contain '$it'", !body.contains(it)) }
        }
    }

    @Test fun offerDoesNotAllocateAfterConstruction() {
        val body = bodyOf(math, "fun offer(")
        forbidden.forEach { assertTrue("offer must not contain '$it'", !body.contains(it)) }
    }
}
