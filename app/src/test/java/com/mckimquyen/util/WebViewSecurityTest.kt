package com.mckimquyen.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SEC-003: proves isAllowedWebViewUrl rejects every phishing/trick shape,
 * and accepts only the exact allowlisted host over HTTPS.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class WebViewSecurityTest {

    @Test
    fun `allows the exact allowlisted https host`() {
        assertTrue(isAllowedWebViewUrl("https://loitp.notion.site/loitp/Privacy-Policy-319b1cd8783942fa8923d2a3c9bce60f/"))
    }

    @Test
    fun `rejects null or blank url`() {
        assertFalse(isAllowedWebViewUrl(null))
        assertFalse(isAllowedWebViewUrl(""))
        assertFalse(isAllowedWebViewUrl("   "))
    }

    @Test
    fun `rejects http scheme`() {
        assertFalse(isAllowedWebViewUrl("http://loitp.notion.site/"))
    }

    @Test
    fun `rejects dangerous schemes`() {
        assertFalse(isAllowedWebViewUrl("javascript:alert(1)"))
        assertFalse(isAllowedWebViewUrl("file:///etc/passwd"))
        assertFalse(isAllowedWebViewUrl("content://com.android.contacts/contacts"))
        assertFalse(isAllowedWebViewUrl("data:text/html,<script>alert(1)</script>"))
        assertFalse(isAllowedWebViewUrl("intent://evil#Intent;scheme=https;end"))
    }

    @Test
    fun `rejects subdomain and lookalike host tricks`() {
        assertFalse(isAllowedWebViewUrl("https://loitp.notion.site.attacker.com/"))
        assertFalse(isAllowedWebViewUrl("https://evil-loitp.notion.site/"))
        assertFalse(isAllowedWebViewUrl("https://notion.site/"))
        assertFalse(isAllowedWebViewUrl("https://attacker.com/?u=loitp.notion.site"))
    }

    @Test
    fun `rejects embedded credentials userinfo trick`() {
        assertFalse(isAllowedWebViewUrl("https://loitp.notion.site@attacker.com/"))
    }

    @Test
    fun `rejects non-standard port`() {
        assertFalse(isAllowedWebViewUrl("https://loitp.notion.site:8443/"))
    }

    @Test
    fun `rejects malformed url`() {
        assertFalse(isAllowedWebViewUrl("ht!tp://[::not-a-url"))
    }

    @Test
    fun `host match is case-insensitive`() {
        assertTrue(isAllowedWebViewUrl("https://LOITP.NOTION.SITE/loitp/"))
    }

    @Test
    fun `scheme match is case-insensitive`() {
        assertTrue(isAllowedWebViewUrl("HTTPS://loitp.notion.site/loitp/"))
    }

    @Test
    fun `explicit default port 443 is allowed`() {
        assertTrue(isAllowedWebViewUrl("https://loitp.notion.site:443/loitp/"))
    }

    @Test
    fun `rejects trailing-dot FQDN host trick`() {
        assertFalse(isAllowedWebViewUrl("https://loitp.notion.site./"))
    }

    @Test
    fun `percent-encoded dot in host decodes to the same trusted host, not a bypass`() {
        // Uri#getHost() returns the decoded host, so "%2e" resolves to "." - this still names
        // the exact allowlisted domain, it does not smuggle a different one.
        assertTrue(isAllowedWebViewUrl("https://loitp%2enotion.site/"))
    }

    @Test
    fun `rejects IPv6 literal host`() {
        assertFalse(isAllowedWebViewUrl("https://[::1]/"))
    }

    @Test
    fun `rejects backslash-authority trick`() {
        assertFalse(isAllowedWebViewUrl("https:\\\\loitp.notion.site\\"))
    }

    @Test
    fun `rejects triple-slash authority trick`() {
        assertFalse(isAllowedWebViewUrl("https:///loitp.notion.site/"))
    }

    @Test
    fun `rejects whitespace and control-character prefixed scheme trick`() {
        assertFalse(isAllowedWebViewUrl(" https://loitp.notion.site/"))
        assertFalse(isAllowedWebViewUrl("\njavascript:alert(1)"))
    }

    @Test
    fun `rejects host that only starts with the allowlisted host`() {
        assertFalse(isAllowedWebViewUrl("https://loitp.notion.siteX/"))
    }
}
