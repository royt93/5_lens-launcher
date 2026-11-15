package com.mckimquyen.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quick-actions row of the empty search is hidden when showing it would leave the all-apps list too short. Numbers come from the
 * Pixel 7 Pro measurements (3.5x density): phone landscape column 974 px, rows above the list 596 px with the 274 px quick-actions row
 * (322 px without it), keypad capped at 40% of the column. 56 dp (one list row) is `minListPx` = 196 px at 3.5x.
 */
class SearchQuickActionsRowPolicyTest {

    private companion object {
        const val DENSITY = 3.5f
        const val MIN_LIST_PX = 196 // 56 dp at 3.5x
        const val PHONE_LANDSCAPE_COLUMN = 974
        const val PHONE_PORTRAIT_COLUMN = 2692
        const val ROWS_WITH_QUICK_ACTIONS = 596
        const val QUICK_ACTIONS_ROW = 274
        const val KEYPAD_PERCENT = 40
    }

    private fun keypadCap(column: Int) = column * KEYPAD_PERCENT / 100

    private fun show(
        isEmptyQuery: Boolean = true,
        column: Int = PHONE_PORTRAIT_COLUMN,
        rows: Int = ROWS_WITH_QUICK_ACTIONS,
        keypad: Int = keypadCap(column),
        minList: Int = MIN_LIST_PX,
    ) = SearchQuickActionsRowPolicy.shouldShow(isEmptyQuery, column, rows, keypad, minList)

    @Test fun `the measured phone landscape case hides the row`() {
        assertFalse(show(column = PHONE_LANDSCAPE_COLUMN))
    }

    @Test fun `the measured phone portrait case shows the row`() {
        assertTrue(show(column = PHONE_PORTRAIT_COLUMN))
    }

    @Test fun `a non empty query never shows the row`() {
        assertFalse(show(isEmptyQuery = false))
    }

    @Test fun `exactly the minimum list height still shows the row`() {
        val keypad = 300
        assertTrue(show(column = ROWS_WITH_QUICK_ACTIONS + keypad + MIN_LIST_PX, keypad = keypad))
    }

    @Test fun `one pixel under the minimum list height hides the row`() {
        val keypad = 300
        assertFalse(show(column = ROWS_WITH_QUICK_ACTIONS + keypad + MIN_LIST_PX - 1, keypad = keypad))
    }

    @Test fun `without the keypad the same column keeps the row`() {
        // 10 px more than rows + minimum: enough for the list without a keypad, not once a 40% keypad takes its share.
        val column = ROWS_WITH_QUICK_ACTIONS + MIN_LIST_PX + 10
        assertTrue(show(column = column, keypad = 0))
        assertFalse(show(column = column, keypad = keypadCap(column)))
    }

    @Test fun `a zero or negative column hides the row and does not throw`() {
        assertFalse(show(column = 0, keypad = 0))
        assertFalse(show(column = -5, keypad = 0))
    }

    @Test fun `hiding the row is what makes the phone landscape list usable`() {
        val column = PHONE_LANDSCAPE_COLUMN
        val keypad = keypadCap(column)
        val listWithRow = column - ROWS_WITH_QUICK_ACTIONS - keypad
        val listWithoutRow = column - (ROWS_WITH_QUICK_ACTIONS - QUICK_ACTIONS_ROW) - keypad
        assertTrue("the list is under the minimum with the row ($listWithRow)", listWithRow < MIN_LIST_PX)
        assertTrue("and over it without the row ($listWithoutRow)", listWithoutRow >= MIN_LIST_PX)
    }

    @Test fun `a negative minimum is treated as zero, not as permission to hide a list that is just empty`() {
        // 100 px column, 100 px of rows: the list would be exactly 0. With minimum 0 that is allowed; a minimum of -50 must not
        // change the answer, and neither may a minimum so negative that a negative list would pass.
        assertTrue(show(column = 100, rows = 100, keypad = 0, minList = 0))
        assertTrue(show(column = 100, rows = 100, keypad = 0, minList = -50))
        assertFalse("a list of -1 px is never enough, however negative the minimum", show(column = 100, rows = 101, keypad = 0, minList = -50))
    }

    @Test fun `a negative keypad height is treated as no keypad and does not grow the list`() {
        // rows 90 + keypad 0 leaves 10 px; a keypad of -500 must not turn that into 510 px of list.
        assertFalse(show(column = 100, rows = 90, keypad = -500, minList = 50))
        assertTrue(show(column = 100, rows = 40, keypad = -500, minList = 50))
    }

    @Test fun `a zero column hides the row even when nothing else would take space`() {
        // With no rows, no keypad and a zero minimum the arithmetic alone (0 >= 0) would show it: only the column guard says no.
        assertFalse(show(column = 0, rows = 0, keypad = 0, minList = 0))
    }
}
