package com.mckimquyen.search

/**
 * Decides whether the empty-search quick-actions row is worth its height. [columnHeightPx] is the column's CONTENT height (its own
 * height minus the padding the activity adds for the system bar). On a phone in landscape the search column is about 974 px
 * tall, the fixed rows above the list already take 596 px (274 of them this row) and the T9 keypad up to 40% of the column, which left the
 * all-apps list 0 px high. The row is hidden when showing it would leave the list under [minListPx]. The keypad is counted at its cap,
 * not at its last measured height, so the decision does not depend on the previous decision.
 */
object SearchQuickActionsRowPolicy {

    /**
     * One list row (56 dp), expressed in dp so callers scale it with the display density. Owner's choice after the measurement that
     * hiding the row on a 974 px phone-landscape column leaves the list 263 px (75 dp, about 1.3 rows); a judgement value, not tuned on a hand.
     */
    const val MIN_LIST_DP = 56

    @JvmStatic
    fun shouldShow(
        isEmptyQuery: Boolean,
        columnHeightPx: Int,
        fixedRowsWithRowPx: Int,
        keypadPx: Int,
        minListPx: Int,
    ): Boolean {
        if (!isEmptyQuery || columnHeightPx <= 0) return false
        val listPx = columnHeightPx - fixedRowsWithRowPx - keypadPx.coerceAtLeast(0)
        return listPx >= minListPx.coerceAtLeast(0)
    }
}
