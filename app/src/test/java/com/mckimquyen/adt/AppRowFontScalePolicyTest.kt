package com.mckimquyen.adt

import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UI-031: Unit tests for app row font-scale action collapse policy.
 * At fontScale >= 1.5x, row action buttons (hide/lock) collapse into ivAppMenu to prevent text clipping.
 */
class AppRowFontScalePolicyTest {

    @Test
    fun shouldCollapseRowActions_returnsTrueFor1_5AndAbove() {
        assertFalse("1.0x (normal) should not collapse", AppRowFontScalePolicy.shouldCollapseRowActions(1.0f))
        assertFalse("1.25x should not collapse", AppRowFontScalePolicy.shouldCollapseRowActions(1.25f))
        assertFalse("1.49x should not collapse", AppRowFontScalePolicy.shouldCollapseRowActions(1.49f))

        assertTrue("1.5x should collapse", AppRowFontScalePolicy.shouldCollapseRowActions(1.5f))
        assertTrue("1.75x should collapse", AppRowFontScalePolicy.shouldCollapseRowActions(1.75f))
        assertTrue("2.0x (max accessibility) should collapse", AppRowFontScalePolicy.shouldCollapseRowActions(2.0f))
    }

    @Test
    fun rowLabelEndAnchor_selectsCorrectAnchor() {
        assertEquals(
            "When collapsed, label should anchor to ivAppMenu",
            R.id.ivAppMenu,
            AppRowFontScalePolicy.rowLabelEndAnchor(isCollapsed = true)
        )
        assertEquals(
            "When normal, label should anchor to btAppLock",
            R.id.btAppLock,
            AppRowFontScalePolicy.rowLabelEndAnchor(isCollapsed = false)
        )
    }
}
