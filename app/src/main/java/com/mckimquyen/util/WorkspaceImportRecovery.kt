package com.mckimquyen.util

import com.mckimquyen.model.LayoutBackup
import kotlinx.coroutines.CancellationException

/**
 * DB-009: puts a safety net under [WorkspaceBackupImportCoordinator].
 *
 * Right after the coordinator has snapshotted the current workspace, and before anything is replaced, the current
 * state is also written to [LocalWorkspaceBackups]. If the import then fails *and* the coordinator's own rollback
 * fails ([WorkspaceBackupImportResult.RecoveryRequired]), that checkpoint is applied instead.
 *
 * Only the checkpoint written by this very attempt is ever used: an older file on disk may describe a workspace the
 * user has since changed, so restoring it would silently lose newer work.
 */
class WorkspaceImportRecovery(
    private val store: WorkspaceBackupImportStore,
    private val backups: LocalWorkspaceBackups,
    private val currentStateJson: suspend () -> String
) {
    suspend fun apply(backup: LayoutBackup): WorkspaceBackupImportResult {
        var checkpoint: LayoutBackup? = null
        val guardedStore = object : WorkspaceBackupImportStore by store {
            override suspend fun snapshotDatabase(): Any {
                val snapshot = store.snapshotDatabase()
                checkpoint = writeCheckpoint()
                return snapshot
            }
        }

        val result = WorkspaceBackupImportCoordinator(guardedStore).apply(backup)
        if (result !is WorkspaceBackupImportResult.RecoveryRequired) return result
        val saved = checkpoint ?: return result
        return restore(saved, result)
    }

    /** Writes the checkpoint and returns what was read back from disk, or null if it could not be made. */
    private suspend fun writeCheckpoint(): LayoutBackup? = try {
        backups.save(currentStateJson())?.let(backups::read)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Logger.e("WorkspaceImportRecovery: checkpoint failed, import continues without one", error)
        null
    }

    private suspend fun restore(
        saved: LayoutBackup,
        original: WorkspaceBackupImportResult.RecoveryRequired
    ): WorkspaceBackupImportResult = try {
        val plan = LayoutImportApplier.planV2(saved)
        store.replaceDatabase(plan)
        store.replacePreferences(plan)
        store.refreshWorkspace()
        WorkspaceBackupImportResult.Recovered(original.cause)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Logger.e("WorkspaceImportRecovery: restoring the checkpoint failed", error)
        original
    }
}
