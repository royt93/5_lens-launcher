package com.mckimquyen.search

import com.mckimquyen.model.App

/**
 * FEAT-009: resolves the recent-apps panel's row list from SearchHistoryStore's stored component
 * keys against the live app snapshot. Pure logic, independent of Android framework/Context, fully
 * unit-testable - same shape as util.LensLabelResolver. Keys for an uninstalled/updated app (no
 * longer in the snapshot) are silently dropped rather than shown as a "ghost" row.
 */
object RecentAppsPanelResolver {

    @JvmStatic
    fun resolve(recentKeys: List<String>, snapshot: List<App>): List<App> {
        val byKey = snapshot.associateBy { AppSearchEngine.componentKey(it) }
        return recentKeys.mapNotNull { byKey[it] }
    }
}
