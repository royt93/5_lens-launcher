package com.mckimquyen.util

import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupEntry
import com.mckimquyen.model.LayoutBackupLens
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.PinnedZone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** DB-009: a checkpoint is taken before a destructive import and used when the rollback itself fails. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class WorkspaceImportRecoveryTest {

    @get:Rule val temp = TemporaryFolder()

    private var now = 1_700_000_000_000L
    private val dir get() = File(temp.root, "layout_backups")
    private val backups get() = LocalWorkspaceBackups(dir, { now })

    private fun backup(lensName: String, schema: Int = LayoutBackup.CURRENT_SCHEMA_VERSION) = LayoutBackup(
        schema,
        0L,
        lenses = listOf(
            LayoutBackupLens(
                LensWorkspace.DEFAULT_LENS_ID, lensName, 0, LensAppScope.ALL, emptySet(), false,
                listOf(LayoutBackupEntry("com.a", "com.a.Main", 0, true, true, false, null, PinnedZone.NONE.name))
            )
        )
    )

    /** Records what the store was asked to write; failures are switched per attempt. */
    private class FakeStore : WorkspaceBackupImportStore {
        val events = mutableListOf<String>()
        val replacedLensNames = mutableListOf<String>()
        var failPreferencesOnce = false
        var failRollbackOnce = false
        /** 1-based call number of replaceDatabase from which every call throws (default: never). */
        var failReplaceFromCall = Int.MAX_VALUE
        private var replaceCalls = 0

        override suspend fun snapshotDatabase(): Any { events += "snapshot-db"; return "db" }
        override fun snapshotPreferences(): Any = "prefs"
        override suspend fun replaceDatabase(plan: LayoutImportApplier.WorkspacePlan) {
            events += "replace-db"
            replacedLensNames += plan.lenses.single().name
            if (++replaceCalls >= failReplaceFromCall) error("replace failed")
        }
        override suspend fun applyLegacyV1(plan: LayoutImportApplier.WorkspacePlan) { events += "v1" }
        override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) {
            events += "replace-prefs"
            if (failPreferencesOnce) { failPreferencesOnce = false; error("prefs failed") }
        }
        override suspend fun restoreDatabase(snapshot: Any) {
            events += "restore-db"
            if (failRollbackOnce) { failRollbackOnce = false; error("rollback failed") }
        }
        override fun restorePreferences(snapshot: Any): Boolean { events += "restore-prefs"; return true }
        override suspend fun refreshWorkspace() { events += "refresh" }
    }

    private fun recovery(store: FakeStore, current: suspend () -> String = { backup("current state").toJson() }) =
        WorkspaceImportRecovery(store, backups, current)

    @Test
    fun `a checkpoint of the current state is written after snapshots and before the destructive replace`() = runBlocking {
        val store = FakeStore()
        var checkpointAt = -1
        val result = recovery(store) {
            checkpointAt = store.events.size
            backup("current state").toJson()
        }.apply(backup("imported"))

        assertEquals(WorkspaceBackupImportResult.Applied, result)
        assertEquals(listOf("snapshot-db", "replace-db", "replace-prefs", "refresh"), store.events)
        assertEquals("checkpoint runs after the snapshot and before replace-db", 1, checkpointAt)
        assertEquals("current state", backups.latest()?.lenses?.single()?.name)
    }

    @Test
    fun `a malformed or unsupported file writes no checkpoint and rotates nothing out`() = runBlocking {
        val store = FakeStore()
        backups.save(backup("existing").toJson())

        val unsupported = recovery(store).apply(backup("x", schema = 999))

        assertTrue(unsupported is WorkspaceBackupImportResult.UnsupportedSchema)
        assertEquals(listOf("existing"), backups.list().map { requireNotNull(backups.read(it)).lenses.single().name })
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun `a clean rollback is reported as failed and does not touch the checkpoint recovery`() = runBlocking {
        val store = FakeStore().apply { failPreferencesOnce = true }

        val result = recovery(store).apply(backup("imported"))

        assertTrue(result is WorkspaceBackupImportResult.Failed)
        assertEquals("rollback worked, so no second replace", listOf("imported"), store.replacedLensNames)
    }

    @Test
    fun `when the rollback fails the checkpoint of this attempt is restored`() = runBlocking {
        val store = FakeStore().apply { failPreferencesOnce = true; failRollbackOnce = true }

        val result = recovery(store).apply(backup("imported"))

        assertTrue("expected Recovered but was $result", result is WorkspaceBackupImportResult.Recovered)
        assertEquals("imported first, then the checkpoint", listOf("imported", "current state"), store.replacedLensNames)
        assertEquals("the recovery write must not add another checkpoint", 1, backups.list().size)
    }

    @Test
    fun `recovery failing too keeps reporting the original recovery required`() = runBlocking {
        val store = FakeStore().apply { failPreferencesOnce = true; failRollbackOnce = true; failReplaceFromCall = 2 }

        val result = recovery(store).apply(backup("imported"))

        assertTrue("expected RecoveryRequired but was $result", result is WorkspaceBackupImportResult.RecoveryRequired)
    }

    @Test
    fun `a stale older backup is never used when this attempt could not write its checkpoint`() = runBlocking {
        backups.save(backup("stale from last week").toJson())
        val store = FakeStore().apply { failPreferencesOnce = true; failRollbackOnce = true }

        val result = recovery(store) { error("disk full") }.apply(backup("imported"))

        assertTrue("expected RecoveryRequired but was $result", result is WorkspaceBackupImportResult.RecoveryRequired)
        assertEquals("the stale backup must not be restored", listOf("imported"), store.replacedLensNames)
    }

    @Test
    fun `a checkpoint that cannot be written does not stop a normal import`() = runBlocking {
        val store = FakeStore()

        val result = recovery(store) { error("disk full") }.apply(backup("imported"))

        assertEquals(WorkspaceBackupImportResult.Applied, result)
        assertEquals(listOf("imported"), store.replacedLensNames)
    }

    @Test
    fun `repeated imports keep only the newest checkpoints`() = runBlocking {
        val store = FakeStore()
        repeat(LocalWorkspaceBackups.MAX_BACKUPS + 2) { i ->
            now += 1_000
            recovery(store) { backup("state-$i").toJson() }.apply(backup("imported-$i"))
        }

        val names = backups.list().map { requireNotNull(backups.read(it)).lenses.single().name }
        assertEquals(LocalWorkspaceBackups.MAX_BACKUPS, names.size)
        assertFalse(names.contains("state-0"))
        assertEquals("state-${LocalWorkspaceBackups.MAX_BACKUPS + 1}", names.first())
    }
}
