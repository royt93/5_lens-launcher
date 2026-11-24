package com.mckimquyen.util

import android.content.Intent
import android.os.Bundle

/**
 * Reads extras of an Intent that another app sent to one of this app's exported components (ActHome, BadgeCountReceiver).
 * The first read unparcels the whole extras Bundle, and a Serializable of a class this app does not have makes Android throw a RuntimeException
 * (BadParcelableException is one) from getStringExtra, which killed the process. A sender must not be able to do that, so a failed read is "no value".
 */
object SafeIntentExtras {

    @JvmStatic
    fun string(intent: Intent?, key: String): String? = try {
        intent?.getStringExtra(key)
    } catch (_: RuntimeException) {
        null
    }

    @JvmStatic
    fun bool(intent: Intent?, key: String, default: Boolean): Boolean = try {
        intent?.getBooleanExtra(key, default) ?: default
    } catch (_: RuntimeException) {
        default
    }

    /** The extras, already unparcelled: a Bundle handed out unread would throw later, at the caller's first get. */
    @JvmStatic
    fun extras(intent: Intent?): Bundle? = try {
        intent?.extras?.also { it.size() } // size() forces the unparcel inside this try
    } catch (_: RuntimeException) {
        null
    }
}
