package com.mckimquyen.model

import com.mckimquyen.util.LensAppScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FEAT-006 / workspace-backup-v2: pure (de)serialization round-trip - the actual JSON on disk is
 * the contract this feature promises ("export now, import after a reinstall"), so every field
 * must survive exactly. Schema v1 (flat `entries`) must stay importable forever; schema v2 (per-
 * lens `lenses`) is strict - any semantically invalid v2 data is rejected outright, never
 * best-effort imported.
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

    private fun sampleLens(
        id: String = LensWorkspace.DEFAULT_LENS_ID,
        name: String = "Lens 1",
        orderIndex: Int = 0,
        scope: LensAppScope = LensAppScope.ALL,
        selectedAppIds: Set<String> = emptySet(),
        frozen: Boolean = false,
        entries: List<LayoutBackupEntry> = listOf(sampleEntry())
    ) = LayoutBackupLens(id, name, orderIndex, scope, selectedAppIds, frozen, entries)

    // --- entry-level (schema-neutral) ---

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

    // --- schema v1 compatibility ---

    @Test
    fun `v1 backup round-trips through toJson-parse with schema version and all entries`() {
        val backup = LayoutBackup(LayoutBackup.SCHEMA_VERSION_V1, 1_700_000_000_000L, listOf(sampleEntry()))
        val result = LayoutBackup.parse(backup.toJson())
        assertTrue(result is LayoutBackupParseResult.Success)
        val restored = (result as LayoutBackupParseResult.Success).backup
        assertEquals(backup.schemaVersion, restored.schemaVersion)
        assertEquals(backup.exportedAt, restored.exportedAt)
        assertEquals(backup.entries, restored.entries)
        assertTrue(restored.lenses.isEmpty())
    }

    @Test
    fun `a known v1 JSON parses as version 1 with entries and no lenses`() {
        val json = """
            {"schemaVersion":1,"exportedAt":0,"entries":[
                {"packageName":"com.a","name":"com.a.Main","orderNumber":0,"appVisible":true,"appOpened":true,"isFavorite":false,"pinnedZone":"NONE"}
            ]}
        """.trimIndent()
        val result = LayoutBackup.parse(json)
        assertTrue(result is LayoutBackupParseResult.Success)
        val backup = (result as LayoutBackupParseResult.Success).backup
        assertEquals(LayoutBackup.SCHEMA_VERSION_V1, backup.schemaVersion)
        assertEquals(1, backup.entries.size)
        assertTrue(backup.lenses.isEmpty())
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
    fun `one malformed entry inside an otherwise valid v1 file is skipped, not fatal`() {
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

    // --- schema v2: round trip ---

    @Test
    fun `v2 backup round-trips through toJson-parse with lenses, scope, selection and frozen state`() {
        val defaultLens = sampleLens()
        val workLens = sampleLens(
            id = "work",
            name = "Work",
            orderIndex = 1,
            scope = LensAppScope.SELECTED,
            selectedAppIds = setOf("com.example.app-com.example.app.MainActivity"),
            frozen = true,
            entries = listOf(sampleEntry().copy(packageName = "com.other", name = "com.other.Main"))
        )
        val backup = LayoutBackup(LayoutBackup.CURRENT_SCHEMA_VERSION, 1_700_000_000_000L, lenses = listOf(defaultLens, workLens))

        val result = LayoutBackup.parse(backup.toJson())

        assertTrue(result is LayoutBackupParseResult.Success)
        val restored = (result as LayoutBackupParseResult.Success).backup
        assertEquals(LayoutBackup.CURRENT_SCHEMA_VERSION, restored.schemaVersion)
        assertTrue(restored.entries.isEmpty())
        assertEquals(listOf(defaultLens, workLens), restored.lenses)
    }

    // --- schema v2: adversarial ---

    @Test
    fun `v2 with missing lenses is malformed`() {
        val json = """{"schemaVersion":2,"exportedAt":0}"""
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(json))
    }

    @Test
    fun `v2 lens missing selectedAppIds is malformed`() {
        val json = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":"default","name":"Lens 1","orderIndex":0,"scope":"SELECTED","frozen":false,"entries":[]}
            ]}
        """.trimIndent()
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(json))
    }

    @Test
    fun `v2 lens missing entries is malformed`() {
        val json = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":"default","name":"Lens 1","orderIndex":0,"scope":"ALL","selectedAppIds":[],"frozen":false}
            ]}
        """.trimIndent()
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(json))
    }

    @Test
    fun `v2 lens with non-array selectedAppIds is malformed`() {
        val json = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":"default","name":"Lens 1","orderIndex":0,"scope":"SELECTED","selectedAppIds":"app-id","frozen":false,"entries":[]}
            ]}
        """.trimIndent()
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(json))
    }

    @Test
    fun `v2 lens with non-array entries is malformed`() {
        val json = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":"default","name":"Lens 1","orderIndex":0,"scope":"ALL","selectedAppIds":[],"frozen":false,"entries":{}}
            ]}
        """.trimIndent()
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(json))
    }

    @Test
    fun `v2 round trips active lens id`() {
        val json = """
            {"schemaVersion":2,"exportedAt":0,"activeLensId":"work","lenses":[
                {"id":"default","name":"Home","orderIndex":0,"scope":"ALL","selectedAppIds":[],"frozen":false,"entries":[]},
                {"id":"work","name":"Work","orderIndex":1,"scope":"ALL","selectedAppIds":[],"frozen":false,"entries":[]}
            ]}
        """.trimIndent()
        val result = LayoutBackup.parse(json) as LayoutBackupParseResult.Success
        assertEquals("work", result.backup.activeLensId)
        val reparsed = LayoutBackup.parse(result.backup.toJson()) as LayoutBackupParseResult.Success
        assertEquals("work", reparsed.backup.activeLensId)
    }

    @Test
    fun `v2 rejects active lens id not present in lenses`() {
        val json = """
            {"schemaVersion":2,"exportedAt":0,"activeLensId":"missing","lenses":[
                {"id":"default","name":"Home","orderIndex":0,"scope":"ALL","selectedAppIds":[],"frozen":false,"entries":[]}
            ]}
        """.trimIndent()
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(json))
    }

    @Test
    fun `v2 requires activeLensId string referencing an existing lens`() {
        val missing = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":"default","name":"Home","orderIndex":0,"scope":"ALL","selectedAppIds":[],"frozen":false,"entries":[]}
            ]}
        """.trimIndent()
        val wrongType = """
            {"schemaVersion":2,"exportedAt":0,"activeLensId":7,"lenses":[
                {"id":"default","name":"Home","orderIndex":0,"scope":"ALL","selectedAppIds":[],"frozen":false,"entries":[]}
            ]}
        """.trimIndent()
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(missing))
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(wrongType))
    }

    @Test
    fun `v2 complete lens with complete entry parses`() {
        val json = """
            {"schemaVersion":2,"exportedAt":0,"activeLensId":"default","lenses":[
                {"id":"default","name":"Lens 1","orderIndex":0,"scope":"ALL","selectedAppIds":[],"frozen":false,"entries":[
                    {"packageName":"com.a","name":"com.a.Main","orderNumber":0,"appVisible":true,"appOpened":true,"isFavorite":false,"folderName":null,"pinnedZone":"NONE"}
                ]}
            ]}
        """.trimIndent()
        val result = LayoutBackup.parse(json)
        assertTrue(result is LayoutBackupParseResult.Success)
        val entry = (result as LayoutBackupParseResult.Success).backup.lenses.single().entries.single()
        assertEquals(0, entry.orderNumber)
        assertEquals("com.a", entry.packageName)
        assertEquals("com.a.Main", entry.name)
        assertTrue(entry.appVisible)
        assertTrue(entry.appOpened)
        assertEquals(false, entry.isFavorite)
        assertNull(entry.folderName)
        assertEquals(PinnedZone.NONE.name, entry.pinnedZone)
    }

    @Test
    fun `v2 lens rejects missing or non-boolean frozen`() {
        val missing = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":"default","name":"Lens 1","orderIndex":0,"scope":"ALL","selectedAppIds":[],"entries":[]}
            ]}
        """.trimIndent()
        val wrongType = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":"default","name":"Lens 1","orderIndex":0,"scope":"ALL","selectedAppIds":[],"frozen":"false","entries":[]}
            ]}
        """.trimIndent()
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(missing))
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(wrongType))
    }

    @Test
    fun `v2 lens rejects wrong-typed identity and numeric fields`() {
        val wrongId = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":7,"name":"Lens 1","orderIndex":0,"scope":"ALL","selectedAppIds":[],"frozen":false,"entries":[]}
            ]}
        """.trimIndent()
        val wrongOrder = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":"default","name":"Lens 1","orderIndex":"0","scope":"ALL","selectedAppIds":[],"frozen":false,"entries":[]}
            ]}
        """.trimIndent()
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(wrongId))
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(wrongOrder))
    }

    @Test
    fun `v2 entry rejects missing and wrong-typed required fields`() {
        val missingOrder = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":"default","name":"Lens 1","orderIndex":0,"scope":"ALL","selectedAppIds":[],"frozen":false,"entries":[
                    {"packageName":"com.a","name":"com.a.Main","appVisible":true,"appOpened":true,"isFavorite":false,"folderName":null,"pinnedZone":"NONE"}
                ]}
            ]}
        """.trimIndent()
        val wrongVisibility = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":"default","name":"Lens 1","orderIndex":0,"scope":"ALL","selectedAppIds":[],"frozen":false,"entries":[
                    {"packageName":"com.a","name":"com.a.Main","orderNumber":0,"appVisible":"true","appOpened":true,"isFavorite":false,"folderName":null,"pinnedZone":"NONE"}
                ]}
            ]}
        """.trimIndent()
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(missingOrder))
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(wrongVisibility))
    }

    @Test
    fun `v2 rejects duplicate app identities within one lens`() {
        val json = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":"default","name":"Lens 1","orderIndex":0,"scope":"ALL","selectedAppIds":[],"frozen":false,"entries":[
                    {"packageName":"com.a","name":"com.a.Main","orderNumber":0,"appVisible":true,"appOpened":true,"isFavorite":false,"folderName":null,"pinnedZone":"NONE"},
                    {"packageName":"com.a","name":"com.a.Main","orderNumber":1,"appVisible":true,"appOpened":true,"isFavorite":true,"folderName":null,"pinnedZone":"NONE"}
                ]}
            ]}
        """.trimIndent()
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(json))
    }

    @Test
    fun `v2 with no default lens is malformed`() {
        val backup = LayoutBackup(2, 0L, lenses = listOf(sampleLens(id = "work")))
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(backup.toJson()))
    }

    @Test
    fun `v2 with duplicate lens ids is malformed`() {
        val backup = LayoutBackup(2, 0L, lenses = listOf(sampleLens(), sampleLens(orderIndex = 1)))
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(backup.toJson()))
    }

    @Test
    fun `v2 with duplicate order indices is malformed`() {
        val backup = LayoutBackup(
            2, 0L,
            lenses = listOf(sampleLens(), sampleLens(id = "work", orderIndex = 0))
        )
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(backup.toJson()))
    }

    @Test
    fun `v2 with a negative order index is malformed`() {
        val backup = LayoutBackup(2, 0L, lenses = listOf(sampleLens(orderIndex = -1)))
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(backup.toJson()))
    }

    @Test
    fun `v2 with an invalid scope value is malformed`() {
        val json = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":"default","name":"Lens 1","orderIndex":0,"scope":"EVERYTHING","selectedAppIds":[],"frozen":false,"entries":[]}
            ]}
        """.trimIndent()
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(json))
    }

    @Test
    fun `v2 with duplicate selected ids is malformed`() {
        val json = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":"default","name":"Lens 1","orderIndex":0,"scope":"SELECTED","selectedAppIds":["a","a"],"frozen":false,"entries":[]}
            ]}
        """.trimIndent()
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(json))
    }

    @Test
    fun `v2 with a blank selected id is malformed`() {
        val json = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":"default","name":"Lens 1","orderIndex":0,"scope":"SELECTED","selectedAppIds":[""],"frozen":false,"entries":[]}
            ]}
        """.trimIndent()
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(json))
    }

    @Test
    fun `v2 with a malformed entry inside a lens is malformed, not silently skipped`() {
        val json = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":"default","name":"Lens 1","orderIndex":0,"scope":"ALL","selectedAppIds":[],"frozen":false,"entries":[
                    {"orderNumber":1}
                ]}
            ]}
        """.trimIndent()
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(json))
    }

    @Test
    fun `v2 with an unrecognized pinnedZone enum value on an entry is malformed`() {
        val json = """
            {"schemaVersion":2,"exportedAt":0,"lenses":[
                {"id":"default","name":"Lens 1","orderIndex":0,"scope":"ALL","selectedAppIds":[],"frozen":false,"entries":[
                    {"packageName":"com.a","name":"com.a.Main","orderNumber":0,"appVisible":true,"appOpened":true,"isFavorite":false,"pinnedZone":"SIDEWAYS"}
                ]}
            ]}
        """.trimIndent()
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(json))
    }

    @Test
    fun `v2 tolerates unknown additive fields at root and lens level`() {
        val json = """
            {"schemaVersion":2,"exportedAt":0,"activeLensId":"default","futureRootField":"x","lenses":[
                {"id":"default","name":"Lens 1","orderIndex":0,"scope":"ALL","selectedAppIds":[],"frozen":false,"entries":[],"futureLensField":42}
            ]}
        """.trimIndent()
        val result = LayoutBackup.parse(json)
        assertTrue(result is LayoutBackupParseResult.Success)
        assertEquals(1, (result as LayoutBackupParseResult.Success).backup.lenses.size)
    }

    @Test
    fun `v2 preserves selected ids for an app that has no matching entry, such as an uninstalled app`() {
        val uninstalledId = "com.gone-com.gone.Main"
        val backup = LayoutBackup(
            2, 0L,
            lenses = listOf(
                sampleLens(
                    scope = LensAppScope.SELECTED,
                    selectedAppIds = setOf(uninstalledId),
                    entries = emptyList()
                )
            )
        )
        val result = LayoutBackup.parse(backup.toJson())
        assertTrue(result is LayoutBackupParseResult.Success)
        val lens = (result as LayoutBackupParseResult.Success).backup.lenses.single()
        assertEquals(setOf(uninstalledId), lens.selectedAppIds)
        assertTrue(lens.entries.isEmpty())
    }
}
