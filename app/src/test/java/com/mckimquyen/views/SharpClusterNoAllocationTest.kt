package com.mckimquyen.views

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Source check: per-cell update and end-of-frame counter must not allocate on LensView's hot path. */
class SharpClusterNoAllocationTest {
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
        error("unbalanced body after $signature")
    }

    @Test fun cellMarkingAndFrameEndDoNotAllocate() {
        listOf("mClusterTracker.markCell(", "mClusterTracker.endFrame(").forEach { signature ->
            val start = source.indexOf(signature)
            assertTrue("$signature exists", start >= 0)
        }
        listOf("fun finishClusterFrame(").forEach { signature ->
            val body = body(signature)
            listOf("Array(", "BooleanArray(", "IntArray(", "RectF(", "listOf(", "map {").forEach {
                assertTrue("$signature must not allocate '$it'", !body.contains(it))
            }
        }
    }
}
