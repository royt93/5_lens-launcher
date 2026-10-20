package com.mckimquyen.search

import com.mckimquyen.model.App

/**
 * FEAT-009: resolves the recent-apps panel's row list from SearchHistoryStore's stored component
 * keys against the live app snapshot. Pure logic, independent of Android framework/Context, fully
 * unit-testable - same shape as util.LensLabelResolver. Keys for an uninstalled/updated app (no
 * longer in the snapshot) are silently dropped rather than shown as a "ghost" row, and so are
 * hidden apps (SEARCH-011).
 */
object RecentAppsPanelResolver {

    @JvmStatic
    fun resolve(recentKeys: List<String>, snapshot: List<App>): List<App> {
        // SEARCH-011: same rule as AppSearchEngine and T9SearchEngine - an app the user hid
        // (isVisible=false) must not surface here, even though its key is still in the history.
        val byKey = snapshot.filter(App::isVisible).associateBy { AppSearchEngine.componentKey(it) }
        return recentKeys.mapNotNull { byKey[it] }
    }
}
