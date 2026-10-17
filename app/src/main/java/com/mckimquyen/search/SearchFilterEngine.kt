package com.mckimquyen.search

import com.mckimquyen.model.App
import com.mckimquyen.model.SearchFilterPreset
import com.mckimquyen.util.LensAppScope

object SearchFilterEngine {

    @JvmStatic
    fun filterCandidates(
        apps: List<App>,
        preset: SearchFilterPreset,
        lensApps: List<App>?,
        hiddenIds: Set<String>
    ): List<App> {
        return when (preset) {
            SearchFilterPreset.ALL -> ArrayList(apps)
            SearchFilterPreset.THIS_LENS -> {
                if (lensApps.isNullOrEmpty()) return emptyList()
                val lensKeys = lensApps.mapTo(HashSet(), LensAppScope::identifierOf)
                ArrayList(apps.filter { LensAppScope.identifierOf(it) in lensKeys })
            }
            SearchFilterPreset.FAVORITES -> {
                ArrayList(apps.filter { it.isFavorite })
            }
            SearchFilterPreset.HIDDEN -> {
                if (hiddenIds.isEmpty()) return emptyList()
                ArrayList(apps.filter { LensAppScope.identifierOf(it) in hiddenIds })
            }
        }
    }
}
