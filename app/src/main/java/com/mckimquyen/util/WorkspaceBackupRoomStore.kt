package com.mckimquyen.util

import android.content.Context
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LensWorkspace

class WorkspaceBackupRoomSnapshot(val lenses: List<LensWorkspace>, val rows: List<AppPersistent>)

class WorkspaceBackupRoomStore(context: Context) : WorkspaceBackupImportStore {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance()
    private val settings = UtilSettings(appContext)

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

    override suspend fun applyLegacyV1(plan: LayoutImportApplier.WorkspacePlan) {
        require(plan.schemaVersion == LayoutBackup.SCHEMA_VERSION_V1)
        require(plan.lenses.size == 1) { "V1 backup must contain exactly one default lens" }
        require(plan.lenses.single().id == LensWorkspace.DEFAULT_LENS_ID) {
            "V1 backup must target the default lens"
        }
        val dao = database.appPersistentDao()
        val allRows = dao.getAllRows()
        val oldDefault = allRows.filter { it.lensId == LensWorkspace.DEFAULT_LENS_ID }
        val preservedByIdentifier = allRows.associateBy { it.identifier }
        val installedIds = com.mckimquyen.app.RAppsSingleton.instance.apps.orEmpty().mapTo(mutableSetOf()) {
            AppPersistent.generateIdentifier(it.packageName?.toString(), it.name?.toString())
        }
        val installedEntries = plan.lenses.single().entries.filter { entry ->
            AppPersistent.generateIdentifier(entry.packageName, entry.name) in installedIds
        }
        val imported = installedEntries.map { entry ->
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
                lensId = LensWorkspace.DEFAULT_LENS_ID
            )
        }
        val importedIds = imported.mapTo(mutableSetOf()) { it.identifier }
        val merged = imported.map { row ->
            val old = preservedByIdentifier[row.identifier]
            row.copy(
                openCount = old?.openCount ?: row.openCount,
                paletteColor = old?.paletteColor ?: row.paletteColor,
                notificationCount = old?.notificationCount ?: row.notificationCount
            )
        } + oldDefault.filter { it.identifier !in importedIds }
        dao.replaceDefaultLensLayout(merged)
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
        LensShortcuts.refresh(appContext, database.lensWorkspaceDao().getAll())
    }
}
