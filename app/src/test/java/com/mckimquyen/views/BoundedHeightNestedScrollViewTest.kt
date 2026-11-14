package com.mckimquyen.views

import android.content.Context
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
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

    @Test
    fun `the cap follows the parent height, so a low landscape window gets a lower cap`() {
        val low = PARENT_HEIGHT_PX / 2
        val h = measuredHeight(scrollWith(TALL_CONTENT_PX), MeasureSpec.makeMeasureSpec(low, MeasureSpec.AT_MOST))
        assertEquals(low * MAX_PERCENT / 100, h)
    }
}
