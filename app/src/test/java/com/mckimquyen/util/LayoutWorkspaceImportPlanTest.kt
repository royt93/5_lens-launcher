package com.mckimquyen.util

import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupEntry
import com.mckimquyen.model.LayoutBackupLens
import com.mckimquyen.model.LayoutBackupParseResult
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.PinnedZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * workspace-backup-v2 Task 2: [LayoutImportApplier.planV1]/[LayoutImportApplier.planV2] turn an
 * already-parsed [LayoutBackup] into an immutable [LayoutImportApplier.WorkspacePlan] with no
 * Android/Room/PackageManager/IO dependency - the actual DB/prefs replacement is a later task.
 * Deliberately does not filter by installed apps: absent-package entries/selections must survive
 * into the plan so Task 3's Room transaction can still restore them (invisible until reinstalled).
 */
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
    fun `planV1 targets only the default lens id`() {
        val backup = LayoutBackup(LayoutBackup.SCHEMA_VERSION_V1, 0L, entries = listOf(entry("com.a")))

        val plan = LayoutImportApplier.planV1(backup)

        assertEquals(1, plan.lenses.size)
        assertEquals(LensWorkspace.DEFAULT_LENS_ID, plan.lenses.single().id)
        assertEquals(listOf(entry("com.a")), plan.lenses.single().entries)
    }

    @Test
    fun `planV2 preserves every lens id, order, settings and entries`() {
        val defaultLens = lens(
            id = LensWorkspace.DEFAULT_LENS_ID,
            orderIndex = 0,
            scope = LensAppScope.ALL,
            entries = listOf(entry("com.a", 0))
        )
        val workLens = lens(
            id = "work",
            orderIndex = 1,
            scope = LensAppScope.SELECTED,
            selectedAppIds = setOf("com.b/com.b.Main"),
            frozen = true,
            entries = listOf(entry("com.b", 0), entry("com.c", 1))
        )
        val backup = LayoutBackup(LayoutBackup.CURRENT_SCHEMA_VERSION, 0L, lenses = listOf(defaultLens, workLens))

        val plan = LayoutImportApplier.planV2(backup)

        assertEquals(2, plan.lenses.size)
        val restoredWork = plan.lenses.single { it.id == "work" }
        assertEquals(1, restoredWork.orderIndex)
        assertEquals(LensAppScope.SELECTED, restoredWork.scope)
        assertEquals(setOf("com.b/com.b.Main"), restoredWork.selectedAppIds)
        assertTrue(restoredWork.frozen)
        assertEquals(workLens.entries, restoredWork.entries)
    }

    @Test
    fun `planV2 retains entries and selection for apps not currently installed`() {
        // Task 2 is pure: it never consults PackageManager/an installed-app list, so an entry or
        // selected id for an uninstalled package is carried through unchanged, not dropped.
        val uninstalled = lens(
            id = LensWorkspace.DEFAULT_LENS_ID,
            orderIndex = 0,
            scope = LensAppScope.SELECTED,
            selectedAppIds = setOf("com.gone/com.gone.Main"),
            entries = listOf(entry("com.gone"))
        )
        val backup = LayoutBackup(LayoutBackup.CURRENT_SCHEMA_VERSION, 0L, lenses = listOf(uninstalled))

        val plan = LayoutImportApplier.planV2(backup)

        val restored = plan.lenses.single()
        assertEquals(setOf("com.gone/com.gone.Main"), restored.selectedAppIds)
        assertEquals(1, restored.entries.size)
    }

    @Test
    fun `previewCounts is deterministic and sums per-lens entry counts`() {
        val backup = LayoutBackup(
            LayoutBackup.CURRENT_SCHEMA_VERSION, 0L,
            lenses = listOf(
                lens(LensWorkspace.DEFAULT_LENS_ID, 0, entries = listOf(entry("com.a"))),
                lens("work", 1, entries = listOf(entry("com.b"), entry("com.c")))
            )
        )
        val plan = LayoutImportApplier.planV2(backup)

        val first = LayoutImportApplier.previewCounts(plan)
        val second = LayoutImportApplier.previewCounts(plan)

        assertEquals(first, second)
        assertEquals(2, first.lensCount)
        assertEquals(3, first.totalEntryCount)
        assertEquals(1, first.perLensEntryCounts[LensWorkspace.DEFAULT_LENS_ID])
        assertEquals(2, first.perLensEntryCounts["work"])
    }

    @Test
    fun `previewCounts on a v1 plan reports the single default lens`() {
        val backup = LayoutBackup(
            LayoutBackup.SCHEMA_VERSION_V1, 0L,
            entries = listOf(entry("com.a"), entry("com.b"))
        )
        val counts = LayoutImportApplier.previewCounts(LayoutImportApplier.planV1(backup))

        assertEquals(1, counts.lensCount)
        assertEquals(2, counts.totalEntryCount)
    }

    @Test
    fun `malformed and unsupported-schema parse results never yield a backup to plan from`() {
        val malformed = LayoutBackup.parse("not json")
        val unsupported = LayoutBackup.parse(
            """{"schemaVersion": ${LayoutBackup.CURRENT_SCHEMA_VERSION + 1}, "exportedAt": 0}"""
        )

        assertTrue(malformed is LayoutBackupParseResult.Malformed)
        assertTrue(unsupported is LayoutBackupParseResult.UnsupportedSchemaVersion)
        // Neither arm exposes a LayoutBackup - planV1/planV2 take a LayoutBackup directly, so
        // there is no way to reach them from a failed parse without a compile error.
        assertFalse(malformed is LayoutBackupParseResult.Success)
        assertFalse(unsupported is LayoutBackupParseResult.Success)
    }
}
