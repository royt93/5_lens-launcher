package com.mckimquyen.util

import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupEntry
import com.mckimquyen.model.LayoutBackupLens
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.PinnedZone
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
                listOf(LayoutBackupEntry("com.a", "com.a.Main", 0, true, true, false, null, PinnedZone.NONE.name))
            )
        )
    )

    @Test
    fun `applies database before preferences then refreshes`() {
        val events = mutableListOf<String>()
        val store = object : WorkspaceBackupImportStore {
            override suspend fun snapshotDatabase(): Any { events += "snapshot-db"; return "db" }
            override fun snapshotPreferences(): Any { events += "snapshot-prefs"; return "prefs" }
            override suspend fun replaceDatabase(plan: LayoutImportApplier.WorkspacePlan) { events += "db" }
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
    fun `preference failure restores both snapshots and reports failed`() {
        val events = mutableListOf<String>()
        val store = object : WorkspaceBackupImportStore {
            override suspend fun snapshotDatabase(): Any { events += "snapshot-db"; return "db" }
            override fun snapshotPreferences(): Any { events += "snapshot-prefs"; return "prefs" }
            override suspend fun replaceDatabase(plan: LayoutImportApplier.WorkspacePlan) { events += "db" }
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
    fun `failed compensation reports recovery required`() {
        val store = object : WorkspaceBackupImportStore {
            override suspend fun snapshotDatabase(): Any = "db"
            override fun snapshotPreferences(): Any = "prefs"
            override suspend fun replaceDatabase(plan: LayoutImportApplier.WorkspacePlan) = Unit
            override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) { error("prefs failed") }
            override suspend fun restoreDatabase(snapshot: Any) { error("db restore failed") }
            override fun restorePreferences(snapshot: Any): Boolean = false
            override suspend fun refreshWorkspace() = Unit
        }
        val result = runBlocking { WorkspaceBackupImportCoordinator(store).apply(backup) }
        assertTrue(result is WorkspaceBackupImportResult.RecoveryRequired)
    }
}
