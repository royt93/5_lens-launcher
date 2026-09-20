package com.mckimquyen.ui.settings

import com.mckimquyen.R
import com.mckimquyen.util.URL_POLICY_NOTION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMenuResolverTest {

    @Test
    fun `resolve returns ShowApps for menuItemShowApps`() {
        val action = SettingsMenuResolver.resolve(R.id.menuItemShowApps, 0)
        assertEquals(SettingsMenuAction.ShowApps, action)
    }

    @Test
    fun `resolve returns About for menuItemAbout`() {
        val action = SettingsMenuResolver.resolve(R.id.menuItemAbout, 0)
        assertEquals(SettingsMenuAction.About, action)
    }

    @Test
    fun `resolve returns ResetDefaults with correct currentTab`() {
        for (tab in 0..2) {
            val action = SettingsMenuResolver.resolve(R.id.menuItemResetDefaultSettings, tab)
            assertTrue(action is SettingsMenuAction.ResetDefaults)
            assertEquals(tab, (action as SettingsMenuAction.ResetDefaults).currentTab)
        }
    }

    @Test
    fun `resolve returns RateApp for menuRateApp`() {
        val action = SettingsMenuResolver.resolve(R.id.menuRateApp, 0)
        assertEquals(SettingsMenuAction.RateApp, action)
    }

    @Test
    fun `resolve returns MoreApps for menuMoreApp`() {
        val action = SettingsMenuResolver.resolve(R.id.menuMoreApp, 0)
        assertEquals(SettingsMenuAction.MoreApps, action)
    }

    @Test
    fun `resolve returns ShareApp for menuShareApp`() {
        val action = SettingsMenuResolver.resolve(R.id.menuShareApp, 0)
        assertEquals(SettingsMenuAction.ShareApp, action)
    }

    @Test
    fun `resolve returns FacebookFanPage for menuFacebookFanPage`() {
        val action = SettingsMenuResolver.resolve(R.id.menuFacebookFanPage, 0)
        assertEquals(SettingsMenuAction.FacebookFanPage, action)
    }

    @Test
    fun `resolve returns OpenWeb with Notion URL for menuPolicy`() {
        val action = SettingsMenuResolver.resolve(R.id.menuPolicy, 0)
        assertTrue(action is SettingsMenuAction.OpenWeb)
        val webAction = action as SettingsMenuAction.OpenWeb
        assertEquals(URL_POLICY_NOTION, webAction.url)
        assertEquals(R.string.terms_and_privacy_policy, webAction.titleResId)
        assertEquals(false, webAction.isExternal)
    }

    @Test
    fun `resolve returns OpenWeb for GitHub original repository`() {
        val action = SettingsMenuResolver.resolve(R.id.menuGithubOriginal, 0)
        assertTrue(action is SettingsMenuAction.OpenWeb)
        val webAction = action as SettingsMenuAction.OpenWeb
        assertEquals("https://github.com/ricknout/lens-launcher", webAction.url)
        assertEquals(R.string.github_original, webAction.titleResId)
        assertEquals(true, webAction.isExternal)
    }

    @Test
    fun `resolve returns OpenWeb for GitHub fork repository`() {
        val action = SettingsMenuResolver.resolve(R.id.menuGithubFork, 0)
        assertTrue(action is SettingsMenuAction.OpenWeb)
        val webAction = action as SettingsMenuAction.OpenWeb
        assertEquals("https://github.com/gj-loitp/lens-launcher", webAction.url)
        assertEquals(R.string.github_fork, webAction.titleResId)
        assertEquals(true, webAction.isExternal)
    }

    @Test
    fun `resolve returns OpenWeb for License URL`() {
        val action = SettingsMenuResolver.resolve(R.id.menuLicense, 0)
        assertTrue(action is SettingsMenuAction.OpenWeb)
        val webAction = action as SettingsMenuAction.OpenWeb
        assertEquals("https://raw.githubusercontent.com/ricknout/lens-launcher/master/LICENSE.md", webAction.url)
        assertEquals(R.string.license, webAction.titleResId)
        assertEquals(true, webAction.isExternal)
    }

    @Test
    fun `resolve returns OpenWeb for Changelog URL`() {
        val action = SettingsMenuResolver.resolve(R.id.menuChangelog, 0)
        assertTrue(action is SettingsMenuAction.OpenWeb)
        val webAction = action as SettingsMenuAction.OpenWeb
        assertEquals("https://raw.githubusercontent.com/gj-loitp/lens-launcher/dev/CHANGE_LOG.md", webAction.url)
        assertEquals(R.string.changelog, webAction.titleResId)
        assertEquals(true, webAction.isExternal)
    }

    @Test
    fun `resolve returns Feedback for menuFeedback`() {
        val action = SettingsMenuResolver.resolve(R.id.menuFeedback, 0)
        assertEquals(SettingsMenuAction.Feedback, action)
    }

    @Test
    fun `resolve returns Unhandled for unknown menu ID`() {
        val action = SettingsMenuResolver.resolve(-9999, 0)
        assertEquals(SettingsMenuAction.Unhandled, action)
    }
}
