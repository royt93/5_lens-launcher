package com.mckimquyen.util

import com.mckimquyen.model.App
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupEntry
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
            AppPersistent.setAppOrderBatch(orderedApps)
        }
        plan.matched.forEach { (entry, app) ->
            val packageName = app.packageName.toString()
            val name = app.name.toString()
            AppPersistent.setAppVisibility(packageName, name, entry.appVisible)
            AppPersistent.setAppOpened(packageName, name, entry.appOpened)
            AppPersistent.setOrganization(
                packageName,
                name,
                entry.isFavorite,
                entry.folderName,
                PinnedZone.fromStored(entry.pinnedZone)
            )
        }
    }
}
