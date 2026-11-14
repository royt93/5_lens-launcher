package com.mckimquyen.views

import android.content.Context
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SEARCH-010: the T9 keypad container never takes more than 40% of the height its parent offers. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class BoundedHeightNestedScrollViewTest {

    private companion object {
        const val WIDTH_PX = 400
        const val PARENT_HEIGHT_PX = 1_000
        const val MAX_PERCENT = 40
        const val CAP_PX = PARENT_HEIGHT_PX * MAX_PERCENT / 100
        const val SHORT_CONTENT_PX = 150
        const val TALL_CONTENT_PX = 900
        const val COLUMN_HEIGHT_PX = 1_000
        const val TALL_SIBLING_PX = 700
        const val SMALL_SIBLING_PX = 100
        const val HUGE_SIBLING_PX = 1_200
        const val GONE_SIBLING_PX = 800
        const val SIBLING_MARGIN_PX = 50
        const val MARGIN_SIBLING_PX = 600
        const val WEIGHT = 1f
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * NestedScrollView measures its child with an UNSPECIFIED height and ignores the child's LayoutParams height, so a plain
     * View would measure 0: the natural content height has to come from the child's minimum height.
     */
    private fun scrollWith(contentHeightPx: Int): BoundedHeightNestedScrollView =
        BoundedHeightNestedScrollView(context).apply {
            val content = View(context).apply { minimumHeight = contentHeightPx }
            addView(content, FrameLayout.LayoutParams(WIDTH_PX, FrameLayout.LayoutParams.WRAP_CONTENT))
        }

    private fun measuredHeight(view: BoundedHeightNestedScrollView, heightSpec: Int): Int {
        view.measure(MeasureSpec.makeMeasureSpec(WIDTH_PX, MeasureSpec.EXACTLY), heightSpec)
        return view.measuredHeight
    }

    @Test
    fun `tall content is capped at forty percent of an at-most parent`() {
        val h = measuredHeight(scrollWith(TALL_CONTENT_PX), MeasureSpec.makeMeasureSpec(PARENT_HEIGHT_PX, MeasureSpec.AT_MOST))
        assertEquals(CAP_PX, h)
    }

    @Test
    fun `tall content is capped even when the parent forces an exact height`() {
        val h = measuredHeight(scrollWith(TALL_CONTENT_PX), MeasureSpec.makeMeasureSpec(PARENT_HEIGHT_PX, MeasureSpec.EXACTLY))
        assertEquals("an EXACTLY parent must not force the keypad taller than the cap", CAP_PX, h)
    }

    @Test
    fun `short content keeps its natural height below the cap`() {
        val h = measuredHeight(scrollWith(SHORT_CONTENT_PX), MeasureSpec.makeMeasureSpec(PARENT_HEIGHT_PX, MeasureSpec.AT_MOST))
        assertEquals(SHORT_CONTENT_PX, h)
        assertTrue("fixture: natural height must be below the cap", SHORT_CONTENT_PX < CAP_PX)
    }

    @Test
    fun `an unspecified height is not bounded`() {
        val h = measuredHeight(scrollWith(TALL_CONTENT_PX), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        assertEquals("no parent limit means the natural height", TALL_CONTENT_PX, h)
    }

    // --- The real search layout: a vertical column [fixed rows..., weighted result list, keypad]. With a weighted child in the
    // column, LinearLayout offers every child the WHOLE column height, so the keypad's 40% of that must also leave room for the
    // rows above it (phone landscape on the Pixel: 596 px above + a 389 px keypad in a 974 px column overflowed by 11 px).

    private fun fixedRow(heightPx: Int, margin: Int = 0, visible: Boolean = true) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(WIDTH_PX, heightPx).apply { topMargin = margin; bottomMargin = margin }
        if (!visible) visibility = View.GONE
    }

    private fun weightedList() = View(context).apply { layoutParams = LinearLayout.LayoutParams(WIDTH_PX, 0, WEIGHT) }

    private fun keypad(): BoundedHeightNestedScrollView = scrollWith(TALL_CONTENT_PX).apply {
        layoutParams = LinearLayout.LayoutParams(WIDTH_PX, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun column(vararg children: View, orientation: Int = LinearLayout.VERTICAL): LinearLayout = LinearLayout(context).apply {
        this.orientation = orientation
        children.forEach { addView(it) }
        remeasure()
    }

    /** A second layout pass: forceLayout marks the whole subtree dirty so measure() really runs again with the stale sizes in place. */
    private fun LinearLayout.remeasure() {
        forceLayout()
        for (i in 0 until childCount) getChildAt(i).forceLayout()
        measure(MeasureSpec.makeMeasureSpec(WIDTH_PX, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(COLUMN_HEIGHT_PX, MeasureSpec.EXACTLY))
    }

    @Test
    fun `rows above a weighted list shrink the keypad so the column does not overflow`() {
        val keypad = keypad()
        val list = weightedList()
        column(fixedRow(TALL_SIBLING_PX), list, keypad)
        assertEquals("only 300 px are left under the 700 px of rows", COLUMN_HEIGHT_PX - TALL_SIBLING_PX, keypad.measuredHeight)
        assertTrue("the column must not overflow", TALL_SIBLING_PX + list.measuredHeight + keypad.measuredHeight <= COLUMN_HEIGHT_PX)
    }

    @Test
    fun `with little above the keypad still gets its full forty percent`() {
        val keypad = keypad()
        val list = weightedList()
        column(fixedRow(SMALL_SIBLING_PX), list, keypad)
        assertEquals(CAP_PX, keypad.measuredHeight)
        assertEquals("the list gets what is left", COLUMN_HEIGHT_PX - SMALL_SIBLING_PX - CAP_PX, list.measuredHeight)
    }

    @Test
    fun `a gone row above takes no space from the keypad`() {
        val keypad = keypad()
        column(fixedRow(SMALL_SIBLING_PX), fixedRow(GONE_SIBLING_PX, visible = false), weightedList(), keypad)
        assertEquals(CAP_PX, keypad.measuredHeight)
    }

    @Test
    fun `margins of the rows above count as taken space`() {
        val keypad = keypad()
        column(fixedRow(MARGIN_SIBLING_PX, margin = SIBLING_MARGIN_PX), weightedList(), keypad)
        val taken = MARGIN_SIBLING_PX + 2 * SIBLING_MARGIN_PX
        assertEquals(COLUMN_HEIGHT_PX - taken, keypad.measuredHeight)
    }

    @Test
    fun `a row that was visible and is now gone leaves neither its stale height nor its margins behind`() {
        val keypad = keypad()
        val row = fixedRow(TALL_SIBLING_PX, margin = SIBLING_MARGIN_PX)
        val column = column(row, weightedList(), keypad)
        assertTrue("fixture: while visible the row really shrinks the keypad", keypad.measuredHeight < CAP_PX)
        row.visibility = View.GONE
        column.remeasure()
        assertEquals("a GONE row keeps its old measuredHeight; it must not be counted", CAP_PX, keypad.measuredHeight)
    }

    @Test
    fun `the keypad can grow again after it was measured small, the stale list height does not ratchet it down`() {
        val keypad = keypad()
        val shortContent = (keypad.getChildAt(0)).apply { minimumHeight = SHORT_CONTENT_PX }
        val column = column(fixedRow(SMALL_SIBLING_PX), weightedList(), keypad)
        assertEquals("fixture: short content, natural height", SHORT_CONTENT_PX, keypad.measuredHeight)
        shortContent.minimumHeight = TALL_CONTENT_PX
        column.remeasure()
        assertEquals("the list was measured as the leftover of the small keypad and must not be counted as a fixed row", CAP_PX, keypad.measuredHeight)
    }

    @Test
    fun `a horizontal parent offers the height to every child so nothing is subtracted`() {
        val keypad = keypad()
        column(fixedRow(TALL_SIBLING_PX), weightedList(), keypad, orientation = LinearLayout.HORIZONTAL)
        assertEquals(CAP_PX, keypad.measuredHeight)
    }

    @Test
    fun `rows taller than the column leave the keypad no height instead of a negative one`() {
        val keypad = keypad()
        column(fixedRow(HUGE_SIBLING_PX), weightedList(), keypad)
        assertEquals(0, keypad.measuredHeight)
    }

    @Test
    fun `without any weighted child LinearLayout already subtracts the rows above, so they are not subtracted twice`() {
        val keypad = keypad()
        column(fixedRow(TALL_SIBLING_PX), keypad)
        val offered = COLUMN_HEIGHT_PX - TALL_SIBLING_PX
        assertEquals("40 percent of the 300 px the column already offered", offered * MAX_PERCENT / 100, keypad.measuredHeight)
    }

    @Test
    fun `a weighted child after the keypad does not change what the keypad is offered`() {
        val keypad = keypad()
        column(fixedRow(TALL_SIBLING_PX), keypad, weightedList())
        val offered = COLUMN_HEIGHT_PX - TALL_SIBLING_PX
        assertEquals(offered * MAX_PERCENT / 100, keypad.measuredHeight)
    }

    @Test
    fun `the cap follows the parent height, so a low landscape window gets a lower cap`() {
        val low = PARENT_HEIGHT_PX / 2
        val h = measuredHeight(scrollWith(TALL_CONTENT_PX), MeasureSpec.makeMeasureSpec(low, MeasureSpec.AT_MOST))
        assertEquals(low * MAX_PERCENT / 100, h)
    }
}
