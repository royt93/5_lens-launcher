package com.mckimquyen.util

import android.content.Context
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LensWorkspace

class WorkspaceBackupRoomSnapshot(val lenses: List<LensWorkspace>, val rows: List<AppPersistent>)

class WorkspaceBackupRoomStore(context: Context) : WorkspaceBackupImportStore {
    private val database = AppDatabase.getInstance()
    private val settings = UtilSettings(context.applicationContext)

    override suspend fun snapshotDatabase(): Any = WorkspaceBackupRoomSnapshot(
        database.lensWorkspaceDao().getAll(),
        database.appPersistentDao().getAllRows()
    )

    override fun snapshotPreferences(): Any = settings.snapshotWorkspacePreferences()

    override suspend fun replaceDatabase(plan: LayoutImportApplier.WorkspacePlan) {
        require(plan.schemaVersion == LayoutBackup.CURRENT_SCHEMA_VERSION)
        database.lensWorkspaceDao().replaceWorkspace(
            plan.lenses.map { LensWorkspace(it.id, it.name, it.orderIndex) },
            plan.lenses.flatMap { lens ->
                lens.entries.map { entry ->
                    AppPersistent(
                        packageName = entry.packageName,
                        name = entry.name,
                        identifier = AppPersistent.generateIdentifier(entry.packageName, entry.name),
                        orderNumber = entry.orderNumber,
                        appVisible = entry.appVisible,
                        appOpened = entry.appOpened,
                        isFavorite = entry.isFavorite,
                        folderName = entry.folderName,
                        pinnedZone = entry.pinnedZone,
                        lensId = lens.id
                    )
                }
            }
        )
    }

    override fun replacePreferences(plan: LayoutImportApplier.WorkspacePlan) {
        require(plan.schemaVersion == LayoutBackup.CURRENT_SCHEMA_VERSION)
        check(settings.replaceWorkspacePreferences(
            plan.lenses.map { LensAppSettingsBackup(it.id, it.scope, it.selectedAppIds, it.frozen) },
            plan.activeLensId
        )) { "Unable to commit workspace preferences" }
    }

    override suspend fun restoreDatabase(snapshot: Any) {
        val previous = snapshot as WorkspaceBackupRoomSnapshot
        database.lensWorkspaceDao().replaceWorkspace(previous.lenses, previous.rows)
    }

    override fun restorePreferences(snapshot: Any): Boolean =
        settings.restoreWorkspacePreferences(snapshot as WorkspacePreferencesSnapshot)

    override suspend fun refreshWorkspace() {
        com.mckimquyen.services.AppEventManager.notifyAppsEdited()
        com.mckimquyen.services.AppEventManager.notifyLensScopeChanged()
    }
}
