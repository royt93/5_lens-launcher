package com.mckimquyen.util

import com.mckimquyen.model.App
import kotlin.math.abs

/**
 * FISH-006: Pure algorithmic helper to arrange apps such that frequently used apps
 * (higher [App.openCount]) are biased toward the center (focus area) of the equispaced grid.
 *
 * Guarantees:
 * 1. Pure function with no side effects or external dependencies.
 * 2. If disabled, empty, or all apps have 0 opens (fresh install): degrades to exact identity.
 * 3. Does not alter grid geometry, preserving equispaced calculations and 120Hz caching.
 */
object SmartFocusArranger {

    fun arrange(
        apps: List<App>,
        cols: Int,
        rows: Int,
        smartFocusEnabled: Boolean
    ): ArrayList<App> {
        if (!smartFocusEnabled || apps.isEmpty() || cols <= 0 || rows <= 0) {
            return ArrayList(apps)
        }

        // If no apps have any opens, or all apps share the exact same open count:
        // degrade to exact identity order per acceptance criteria.
        val firstCount = apps.first().openCount
        val hasVariation = apps.any { it.openCount != firstCount }
        if (!hasVariation || apps.none { it.openCount > 0L }) {
            return ArrayList(apps)
        }

        val n = apps.size
        val usedRows = (n + cols - 1) / cols
        val cx = (cols - 1) / 2.0f
        val cy = (usedRows - 1) / 2.0f

        // Rank slot indices [0 until n] by distance to center ascending
        val slots = (0 until n).sortedWith(
            compareBy<Int> { slot ->
                val col = slot % cols
                val row = slot / cols
                val dx = col - cx
                val dy = row - cy
                dx * dx + dy * dy
            }.thenBy { slot ->
                abs((slot / cols) - cy)
            }.thenBy { slot ->
                abs((slot % cols) - cx)
            }.thenBy { slot ->
                slot
            }
        )

        // Separate apps: opened apps sorted by open count desc, unopened apps in original order
        val indexedApps = apps.withIndex().toList()
        val opened = indexedApps
            .filter { it.value.openCount > 0L }
            .sortedWith(
                compareByDescending<IndexedValue<App>> { it.value.openCount }
                    .thenBy { it.index }
            )
            .map { it.value }

        val unopened = indexedApps
            .filter { it.value.openCount <= 0L }
            .map { it.value }

        val result = arrayOfNulls<App>(n)
        opened.forEachIndexed { index, app -> result[slots[index]] = app }

        // Preserve unopened apps' stable source order across the remaining row-major slots.
        val remainingSlots = result.indices.filter { result[it] == null }
        unopened.forEachIndexed { index, app -> result[remainingSlots[index]] = app }

        return ArrayList(result.filterNotNull())
    }
}
