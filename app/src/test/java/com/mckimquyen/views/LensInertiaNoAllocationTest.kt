package com.mckimquyen.views

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** FISH-041 source guards for the per-frame spring and warped-rect selection path; static check, not runtime profiling. */
class LensInertiaNoAllocationTest {
    private val source by lazy {
        val dir = File(requireNotNull(System.getProperty("user.dir")))
        sequenceOf(
            File(dir, "src/main/java/com/mckimquyen/views/LensView.kt"),
            File(dir, "app/src/main/java/com/mckimquyen/views/LensView.kt")
        ).first { it.isFile }.readText()
    }

    private fun body(signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("$signature exists", start >= 0)
        val open = source.indexOf('{', start)
        var depth = 0
        for (i in open until source.length) {
            if (source[i] == '{') depth++
            if (source[i] == '}' && --depth == 0) return source.substring(open, i + 1)
        }
        error("unbalanced body: $signature")
    }

    @Test fun inertiaFrameStepDoesNotAllocate() {
        val b = body("private fun updateLensInertia(")
        listOf("RectF(", "Paint(", "Array(", "FloatArray(", "IntArray(", "listOf(", "mutableListOf(", "String.format").forEach {
            assertFalse("updateLensInertia contains $it", b.contains(it))
        }
    }

    @Test fun centreComparisonAndWarpRectHelperDoNotAllocate() {
        val b = body("private fun calculateWarpedRect(")
        listOf("RectF(", "Paint(", "Array(", "FloatArray(", "IntArray(", "listOf(", "mutableListOf(", "String.format").forEach {
            assertFalse("calculateWarpedRect contains $it", b.contains(it))
        }
    }
}
