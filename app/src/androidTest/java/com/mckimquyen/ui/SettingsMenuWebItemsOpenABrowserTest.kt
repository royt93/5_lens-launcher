package com.mckimquyen.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.views.SuperWebViewActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audit R1: "Github (Original)", "Github (Forked)", "License" and "Changelog" in the Settings menu passed isExternal=true into the parameter that means
 * isUsingInternalWebView, so they went to SuperWebViewActivity, which only accepts loitp.notion.site and finished at once: a flash and no page (seen on a
 * Samsung A50s release build). Both launches are intercepted by monitors that block them, so no browser really opens.
 */
@RunWith(AndroidJUnit4::class)
class SettingsMenuWebItemsOpenABrowserTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val GITHUB = "https://github.com/ricknout/lens-launcher"
        const val LICENSE = "https://raw.githubusercontent.com/ricknout/lens-launcher/master/LICENSE.md"
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun openFromMenu(url: String, isExternal: Boolean): Pair<Int, Int> {
        val viewMonitor = instrumentation.addMonitor(IntentFilter(Intent.ACTION_VIEW).apply { addDataScheme("https") }, Instrumentation.ActivityResult(Activity.RESULT_OK, null), true)
        val webViewMonitor = instrumentation.addMonitor(SuperWebViewActivity::class.java.name, Instrumentation.ActivityResult(Activity.RESULT_OK, null), true)
        try {
            ActivityScenario.launch(ActSettings::class.java).use { scenario ->
                instrumentation.waitForIdleSync()
                scenario.onActivity { it.openWebUrl(url, com.mckimquyen.R.string.license, isExternal) }
                instrumentation.waitForIdleSync()
            }
            return viewMonitor.hits to webViewMonitor.hits
        } finally {
            instrumentation.removeMonitor(viewMonitor)
            instrumentation.removeMonitor(webViewMonitor)
        }
    }

    @Test fun anExternalMenuItemOpensTheBrowserAndNeverTheInternalWebView() {
        val (browser, internal) = openFromMenu(GITHUB, isExternal = true)

        assertEquals("the browser must be started once", 1, browser)
        assertEquals("the internal web view only accepts loitp.notion.site, so it must not be started for GitHub", 0, internal)
    }

    @Test fun theLicenseItemBehavesTheSame() {
        val (browser, internal) = openFromMenu(LICENSE, isExternal = true)

        assertEquals(1, browser)
        assertEquals(0, internal)
    }

    @Test fun thePrivacyItemStillOpensTheBrowser() {
        // Positive control: the item that always worked keeps working, so "browser once" above is not an artefact of the monitors.
        val (browser, internal) = openFromMenu("https://loitp.notion.site/loitp/Privacy-Policy-319b1cd8783942fa8923d2a3c9bce60f/", isExternal = false)

        assertEquals(1, browser)
        assertEquals(0, internal)
    }
}
