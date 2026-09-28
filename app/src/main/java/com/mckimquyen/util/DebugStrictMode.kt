package com.mckimquyen.util

import android.os.StrictMode

/**
 * Development-only diagnostics: surfaces main-thread disk/network I/O and leaked closeables in
 * logcat (tag `StrictMode`) for debug builds. Release/benchmark builds never install it.
 */
object DebugStrictMode {

    /**
     * TEST-003: records that [installIfDebug] actually ran and installed a policy at least once
     * this process - unlike the real OS-level `StrictMode` policy (which something elsewhere in a
     * long instrumented run can reset without warning, confirmed non-deterministic across otherwise
     * identical full-suite reruns), this plain flag cannot be touched by anything outside this
     * object. Lets a test distinguish "RApplication's wiring genuinely never called this" (a real
     * regression) from "it called this correctly, but the ambient OS policy was reset by something
     * unrelated afterwards" (a known, harmless, unreproducible-on-demand environmental flake).
     */
    @Volatile
    @get:androidx.annotation.VisibleForTesting
    var wasInstalledThisProcess: Boolean = false
        private set

    /** Installs log-only thread + VM policies when [isDebug]; returns whether it installed them. */
    // ponytail: penaltyLog only - startup still does some sanctioned main-thread I/O (prefs),
    // so penaltyDeath would crash debug builds; tighten once that I/O is moved off-thread.
    @JvmStatic
    fun installIfDebug(isDebug: Boolean): Boolean {
        if (!isDebug) return false
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
        wasInstalledThisProcess = true
        return true
    }
}
