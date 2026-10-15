package com.mckimquyen.util

import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupEntry
import com.mckimquyen.model.LayoutBackupLens
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.PinnedZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceBackupImportCoordinatorTest {
    private val backup = LayoutBackup(
        LayoutBackup.CURRENT_SCHEMA_VERSION,
        0L,
        lenses = listOf(
            LayoutBackupLens(
                LensWorkspace.DEFAULT_LENS_ID,
                "Home",
                0,
                LensAppScope.ALL,
                emptySet(),
                false,
                listOf(LayoutBackupEntry("com.a", "com.a.Main", 0, true, true, false, null, PinnedZone.NONE.name)),
                "Work"
            )
        ),
        collapsedGroupNames = setOf("Work")
    )

    @Test
    fun `applies database before preferences then refreshes`() {
        val events = mutableListOf<String>()
        val store = object : WorkspaceBackupImportStore {
            override suspend fun snapshotDatabase(): Any { events += "snapshot-db"; return "db" }
            override fun snapshotPreferences(): Any { events += "snapshot-prefs"; return "prefs" }
            override suspend fun replaceDatabase(plan: LayoutImportApplier.WorkspacePlan) { events += "db" }
            override suspend fun applyLegacyV1(plan: LayoutImportApplier.WorkspacePlan) { events += "v1-${plan.lenses.single().id}" }
            override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) { events += "prefs" }
            override suspend fun restoreDatabase(snapshot: Any) { events += "restore-db" }
            override fun restorePreferences(snapshot: Any): Boolean { events += "restore-prefs"; return true }
            override suspend fun refreshWorkspace() { events += "refresh" }
        }
        val result = runBlocking { WorkspaceBackupImportCoordinator(store).apply(backup) }
        assertEquals(WorkspaceBackupImportResult.Applied, result)
        assertEquals(listOf("snapshot-db", "snapshot-prefs", "db", "prefs", "refresh"), events)
    }

    @Test
    fun `plan preserves group labels and collapsed state`() {
        val events = mutableListOf<String>()
        val store = object : WorkspaceBackupImportStore {
            override suspend fun snapshotDatabase(): Any = "db"
            override fun snapshotPreferences(): Any = "prefs"
            override suspend fun replaceDatabase(plan: LayoutImportApplier.WorkspacePlan) {
                events += "group=${plan.lenses.single().groupName}"
            }
            override suspend fun applyLegacyV1(plan: LayoutImportApplier.WorkspacePlan) = Unit
            override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) {
                events += "collapsed=${plan.collapsedGroupNames}"
            }
            override suspend fun restoreDatabase(snapshot: Any) = Unit
            override fun restorePreferences(snapshot: Any): Boolean = true
            override suspend fun refreshWorkspace() = Unit
        }

        val result = runBlocking { WorkspaceBackupImportCoordinator(store).apply(backup) }

        assertEquals(WorkspaceBackupImportResult.Applied, result)
        assertEquals(listOf("group=Work", "collapsed=[Work]"), events)
    }

    @Test
    fun `preference failure restores both snapshots and reports failed`() {
        val events = mutableListOf<String>()
        val store = object : WorkspaceBackupImportStore {
            override suspend fun snapshotDatabase(): Any { events += "snapshot-db"; return "db" }
            override fun snapshotPreferences(): Any { events += "snapshot-prefs"; return "prefs" }
            override suspend fun replaceDatabase(plan: LayoutImportApplier.WorkspacePlan) { events += "db" }
            override suspend fun applyLegacyV1(plan: LayoutImportApplier.WorkspacePlan) { events += "v1-${plan.lenses.single().id}" }
            override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) { events += "prefs"; error("prefs failed") }
            override suspend fun restoreDatabase(snapshot: Any) { events += "restore-db" }
            override fun restorePreferences(snapshot: Any): Boolean { events += "restore-prefs"; return true }
            override suspend fun refreshWorkspace() { events += "refresh" }
        }
        val result = runBlocking { WorkspaceBackupImportCoordinator(store).apply(backup) }
        assertTrue(result is WorkspaceBackupImportResult.Failed)
        assertEquals(listOf("snapshot-db", "snapshot-prefs", "db", "prefs", "restore-db", "restore-prefs"), events)
    }

    @Test
    fun `database replacement failure skips preference write and refresh`() {
        val events = mutableListOf<String>()
        val store = object : WorkspaceBackupImportStore {
            override suspend fun snapshotDatabase(): Any { events += "snapshot-db"; return "db" }
            override fun snapshotPreferences(): Any { events += "snapshot-prefs"; return "prefs" }
            override suspend fun replaceDatabase(plan: LayoutImportApplier.WorkspacePlan) { events += "db"; error("db failure") }
            override suspend fun applyLegacyV1(plan: LayoutImportApplier.WorkspacePlan) { events += "v1" }
            override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) { events += "prefs" }
            override suspend fun restoreDatabase(snapshot: Any) { events += "restore-db" }
            override fun restorePreferences(snapshot: Any): Boolean { events += "restore-prefs"; return true }
            override suspend fun refreshWorkspace() { events += "refresh" }
        }

        val result = runBlocking { WorkspaceBackupImportCoordinator(store).apply(backup) }

        assertTrue(result is WorkspaceBackupImportResult.Failed)
        assertEquals(listOf("snapshot-db", "snapshot-prefs", "db"), events)
    }

    @Test
    fun `refresh failure rolls database and preferences back`() {
        val events = mutableListOf<String>()
        val store = object : WorkspaceBackupImportStore {
            override suspend fun snapshotDatabase(): Any { events += "snapshot-db"; return "db" }
            override fun snapshotPreferences(): Any { events += "snapshot-prefs"; return "prefs" }
            override suspend fun replaceDatabase(plan: LayoutImportApplier.WorkspacePlan) { events += "db" }
            override suspend fun applyLegacyV1(plan: LayoutImportApplier.WorkspacePlan) { events += "v1" }
            override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) { events += "prefs" }
            override suspend fun restoreDatabase(snapshot: Any) { events += "restore-db" }
            override fun restorePreferences(snapshot: Any): Boolean { events += "restore-prefs"; return true }
            override suspend fun refreshWorkspace() { events += "refresh"; error("refresh failure") }
        }

        val result = runBlocking { WorkspaceBackupImportCoordinator(store).apply(backup) }

        assertTrue(result is WorkspaceBackupImportResult.Failed)
        assertEquals(listOf("snapshot-db", "snapshot-prefs", "db", "prefs", "refresh", "restore-db", "restore-prefs"), events)
    }

    @Test
    fun `failed compensation after refresh failure reports recovery required`() {
        val store = object : WorkspaceBackupImportStore {
            override suspend fun snapshotDatabase(): Any = "db"
            override fun snapshotPreferences(): Any = "prefs"
            override suspend fun replaceDatabase(plan: LayoutImportApplier.WorkspacePlan) = Unit
            override suspend fun applyLegacyV1(plan: LayoutImportApplier.WorkspacePlan) = Unit
            override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) = Unit
            override suspend fun restoreDatabase(snapshot: Any) = error("restore failure")
            override fun restorePreferences(snapshot: Any): Boolean = false
            override suspend fun refreshWorkspace() = error("refresh failure")
        }

        val result = runBlocking { WorkspaceBackupImportCoordinator(store).apply(backup) }

        assertTrue(result is WorkspaceBackupImportResult.RecoveryRequired)
    }

    @Test
    fun `cancellation after database replacement compensates then propagates`() {
        val events = mutableListOf<String>()
        val store = object : WorkspaceBackupImportStore {
            override suspend fun snapshotDatabase(): Any { events += "snapshot-db"; return "db" }
            override fun snapshotPreferences(): Any { events += "snapshot-prefs"; return "prefs" }
            override suspend fun replaceDatabase(plan: LayoutImportApplier.WorkspacePlan) { events += "db" }
            override suspend fun applyLegacyV1(plan: LayoutImportApplier.WorkspacePlan) { events += "v1" }
            override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) {
                events += "prefs"
                throw CancellationException("cancel import")
            }
            override suspend fun restoreDatabase(snapshot: Any) { events += "restore-db" }
            override fun restorePreferences(snapshot: Any): Boolean { events += "restore-prefs"; return true }
            override suspend fun refreshWorkspace() { events += "refresh" }
        }

        try {
            runBlocking { WorkspaceBackupImportCoordinator(store).apply(backup) }
            throw AssertionError("Cancellation must propagate")
        } catch (expected: CancellationException) {
            assertEquals("cancel import", expected.message)
        }

        assertEquals(listOf("snapshot-db", "snapshot-prefs", "db", "prefs", "restore-db", "restore-prefs"), events)
    }

    @Test
    fun `v1 cancellation propagates without converting to an import result`() {
        val legacy = LayoutBackup(
            LayoutBackup.SCHEMA_VERSION_V1, 0L,
            entries = backup.lenses.single().entries
        )
        val store = object : WorkspaceBackupImportStore {
            override suspend fun snapshotDatabase(): Any = error("v1 must not snapshot")
            override fun snapshotPreferences(): Any = error("v1 must not snapshot")
            override suspend fun replaceDatabase(plan: LayoutImportApplier.WorkspacePlan) = Unit
            override suspend fun applyLegacyV1(plan: LayoutImportApplier.WorkspacePlan) {
                throw CancellationException("cancel v1")
            }
            override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) = Unit
            override suspend fun restoreDatabase(snapshot: Any) = Unit
            override fun restorePreferences(snapshot: Any): Boolean = true
            override suspend fun refreshWorkspace() = error("cancelled v1 must not refresh")
        }

        try {
            runBlocking { WorkspaceBackupImportCoordinator(store).apply(legacy) }
            throw AssertionError("V1 cancellation must propagate")
        } catch (expected: CancellationException) {
            assertEquals("cancel v1", expected.message)
        }
    }

    @Test
    fun `failed compensation reports recovery required`() {
        val store = object : WorkspaceBackupImportStore {
            override suspend fun snapshotDatabase(): Any = "db"
            override fun snapshotPreferences(): Any = "prefs"
            override suspend fun replaceDatabase(plan: LayoutImportApplier.WorkspacePlan) = Unit
            override suspend fun applyLegacyV1(plan: LayoutImportApplier.WorkspacePlan) = Unit
            override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) { error("prefs failed") }
            override suspend fun restoreDatabase(snapshot: Any) { error("db restore failed") }
            override fun restorePreferences(snapshot: Any): Boolean = false
            override suspend fun refreshWorkspace() = Unit
        }
        val result = runBlocking { WorkspaceBackupImportCoordinator(store).apply(backup) }
        assertTrue(result is WorkspaceBackupImportResult.RecoveryRequired)
    }
}
