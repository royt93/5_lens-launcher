package com.mckimquyen.util

import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupEntry
import com.mckimquyen.model.LayoutBackupLens
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.PinnedZone
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LayoutWorkspaceImportPlanTest {

    private fun entry(pkg: String, order: Int = 0) = LayoutBackupEntry(
        packageName = pkg,
        name = "$pkg.Main",
        orderNumber = order,
        appVisible = true,
        appOpened = true,
        isFavorite = false,
        folderName = null,
        pinnedZone = PinnedZone.NONE.name
    )

    private fun lens(
        id: String,
        orderIndex: Int,
        scope: LensAppScope = LensAppScope.ALL,
        selectedAppIds: Set<String> = emptySet(),
        frozen: Boolean = false,
        entries: List<LayoutBackupEntry> = emptyList()
    ) = LayoutBackupLens(id, "Lens $id", orderIndex, scope, selectedAppIds, frozen, entries)

    @Test
    fun `planV1 targets only the default lens id and carries entries`() {
        val entries = listOf(entry("com.a"))
        val plan = LayoutImportApplier.planV1(LayoutBackup(LayoutBackup.SCHEMA_VERSION_V1, 0L, entries))
        assertEquals(LayoutBackup.SCHEMA_VERSION_V1, plan.schemaVersion)
        assertEquals(1, plan.lenses.size)
        assertEquals(LensWorkspace.DEFAULT_LENS_ID, plan.lenses.single().id)
        assertEquals(entries, plan.lenses.single().entries)
    }

    @Test
    fun `planV1 rejects schema v2 rather than dropping its lens payload`() {
        val backup = LayoutBackup(LayoutBackup.CURRENT_SCHEMA_VERSION, 0L, lenses = listOf(lens("default", 0)))
        try {
            LayoutImportApplier.planV1(backup)
            throw AssertionError("Expected schema mismatch rejection")
        } catch (expected: IllegalArgumentException) {
            assertEquals("planV1 requires schema 1, got 2", expected.message)
        }
    }

    @Test
    fun `planV2 preserves lens settings entries and uninstalled selections`() {
        val selectedId = "com.gone/com.gone.Main"
        val workLens = lens(
            id = "work", orderIndex = 1, scope = LensAppScope.SELECTED,
            selectedAppIds = setOf(selectedId), frozen = true,
            entries = listOf(entry("com.gone", 2))
        )
        val backup = LayoutBackup(
            LayoutBackup.CURRENT_SCHEMA_VERSION, 0L,
            lenses = listOf(lens("default", 0, entries = listOf(entry("com.a"))), workLens)
        )

        val plan = LayoutImportApplier.planV2(backup)

        assertEquals(2, plan.lenses.size)
        val restored = plan.lenses.single { it.id == "work" }
        assertEquals(1, restored.orderIndex)
        assertEquals(LensAppScope.SELECTED, restored.scope)
        assertEquals(setOf(selectedId), restored.selectedAppIds)
        assertEquals(true, restored.frozen)
        assertEquals(workLens.entries, restored.entries)
    }

    @Test
    fun `planV2 rejects schema v1 rather than dropping its flat entries`() {
        val backup = LayoutBackup(LayoutBackup.SCHEMA_VERSION_V1, 0L, entries = listOf(entry("com.a")))
        try {
            LayoutImportApplier.planV2(backup)
            throw AssertionError("Expected schema mismatch rejection")
        } catch (expected: IllegalArgumentException) {
            assertEquals("planV2 requires schema 2, got 1", expected.message)
        }
    }

    @Test
    fun `previewCounts deterministically totals lens entries`() {
        val plan = LayoutImportApplier.planV2(
            LayoutBackup(
                LayoutBackup.CURRENT_SCHEMA_VERSION, 0L,
                lenses = listOf(
                    lens("default", 0, entries = listOf(entry("com.a"))),
                    lens("work", 1, entries = listOf(entry("com.b"), entry("com.c")))
                )
            )
        )

        val counts = LayoutImportApplier.previewCounts(plan)
        assertEquals(counts, LayoutImportApplier.previewCounts(plan))
        assertEquals(2, counts.lensCount)
        assertEquals(3, counts.totalEntryCount)
        assertEquals(mapOf("default" to 1, "work" to 2), counts.perLensEntryCounts)
    }

    @Test
    fun `previewCounts reports one default lens and every v1 entry`() {
        val backup = LayoutBackup(LayoutBackup.SCHEMA_VERSION_V1, 0L, entries = listOf(entry("com.a"), entry("com.b")))
        val counts = LayoutImportApplier.previewCounts(LayoutImportApplier.planV1(backup))
        assertEquals(1, counts.lensCount)
        assertEquals(2, counts.totalEntryCount)
        assertEquals(mapOf(LensWorkspace.DEFAULT_LENS_ID to 2), counts.perLensEntryCounts)
    }

    @Test
    fun `previewCounts rejects duplicate lens IDs instead of undercounting`() {
        val duplicatePlan = LayoutImportApplier.WorkspacePlan(
            schemaVersion = LayoutBackup.CURRENT_SCHEMA_VERSION,
            lenses = listOf(
                LayoutImportApplier.WorkspaceLensPlan("work", "Work", 0, LensAppScope.ALL, emptySet(), false, listOf(entry("com.a"))),
                LayoutImportApplier.WorkspaceLensPlan("work", "Work copy", 1, LensAppScope.ALL, emptySet(), false, listOf(entry("com.b")))
            )
        )
        try {
            LayoutImportApplier.previewCounts(duplicatePlan)
            throw AssertionError("Expected duplicate lens rejection")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Workspace plan contains duplicate lens IDs", expected.message)
        }
    }
}
