package com.mckimquyen.util

import android.os.StrictMode

/**
 * Development-only diagnostics: surfaces main-thread disk/network I/O and leaked closeables in
 * logcat (tag `StrictMode`) for debug builds. Release/benchmark builds never install it.
 */
object DebugStrictMode {

    /** Installs log-only thread + VM policies when [isDebug]; returns whether it installed them. */
    // ponytail: penaltyLog only - startup still does some sanctioned main-thread I/O (prefs),
    // so penaltyDeath would crash debug builds; tighten once that I/O is moved off-thread.
    @JvmStatic
    fun installIfDebug(isDebug: Boolean): Boolean {
        if (!isDebug) return false
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        return true
    }
}
