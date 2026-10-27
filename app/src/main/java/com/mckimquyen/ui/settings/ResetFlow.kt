package com.mckimquyen.ui.settings

/**
 * UI-034: the decisions around one tab reset, kept free of Android types. [prepare] says what would change (null when
 * nothing would), [confirm] snapshots then applies the defaults, [undo] puts the snapshot back exactly once.
 */
class ResetFlow(private val store: Store) {
    interface Store {
        /** Raw stored preferences, used only for the snapshot. */
        fun raw(): Map<String, Any?>
        fun snapshot(keys: Set<String>): ResetSnapshot
        fun restore(snapshot: ResetSnapshot): Boolean
        fun apply(entries: List<ResetEntry>)
    }

    private var pending: ResetSnapshot? = null

    val canUndo: Boolean get() = pending != null

    /** The preview for [entries], or null when every visible value already equals its default. */
    fun prepare(entries: List<ResetEntry>): ResetPlan? =
        ResetPlan.build(store.raw(), entries).takeIf { it.rows.isNotEmpty() }

    /** Snapshots exactly the keys [entries] write, then applies the defaults. Replaces any earlier undo. */
    fun confirm(plan: ResetPlan, entries: List<ResetEntry>) {
        pending = store.snapshot(plan.snapshot.values.keys)
        store.apply(entries)
    }

    /** Restores the snapshot; false (and the undo stays available) if there is none or the write failed. */
    fun undo(): Boolean {
        val snapshot = pending ?: return false
        if (!store.restore(snapshot)) return false
        pending = null
        return true
    }

    fun clear() {
        pending = null
    }
}
