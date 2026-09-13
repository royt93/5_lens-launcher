package com.mckimquyen.search

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.Log

/** One app-published shortcut ready to render as a chip under a search result. */
data class QuickShortcut(
    val id: String,
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

/**
 * SEARCH-003: queries each matched app's own published shortcuts (e.g. "New message" in a chat
 * app) via [LauncherApps] - the same API this launcher's own manifest shortcuts are exposed
 * through to other launchers (see AndroidManifest.xml's android.app.shortcuts meta-data).
 * Reading another app's shortcuts requires this app to hold the default-launcher role;
 * [LauncherApps.getShortcuts] throws [SecurityException] otherwise - callers always get an
 * empty list back rather than a crash, matching how a first-run/non-default-launcher install
 * should behave (no shortcuts shown, same as an app that simply publishes none).
 */
object AppShortcutsProvider {
    const val MAX_SHORTCUTS = 3

    @JvmStatic
    fun shortcutsFor(context: Context, packageName: String): List<QuickShortcut> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(packageName)
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        }
        return try {
            val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle()) ?: return emptyList()
            shortcuts
                .filter { it.isEnabled }
                .sortedBy { it.rank } // ShortcutInfo: lower rank = higher priority
                .take(MAX_SHORTCUTS)
                .map { info ->
                    QuickShortcut(
                        id = info.id,
                        packageName = info.`package`,
                        label = info.shortLabel?.toString() ?: info.longLabel?.toString().orEmpty(),
                        icon = try {
                            launcherApps.getShortcutIconDrawable(info, 0)
                        } catch (e: Exception) {
                            null
                        }
                    )
                }
        } catch (e: SecurityException) {
            // Not the default launcher (or role revoked) - not an error, just no shortcuts.
            emptyList()
        } catch (e: Exception) {
            Log.w("AppShortcutsProvider", "shortcutsFor($packageName) failed", e)
            emptyList()
        }
    }

    /** Launches [shortcut]; false if it's no longer valid (app updated/uninstalled meanwhile). */
    @JvmStatic
    fun startShortcut(context: Context, shortcut: QuickShortcut, sourceBounds: android.graphics.Rect?): Boolean {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
        return try {
            launcherApps.startShortcut(shortcut.packageName, shortcut.id, sourceBounds, null, Process.myUserHandle())
            true
        } catch (e: Exception) {
            Log.w("AppShortcutsProvider", "startShortcut(${shortcut.id}) failed", e)
            false
        }
    }
}
