package com.mckimquyen.testing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TEST-005: Robolectric 4.11.1 does not support the app's targetSdk (37). A Robolectric test with no
 * pinned `sdk` picks an SDK from whatever is installed on the machine, so its result depends on the
 * environment instead of the code. This guard fails the build for any Robolectric test class that
 * does not pin one, so the rule cannot silently regress.
 */
class RobolectricSdkPinTest {
    private val testRoot: File = run {
        val wd = File(requireNotNull(System.getProperty("user.dir")))
        sequenceOf(File(wd, "src/test/java"), File(wd, "app/src/test/java")).first { it.isDirectory }
    }

    private fun robolectricTests(): List<File> = testRoot.walkTopDown()
        .filter { it.isFile && it.extension == "kt" && "RobolectricTestRunner" in it.readText() && "@RunWith(RobolectricTestRunner::class)" in it.readText() }
        .toList()

    @Test
    fun `every Robolectric test pins an sdk`() {
        val unpinned = robolectricTests().filterNot { Regex("""@Config\([^)]*\bsdk\s*=""").containsMatchIn(it.readText()) }
            .map { it.relativeTo(testRoot).path }
        assertEquals("Robolectric tests without @Config(sdk = [...]): $unpinned", emptyList<String>(), unpinned)
    }

    @Test
    fun `the guard actually finds the Robolectric tests`() {
        assertTrue("expected dozens of Robolectric tests, found ${robolectricTests().size}", robolectricTests().size >= 50)
    }

    @Test
    fun `the pinned sdk is one Robolectric 4_11_1 supports`() {
        val sdks = robolectricTests().flatMap { file ->
            Regex("""sdk\s*=\s*\[([^\]]*)]""").findAll(file.readText()).flatMap { it.groupValues[1].split(',') }.map { it.trim() }
        }.mapNotNull(String::toIntOrNull).toSet()
        assertTrue("pinned sdk levels must not exceed 34 (Robolectric 4.11.1 ceiling): $sdks", sdks.all { it <= MAX_SUPPORTED_SDK })
    }

    private companion object {
        const val MAX_SUPPORTED_SDK = 34
    }
}
