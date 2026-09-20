package com.mckimquyen.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMenuDispatcherTest {

    private class FakeSettingsMenuHost : SettingsMenuHost {
        var launchAppsCalled = false
        var openAboutCalled = false
        var resetTabDefaultsTab: Int? = null
        var rateAppCalled = false
        var openMoreAppsCalled = false
        var shareAppCalled = false
        var openFacebookFanPageCalled = false
        var openedWebUrl: String? = null
        var openedWebTitleResId: Int? = null
        var openedWebIsExternal: Boolean? = null
        var sendFeedbackCalled = false

        override fun launchApps() { launchAppsCalled = true }
        override fun openAbout() { openAboutCalled = true }
        override fun resetTabDefaults(currentTab: Int) { resetTabDefaultsTab = currentTab }
        override fun rateApp() { rateAppCalled = true }
        override fun openMoreApps() { openMoreAppsCalled = true }
        override fun shareApp() { shareAppCalled = true }
        override fun openFacebookFanPage() { openFacebookFanPageCalled = true }
        override fun openWebUrl(url: String, titleResId: Int, isExternal: Boolean) {
            openedWebUrl = url
            openedWebTitleResId = titleResId
            openedWebIsExternal = isExternal
        }
        override fun sendFeedback() { sendFeedbackCalled = true }
    }

    @Test
    fun `dispatch ShowApps invokes host launchApps`() {
        val host = FakeSettingsMenuHost()
        val dispatcher = SettingsMenuDispatcher(host)
        val handled = dispatcher.dispatch(SettingsMenuAction.ShowApps)
        assertTrue(handled)
        assertTrue(host.launchAppsCalled)
    }

    @Test
    fun `dispatch About invokes host openAbout`() {
        val host = FakeSettingsMenuHost()
        val dispatcher = SettingsMenuDispatcher(host)
        val handled = dispatcher.dispatch(SettingsMenuAction.About)
        assertTrue(handled)
        assertTrue(host.openAboutCalled)
    }

    @Test
    fun `dispatch ResetDefaults invokes host resetTabDefaults with tab index`() {
        val host = FakeSettingsMenuHost()
        val dispatcher = SettingsMenuDispatcher(host)
        val handled = dispatcher.dispatch(SettingsMenuAction.ResetDefaults(2))
        assertTrue(handled)
        assertEquals(2, host.resetTabDefaultsTab)
    }

    @Test
    fun `dispatch RateApp invokes host rateApp`() {
        val host = FakeSettingsMenuHost()
        val dispatcher = SettingsMenuDispatcher(host)
        val handled = dispatcher.dispatch(SettingsMenuAction.RateApp)
        assertTrue(handled)
        assertTrue(host.rateAppCalled)
    }

    @Test
    fun `dispatch MoreApps invokes host openMoreApps`() {
        val host = FakeSettingsMenuHost()
        val dispatcher = SettingsMenuDispatcher(host)
        val handled = dispatcher.dispatch(SettingsMenuAction.MoreApps)
        assertTrue(handled)
        assertTrue(host.openMoreAppsCalled)
    }

    @Test
    fun `dispatch ShareApp invokes host shareApp`() {
        val host = FakeSettingsMenuHost()
        val dispatcher = SettingsMenuDispatcher(host)
        val handled = dispatcher.dispatch(SettingsMenuAction.ShareApp)
        assertTrue(handled)
        assertTrue(host.shareAppCalled)
    }

    @Test
    fun `dispatch FacebookFanPage invokes host openFacebookFanPage`() {
        val host = FakeSettingsMenuHost()
        val dispatcher = SettingsMenuDispatcher(host)
        val handled = dispatcher.dispatch(SettingsMenuAction.FacebookFanPage)
        assertTrue(handled)
        assertTrue(host.openFacebookFanPageCalled)
    }

    @Test
    fun `dispatch OpenWeb invokes host openWebUrl`() {
        val host = FakeSettingsMenuHost()
        val dispatcher = SettingsMenuDispatcher(host)
        val handled = dispatcher.dispatch(SettingsMenuAction.OpenWeb("https://example.com", 1234, true))
        assertTrue(handled)
        assertEquals("https://example.com", host.openedWebUrl)
        assertEquals(1234, host.openedWebTitleResId)
        assertEquals(true, host.openedWebIsExternal)
    }

    @Test
    fun `dispatch Feedback invokes host sendFeedback`() {
        val host = FakeSettingsMenuHost()
        val dispatcher = SettingsMenuDispatcher(host)
        val handled = dispatcher.dispatch(SettingsMenuAction.Feedback)
        assertTrue(handled)
        assertTrue(host.sendFeedbackCalled)
    }

    @Test
    fun `dispatch Unhandled returns false without invoking host`() {
        val host = FakeSettingsMenuHost()
        val dispatcher = SettingsMenuDispatcher(host)
        val handled = dispatcher.dispatch(SettingsMenuAction.Unhandled)
        assertFalse(handled)
        assertFalse(host.launchAppsCalled)
        assertFalse(host.openAboutCalled)
    }
}
