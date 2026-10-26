package com.mckimquyen.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * REL-002: backup must not carry entitlement, ad state or search history to another device.
 * Parses the real manifest + rule files, so a dropped attribute or file fails here.
 */
class BackupRulesTest {

    private val appDir: File
        get() {
            val wd = File(System.getProperty("user.dir") ?: ".")
            return if (File(wd, "src/main").isDirectory) wd else File(wd, "app")
        }

    private fun parse(path: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(appDir, path)).documentElement

    private val excludedPrefs = setOf(VIP_PREFS, SEARCH_HISTORY_PREFS, APP_PREFS)

    private fun excludes(root: org.w3c.dom.Element, domain: String): Set<String> {
        val nodes = root.getElementsByTagName("exclude")
        return (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
            .filter { it.getAttribute("domain") == domain }
            .map { it.getAttribute("path") }
            .toSet()
    }

    @Test
    fun `manifest wires both backup rule files`() {
        val application = parse("src/main/AndroidManifest.xml").getElementsByTagName("application").item(0) as org.w3c.dom.Element
        assertEquals("@xml/data_extraction_rules", application.getAttribute("android:dataExtractionRules"))
        assertEquals("@xml/backup_rules", application.getAttribute("android:fullBackupContent"))
    }

    @Test
    fun `legacy full backup rules exclude sensitive prefs`() {
        val ex = excludes(parse("src/main/res/xml/backup_rules.xml"), "sharedpref")
        assertTrue("missing: ${excludedPrefs - ex}", ex.containsAll(excludedPrefs.map { "$it.xml" }))
    }

    @Test
    fun `android 12 rules exclude sensitive prefs from cloud backup and device transfer`() {
        val root = parse("src/main/res/xml/data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val node = root.getElementsByTagName(section).item(0) as org.w3c.dom.Element
            val ex = excludes(node, "sharedpref")
            assertTrue("$section missing: ${excludedPrefs - ex}", ex.containsAll(excludedPrefs.map { "$it.xml" }))
        }
    }

    /**
     * DB-009: rotating workspace snapshots live in `noBackupFilesDir`, which Android never includes in cloud
     * backup or device transfer, so no rule entry is needed - but that only holds while the code keeps using it
     * and no FileProvider path exposes it.
     */
    @Test
    fun `local workspace snapshots stay in noBackupFilesDir and out of the file provider`() {
        val source = File(appDir, "src/main/java/com/mckimquyen/util/LocalWorkspaceBackups.kt").readText()
        assertTrue("snapshots must be written under noBackupFilesDir", source.contains("noBackupFilesDir"))
        assertTrue("snapshots must not be written to filesDir", !Regex("""\bfilesDir\b""").containsMatchIn(source.replace("noBackupFilesDir", "")))

        val paths = parse("src/main/res/xml/file_paths.xml")
        val tags = paths.childNodes
        for (i in 0 until tags.length) {
            val node = tags.item(i) as? org.w3c.dom.Element ?: continue
            assertTrue("FileProvider must not expose snapshots: ${node.tagName}", node.tagName == "cache-path")
            assertEquals("polaroid/", node.getAttribute("path"))
        }
    }

    private companion object {
        const val VIP_PREFS = "vip_screen_prefs"
        const val SEARCH_HISTORY_PREFS = "app_search_history"
        const val APP_PREFS = "app_preferences"
    }
}
