package com.mckimquyen.util

import com.mckimquyen.model.App
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupEntry
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.PinnedZone

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
     * Reuses [AppPersistent]'s existing single-app persistence functions in a loop - the same
     * codepath a user flipping each switch by hand would trigger (including the
     * RAppsSingleton sync + AppEventManager notify each one already does) - rather than a new
     * bulk-write query that would need its own side-effect wiring duplicated.
     */
    fun apply(plan: Plan) {
        val orderedApps = plan.matched.sortedBy { it.entry.orderNumber }.map { it.app }
        if (orderedApps.isNotEmpty()) {
            // FISH-008 Phase 2: installedApps passed into plan() already came from one active
            // lens's merged list, so every matched app shares the same lensId.
            AppPersistent.setAppOrderBatch(orderedApps, orderedApps.first().lensId)
        }
        plan.matched.forEach { (entry, app) ->
            val packageName = app.packageName.toString()
            val name = app.name.toString()
            AppPersistent.setAppVisibility(packageName, name, entry.appVisible, app.lensId)
            AppPersistent.setAppOpened(packageName, name, entry.appOpened, app.lensId)
            AppPersistent.setOrganization(
                packageName,
                name,
                entry.isFavorite,
                entry.folderName,
                PinnedZone.fromStored(entry.pinnedZone),
                app.lensId
            )
        }
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
        val entries: List<LayoutBackupEntry>
    )

    /** Pure plan for a full workspace restore - no Context/PackageManager/Room/prefs/IO. */
    data class WorkspacePlan(val schemaVersion: Int, val lenses: List<WorkspaceLensPlan>)

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
        lenses = listOf(
            WorkspaceLensPlan(
                id = LensWorkspace.DEFAULT_LENS_ID,
                name = LensWorkspace.DEFAULT_LENS_NAME,
                orderIndex = 0,
                scope = LensAppScope.ALL,
                selectedAppIds = emptySet(),
                frozen = false,
                entries = backup.entries
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
        lenses = backup.lenses.map {
            WorkspaceLensPlan(it.id, it.name, it.orderIndex, it.scope, it.selectedAppIds, it.frozen, it.entries)
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
