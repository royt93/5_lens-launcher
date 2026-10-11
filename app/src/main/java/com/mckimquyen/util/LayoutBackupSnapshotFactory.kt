package com.mckimquyen.util

import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupEntry
import com.mckimquyen.model.LayoutBackupLens
import com.mckimquyen.model.LensWorkspace

object LayoutBackupSnapshotFactory {
    fun create(
        lenses: List<LensWorkspace>,
        rows: List<AppPersistent>,
        appSettings: List<LensAppSettingsBackup>,
        exportedAt: Long
    ): LayoutBackup {
        require(lenses.isNotEmpty()) { "Workspace is empty" }
        require(lenses.map { it.id }.toSet().size == lenses.size) { "Duplicate lens IDs" }
        require(lenses.count { it.id == LensWorkspace.DEFAULT_LENS_ID } == 1) { "Default lens is required" }
        val byLens = rows.groupBy { it.lensId }
        val settingsById = appSettings.associateBy { it.lensId }
        require(settingsById.size == appSettings.size && settingsById.keys == lenses.map { it.id }.toSet()) {
            "App settings must exist exactly once for every lens"
        }
        require(rows.all { it.lensId in settingsById }) { "App row references unknown lens" }
        return LayoutBackup(
            schemaVersion = LayoutBackup.CURRENT_SCHEMA_VERSION,
            exportedAt = exportedAt,
            lenses = lenses.sortedWith(compareBy<LensWorkspace> { it.orderIndex }.thenBy { it.createdAt }).map { lens ->
                val settings = settingsById.getValue(lens.id)
                val entries = byLens[lens.id].orEmpty().mapNotNull(LayoutBackupEntry::fromPersistent)
                require(entries.map { AppPersistent.generateIdentifier(it.packageName, it.name) }.toSet().size == entries.size) {
                    "Duplicate app identity in lens ${lens.id}"
                }
                LayoutBackupLens(lens.id, lens.name, lens.orderIndex, settings.scope, settings.selectedAppIds, settings.frozen, entries)
            }
        )
    }
}
