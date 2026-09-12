package com.mckimquyen.util

import androidx.core.net.toUri

/**
 * Exact-match host allowlist for content loaded inside SuperWebViewActivity.
 * Only https, no port other than 443, no embedded credentials.
 */
val ALLOWED_WEBVIEW_HOSTS = setOf(
    "loitp.notion.site",
)

fun isAllowedWebViewUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val uri = try {
        url.toUri()
    } catch (_: Exception) {
        return false
    }
    if (uri.scheme?.lowercase() != "https") return false
    if (!uri.userInfo.isNullOrEmpty()) return false
    if (uri.port != -1 && uri.port != 443) return false
    val host = uri.host?.lowercase() ?: return false
    return ALLOWED_WEBVIEW_HOSTS.contains(host)
}
