package com.mckimquyen.views

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SEC-003: proves SuperWebViewActivity self-defends against a malicious KEY_URL
 * even if reached (e.g. same-uid launch, or a future exported regression).
 */
@RunWith(AndroidJUnit4::class)
class SuperWebViewActivityHardeningTest {

    private fun intentWithUrl(url: String): Intent {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return Intent(context, SuperWebViewActivity::class.java).apply {
            putExtra(SuperWebViewActivity.KEY_URL, url)
            putExtra(SuperWebViewActivity.KEY_TITLE, "test")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @Test
    fun rejectsJavascriptSchemeUrl() {
        ActivityScenario.launch<SuperWebViewActivity>(intentWithUrl("javascript:alert(1)")).use { scenario ->
            assertEquals("must finish on disallowed scheme", Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    fun rejectsFileSchemeUrl() {
        ActivityScenario.launch<SuperWebViewActivity>(intentWithUrl("file:///etc/passwd")).use { scenario ->
            assertEquals("must finish on disallowed scheme", Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    fun rejectsSubdomainLookalikeHost() {
        ActivityScenario.launch<SuperWebViewActivity>(
            intentWithUrl("https://evil-loitp.notion.site/")
        ).use { scenario ->
            assertEquals("must finish on non-allowlisted host", Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    fun rejectsEmbeddedCredentialsHost() {
        ActivityScenario.launch<SuperWebViewActivity>(
            intentWithUrl("https://loitp.notion.site@attacker.com/")
        ).use { scenario ->
            assertEquals("must finish when userinfo trick is used", Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    fun acceptsExactAllowlistedHost() {
        ActivityScenario.launch<SuperWebViewActivity>(
            intentWithUrl("https://loitp.notion.site/loitp/Privacy-Policy-319b1cd8783942fa8923d2a3c9bce60f/")
        ).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue("must stay open for the allowlisted host", !activity.isFinishing)
            }
        }
    }
}
