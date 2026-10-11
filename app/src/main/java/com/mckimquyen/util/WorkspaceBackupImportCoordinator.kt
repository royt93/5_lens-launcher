package com.mckimquyen.util

import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupParseResult
import kotlinx.coroutines.CancellationException

interface WorkspaceBackupImportStore {
    suspend fun snapshotDatabase(): Any
    fun snapshotPreferences(): Any
    suspend fun replaceDatabase(plan: LayoutImportApplier.WorkspacePlan)
    fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan)
    suspend fun restoreDatabase(snapshot: Any)
    fun restorePreferences(snapshot: Any): Boolean
    suspend fun refreshWorkspace()
}

sealed class WorkspaceBackupImportResult {
    data object Applied : WorkspaceBackupImportResult()
    data object Malformed : WorkspaceBackupImportResult()
    data class UnsupportedSchema(val version: Int) : WorkspaceBackupImportResult()
    data class Failed(val cause: Throwable) : WorkspaceBackupImportResult()
    data class RecoveryRequired(val cause: Throwable, val rollbackFailure: Throwable) : WorkspaceBackupImportResult()
}

class WorkspaceBackupImportCoordinator(private val store: WorkspaceBackupImportStore) {
    suspend fun apply(backup: LayoutBackup): WorkspaceBackupImportResult {
        val plan = try {
            when (backup.schemaVersion) {
                LayoutBackup.SCHEMA_VERSION_V1 -> LayoutImportApplier.planV1(backup)
                LayoutBackup.CURRENT_SCHEMA_VERSION -> LayoutImportApplier.planV2(backup)
                else -> return WorkspaceBackupImportResult.UnsupportedSchema(backup.schemaVersion)
            }
        } catch (error: IllegalArgumentException) {
            return WorkspaceBackupImportResult.Malformed
        }

        if (backup.schemaVersion == LayoutBackup.SCHEMA_VERSION_V1) {
            return try {
                store.replaceDatabase(plan)
                store.refreshWorkspace()
                WorkspaceBackupImportResult.Applied
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                WorkspaceBackupImportResult.Failed(error)
            }
        }

        val databaseSnapshot: Any
        val preferenceSnapshot: Any
        try {
            databaseSnapshot = store.snapshotDatabase()
            preferenceSnapshot = store.snapshotPreferences()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return WorkspaceBackupImportResult.Failed(error)
        }

        var databaseReplaced = false
        try {
            store.replaceDatabase(plan)
            databaseReplaced = true
            store.replacePreferences(plan)
            store.refreshWorkspace()
            return WorkspaceBackupImportResult.Applied
        } catch (cancelled: CancellationException) {
            if (databaseReplaced) rollback(databaseSnapshot, preferenceSnapshot, cancelled)
            throw cancelled
        } catch (error: Exception) {
            if (!databaseReplaced) return WorkspaceBackupImportResult.Failed(error)
            return rollback(databaseSnapshot, preferenceSnapshot, error)
        }
    }

    private suspend fun rollback(databaseSnapshot: Any, preferenceSnapshot: Any, cause: Throwable): WorkspaceBackupImportResult {
        return try {
            store.restoreDatabase(databaseSnapshot)
            if (!store.restorePreferences(preferenceSnapshot)) throw IllegalStateException("Unable to restore preferences")
            WorkspaceBackupImportResult.Failed(cause)
        } catch (rollbackFailure: Exception) {
            runCatching { Logger.e("WorkspaceBackupImportCoordinator: rollback failed", rollbackFailure) }
            WorkspaceBackupImportResult.RecoveryRequired(cause, rollbackFailure)
        }
    }
}
