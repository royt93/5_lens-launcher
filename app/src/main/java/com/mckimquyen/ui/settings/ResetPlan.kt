package com.mckimquyen.ui.settings

/**
 * UI-034: one preference a tab's reset writes. [currentValue] is what the user sees now (the effective value, e.g. an
 * inherited per-lens value or a computed default), not necessarily what is stored under [key].
 */
data class ResetEntry(
    val key: String,
    val labelRes: Int,
    val defaultValue: Any,
    val currentValue: Any,
    /** False for a companion key the reset also writes but the user never sees as its own setting. */
    val showInPreview: Boolean = true
)

/** One line of the reset preview: [labelRes] goes from [currentValue] to [defaultValue]. */
data class ResetPreviewRow(val key: String, val labelRes: Int, val currentValue: Any, val defaultValue: Any)

/** Raw preference values before a reset; a null value means the key was absent and must be removed again by undo. */
data class ResetSnapshot(val values: Map<String, Any?>)

/**
 * What a reset would do. [rows] are the entries whose visible value really changes. [snapshot] holds the raw stored
 * value of every key the reset writes (absent ones as null) so undo restores the exact previous state; it is empty
 * when nothing would change.
 */
data class ResetPlan(val rows: List<ResetPreviewRow>, val snapshot: ResetSnapshot) {
    companion object {
        /** [rawStored] is the raw preference map, used only for the snapshot. */
        fun build(rawStored: Map<String, *>, entries: List<ResetEntry>): ResetPlan {
            val rows = entries
                .filter { it.showInPreview && it.currentValue != it.defaultValue }
                .map { ResetPreviewRow(it.key, it.labelRes, it.currentValue, it.defaultValue) }
            if (rows.isEmpty()) return ResetPlan(emptyList(), ResetSnapshot(emptyMap()))
            return ResetPlan(rows, ResetSnapshot(entries.associate { it.key to rawStored[it.key] }))
        }
    }
}
