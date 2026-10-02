package com.mckimquyen.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * UI-025: the lens-management popup shipped square because one call site built its PopupMenu
 * from a bare Activity instead of a PopupMenuTheme-wrapped context. This is a convention guard:
 * every PopupMenu constructed in main sources must take a context named `wrapper` or `themed`,
 * and its file must reference PopupMenuTheme. It cannot prove the wrapper is built correctly -
 * the device widget test resolves the real theme attribute for that.
 */
class PopupMenuThemeConventionTest {

    private val themedContextNames = setOf("wrapper", "themed")

    // Matches `PopupMenu(x,` and `new PopupMenu(x,` but not `ListPopupMenu(` or `a.PopupMenu(`.
    private val constructorCall = Regex("""(?<![\w.])(?:new\s+)?PopupMenu\(\s*(\w+)\s*,""")

    private fun mainSources(): List<File> {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val root = sequenceOf(
            File(workingDirectory, "src/main/java"),
            File(workingDirectory, "app/src/main/java")
        ).first { it.isDirectory }
        return root.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .toList()
    }

    @Test
    fun `the scan really finds the known popup call sites`() {
        val sites = mainSources().sumOf { constructorCall.findAll(it.readText()).count() }
        assertTrue("expected at least the four known PopupMenu call sites, found $sites", sites >= 4)
    }

    @Test
    fun `every PopupMenu is built from a PopupMenuTheme wrapped context`() {
        val offenders = mutableListOf<String>()
        for (file in mainSources()) {
            val text = file.readText()
            for (match in constructorCall.findAll(text)) {
                val firstArgument = match.groupValues[1]
                if (firstArgument !in themedContextNames) {
                    offenders += "${file.name}: PopupMenu($firstArgument, ...) is not a themed context"
                } else if (!text.contains("PopupMenuTheme")) {
                    offenders += "${file.name}: PopupMenu($firstArgument, ...) but no PopupMenuTheme in file"
                }
            }
        }
        assertEquals("un-themed popups render square corners: $offenders", emptyList<String>(), offenders)
    }
}
