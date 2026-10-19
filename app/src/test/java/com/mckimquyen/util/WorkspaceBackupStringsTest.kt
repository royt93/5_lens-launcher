package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every locale must translate the workspace-backup strings and keep the format placeholders of the default text. */
class WorkspaceBackupStringsTest {
    private val keys = listOf(
        "layout_import_workspace_preview_message",
        "layout_import_workspace_failed",
        "layout_import_workspace_recovery_required",
        "layout_import_v1_default_lens_note"
    )
    private val placeholder = Regex("%\\d+\\$[a-z]")

    private fun resDir(): File {
        val wd = File(requireNotNull(System.getProperty("user.dir")))
        return listOf(File(wd, "src/main/res"), File(wd, "app/src/main/res")).first { it.isDirectory }
    }

    private fun value(file: File, key: String): String? =
        Regex("<string name=\"$key\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .find(file.readText())?.groupValues?.get(1)

    @Test
    fun everyLocaleTranslatesWorkspaceStringsAndKeepsPlaceholders() {
        val res = resDir()
        val defaults = keys.associateWith { requireNotNull(value(File(res, "values/strings.xml"), it)) { "missing default $it" } }
        val locales = res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!
        assertTrue("expected translated locales", locales.size >= 15)
        for (dir in locales) {
            for (key in keys) {
                val translated = value(File(dir, "strings.xml"), key) ?: continue
                assertNotEquals("${dir.name}/$key is still English", defaults.getValue(key), translated)
                assertEquals(
                    "${dir.name}/$key placeholders",
                    placeholder.findAll(defaults.getValue(key)).map { it.value }.sorted().toList(),
                    placeholder.findAll(translated).map { it.value }.sorted().toList()
                )
            }
        }
    }

    @Test
    fun everyLocaleDefinesAllWorkspaceStrings() {
        val res = resDir()
        val locales = res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") && File(f, "strings.xml").exists() }!!
        for (dir in locales) {
            if (value(File(dir, "strings.xml"), "layout_import_preview_message") == null) continue
            for (key in keys) assertTrue("${dir.name} lacks $key", value(File(dir, "strings.xml"), key) != null)
        }
    }
}
