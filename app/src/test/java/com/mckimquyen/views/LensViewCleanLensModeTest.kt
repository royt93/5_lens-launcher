package com.mckimquyen.views

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Clean the Lens: pure gating decisions for the hover label and the NEW tag. Same convention as
 * `shouldDrawNotificationBadge` - one function shared by the draw path so the rule can't drift.
 */
class LensViewCleanLensModeTest {

    // ============================================================ shouldDrawAppNameLabel

    @Test
    fun `hover label draws when setting on, finger moving, clean mode off`() {
        assertTrue(LensView.shouldDrawAppNameLabel(showNameSetting = true, cleanMode = false, moving = true))
    }

    @Test
    fun `hover label never draws in clean mode even with setting on and finger moving`() {
        assertFalse(LensView.shouldDrawAppNameLabel(showNameSetting = true, cleanMode = true, moving = true))
    }

    @Test
    fun `hover label does not draw when setting off`() {
        assertFalse(LensView.shouldDrawAppNameLabel(showNameSetting = false, cleanMode = false, moving = true))
    }

    @Test
    fun `hover label does not draw when finger is not moving`() {
        assertFalse(LensView.shouldDrawAppNameLabel(showNameSetting = true, cleanMode = false, moving = false))
    }

    // ============================================================ shouldShowNotificationBadges

    @Test
    fun `badges shown when setting on and clean mode off`() {
        assertTrue(LensView.shouldShowNotificationBadges(badgesSetting = true, cleanMode = false))
    }

    @Test
    fun `badges hidden in clean mode even with setting on`() {
        assertFalse(LensView.shouldShowNotificationBadges(badgesSetting = true, cleanMode = true))
    }

    @Test
    fun `badges hidden when setting off`() {
        assertFalse(LensView.shouldShowNotificationBadges(badgesSetting = false, cleanMode = false))
    }

    @Test
    fun `clean mode suppresses the badge draw decision end to end`() {
        val show = LensView.shouldShowNotificationBadges(badgesSetting = true, cleanMode = true)
        assertFalse(LensView.shouldDrawNotificationBadge(show, notificationCount = 5))
    }

    // ============================================================ shouldDrawNewAppTag

    @Test
    fun `new tag draws when setting on and clean mode off`() {
        assertTrue(LensView.shouldDrawNewAppTag(showTagSetting = true, cleanMode = false))
    }

    @Test
    fun `new tag never draws in clean mode even with setting on`() {
        assertFalse(LensView.shouldDrawNewAppTag(showTagSetting = true, cleanMode = true))
    }

    @Test
    fun `new tag does not draw when setting off`() {
        assertFalse(LensView.shouldDrawNewAppTag(showTagSetting = false, cleanMode = false))
    }
}
