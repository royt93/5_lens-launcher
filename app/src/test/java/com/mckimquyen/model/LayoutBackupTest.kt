package com.mckimquyen.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-006: pure (de)serialization round-trip - the actual JSON on disk is the contract this
 * feature promises ("export now, import after a reinstall"), so every field must survive exactly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LayoutBackupTest {

    private fun sampleEntry() = LayoutBackupEntry(
        packageName = "com.example.app",
        name = "com.example.app.MainActivity",
        orderNumber = 3,
        appVisible = false,
        appOpened = false,
        isFavorite = true,
        folderName = "Work",
        pinnedZone = PinnedZone.START.name
    )

    @Test
    fun `entry round-trips through JSON with every field intact`() {
        val entry = sampleEntry()
        val restored = LayoutBackupEntry.fromJson(entry.toJson())
        assertEquals(entry, restored)
    }

    @Test
    fun `entry with null folder round-trips as null, not the string 'null'`() {
        val entry = sampleEntry().copy(folderName = null)
        val restored = LayoutBackupEntry.fromJson(entry.toJson())
        assertEquals(null, restored?.folderName)
    }

    @Test
    fun `backup round-trips through toJson-parse with schema version and all entries`() {
        val backup = LayoutBackup(LayoutBackup.CURRENT_SCHEMA_VERSION, 1_700_000_000_000L, listOf(sampleEntry()))
        val result = LayoutBackup.parse(backup.toJson())
        assertTrue(result is LayoutBackupParseResult.Success)
        val restored = (result as LayoutBackupParseResult.Success).backup
        assertEquals(backup.schemaVersion, restored.schemaVersion)
        assertEquals(backup.exportedAt, restored.exportedAt)
        assertEquals(backup.entries, restored.entries)
    }

    @Test
    fun `fromPersistent skips rows with a blank identity instead of crashing`() {
        val validRow = AppPersistent(packageName = "com.a", name = "com.a.Main", identifier = "com.a-com.a.Main")
        val blankPackageRow = AppPersistent(packageName = "", name = "com.b.Main", identifier = "")
        val nullNameRow = AppPersistent(packageName = "com.c", name = null, identifier = "")

        val backup = LayoutBackup.fromPersistent(listOf(validRow, blankPackageRow, nullNameRow), 0L)

        assertEquals(1, backup.entries.size)
        assertEquals("com.a", backup.entries.first().packageName)
    }

    @Test
    fun `malformed JSON is rejected, never throws`() {
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse("not json at all"))
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse("{}"))
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse("""{"schemaVersion":0,"entries":[]}"""))
    }

    @Test
    fun `a future schema version is rejected explicitly, not silently best-effort read`() {
        val future = """{"schemaVersion":999,"exportedAt":0,"entries":[]}"""
        val result = LayoutBackup.parse(future)
        assertTrue(result is LayoutBackupParseResult.UnsupportedSchemaVersion)
        assertEquals(999, (result as LayoutBackupParseResult.UnsupportedSchemaVersion).foundVersion)
    }

    @Test
    fun `one malformed entry inside an otherwise valid file is skipped, not fatal`() {
        val json = """
            {"schemaVersion":1,"exportedAt":0,"entries":[
                {"packageName":"com.a","name":"com.a.Main","orderNumber":0,"appVisible":true,"appOpened":true,"isFavorite":false,"pinnedZone":"NONE"},
                {"orderNumber":1}
            ]}
        """.trimIndent()
        val result = LayoutBackup.parse(json) as LayoutBackupParseResult.Success
        assertEquals(1, result.backup.entries.size)
        assertEquals("com.a", result.backup.entries.first().packageName)
    }

    @Test
    fun `missing required top-level keys is malformed`() {
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse("""{"schemaVersion":1}"""))
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse("""{"entries":[]}"""))
    }

    @Test
    fun `missing optional fields fall back to safe defaults, not crash`() {
        val json = """
            {"schemaVersion":1,"exportedAt":0,"entries":[
                {"packageName":"com.a","name":"com.a.Main"}
            ]}
        """.trimIndent()
        val result = LayoutBackup.parse(json) as LayoutBackupParseResult.Success
        val entry = result.backup.entries.single()
        assertTrue(entry.appVisible)
        assertTrue(entry.appOpened)
        assertEquals(false, entry.isFavorite)
        assertNull(entry.folderName)
        assertEquals(PinnedZone.NONE.name, entry.pinnedZone)
    }
}
