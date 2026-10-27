package com.mckimquyen.ui.settings

/** UI-034: one preference a tab's reset writes, with the value it is reset to. */
data class ResetEntry(val key: String, val label: String, val defaultValue: Any)

/** One line of the reset preview: [label] goes from [currentValue] to [defaultValue]. */
data class ResetPreviewRow(val key: String, val label: String, val currentValue: Any, val defaultValue: Any)

/** Raw preference values before a reset; a null value means the key was absent and must be removed again by undo. */
data class ResetSnapshot(val values: Map<String, Any?>)

/**
 * What a reset would do. [rows] are the stored values that really change. [snapshot] holds every key the reset writes
 * (absent ones as null) so undo restores the exact previous state; it is empty when nothing would change.
 */
data class ResetPlan(val rows: List<ResetPreviewRow>, val snapshot: ResetSnapshot) {
    companion object {
        fun build(current: Map<String, *>, entries: List<ResetEntry>): ResetPlan {
            val rows = entries.mapNotNull { entry ->
                val stored = current[entry.key]
                if (stored != null && stored != entry.defaultValue) {
                    ResetPreviewRow(entry.key, entry.label, stored, entry.defaultValue)
                } else {
                    null
                }
            }
            if (rows.isEmpty()) return ResetPlan(emptyList(), ResetSnapshot(emptyMap()))
            return ResetPlan(rows, ResetSnapshot(entries.associate { it.key to current[it.key] }))
        }
    }
}
