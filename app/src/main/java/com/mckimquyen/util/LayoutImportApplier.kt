package com.mckimquyen.util

import com.mckimquyen.model.App
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupEntry
import com.mckimquyen.model.LensWorkspace

/**
 * FEAT-006: matches an imported [LayoutBackup] against currently-installed apps by the same
 * component identity [AppPersistent.generateIdentifier] already uses (packageName + component
 * name, not the user-facing label) - apps not currently installed are skipped, never erroring the
 * whole import.
 */
object LayoutImportApplier {

    data class MatchedEntry(val entry: LayoutBackupEntry, val app: App)

    data class Plan(val matched: List<MatchedEntry>, val skippedCount: Int)

    fun plan(backup: LayoutBackup, installedApps: List<App>): Plan {
        val byIdentity = installedApps.associateBy {
            AppPersistent.generateIdentifier(it.packageName.toString(), it.name.toString())
        }
        val matched = mutableListOf<MatchedEntry>()
        var skipped = 0
        backup.entries.forEach { entry ->
            val identity = AppPersistent.generateIdentifier(entry.packageName, entry.name)
            val app = byIdentity[identity]
            if (app != null) matched.add(MatchedEntry(entry, app)) else skipped++
        }
        return Plan(matched, skipped)
    }

    /**
     * workspace-backup-v2 Task 2: one lens's restorable state, carried verbatim from the parsed
     * backup - [scope]/[selectedAppIds]/[frozen] are meaningless for a v1-sourced plan (v1 has no
     * per-lens settings) and callers must ignore them there; [WorkspacePlan.schemaVersion] is what
     * tells a consumer (Task 3/4) which fields apply.
     */
    data class WorkspaceLensPlan(
        val id: String,
        val name: String,
        val orderIndex: Int,
        val scope: LensAppScope,
        val selectedAppIds: Set<String>,
        val frozen: Boolean,
        val entries: List<LayoutBackupEntry>,
        val groupName: String? = null,
        val temporarilyHiddenAppIds: Set<String> = emptySet()
    ) {
        fun toAppSettingsBackup() = LensAppSettingsBackup(id, scope, selectedAppIds, frozen, temporarilyHiddenAppIds)
    }

    /** Pure plan for a full workspace restore - no Context/PackageManager/Room/prefs/IO. */
    data class WorkspacePlan(
        val schemaVersion: Int,
        val lenses: List<WorkspaceLensPlan>,
        val activeLensId: String,
        val collapsedGroupNames: Set<String> = emptySet()
    )

    data class PreviewCounts(
        val lensCount: Int,
        val totalEntryCount: Int,
        val perLensEntryCounts: Map<String, Int>
    )

    /** v1 has only ever applied to the default lens; this plan is intentionally always one lens. */
    fun planV1(backup: LayoutBackup): WorkspacePlan {
        require(backup.schemaVersion == LayoutBackup.SCHEMA_VERSION_V1) {
            "planV1 requires schema ${LayoutBackup.SCHEMA_VERSION_V1}, got ${backup.schemaVersion}"
        }
        return WorkspacePlan(
        schemaVersion = LayoutBackup.SCHEMA_VERSION_V1,
        activeLensId = LensWorkspace.DEFAULT_LENS_ID,
        lenses = listOf(
            WorkspaceLensPlan(
                id = LensWorkspace.DEFAULT_LENS_ID,
                name = LensWorkspace.DEFAULT_LENS_NAME,
                orderIndex = 0,
                scope = LensAppScope.ALL,
                selectedAppIds = emptySet(),
                frozen = false,
                entries = backup.entries,
                groupName = null
            )
        )
        )
    }

    /**
     * Carries every parsed lens through unchanged, including entries/selections for packages not
     * currently installed - Task 2 never consults an installed-app list, so there is nothing to
     * filter against.
     */
    fun planV2(backup: LayoutBackup): WorkspacePlan {
        require(backup.schemaVersion == LayoutBackup.CURRENT_SCHEMA_VERSION) {
            "planV2 requires schema ${LayoutBackup.CURRENT_SCHEMA_VERSION}, got ${backup.schemaVersion}"
        }
        return WorkspacePlan(
        schemaVersion = backup.schemaVersion,
        activeLensId = backup.activeLensId ?: LensWorkspace.DEFAULT_LENS_ID,
        collapsedGroupNames = backup.collapsedGroupNames,
        lenses = backup.lenses.map {
            WorkspaceLensPlan(it.id, it.name, it.orderIndex, it.scope, it.selectedAppIds, it.frozen, it.entries, it.groupName, it.temporarilyHiddenAppIds)
        }
        )
    }

    /** Deterministic: a pure fold over [plan], safe to call repeatedly for preview re-renders. */
    fun previewCounts(plan: WorkspacePlan): PreviewCounts {
        val perLens = plan.lenses.associate { it.id to it.entries.size }
        require(perLens.size == plan.lenses.size) { "Workspace plan contains duplicate lens IDs" }
        return PreviewCounts(
            lensCount = plan.lenses.size,
            totalEntryCount = plan.lenses.sumOf { it.entries.size },
            perLensEntryCounts = perLens
        )
    }
}
