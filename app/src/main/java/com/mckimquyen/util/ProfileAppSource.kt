package com.mckimquyen.util

import android.graphics.Bitmap
import com.mckimquyen.model.App
import com.mckimquyen.model.AppIdentity

/** One launchable activity of one profile, before it becomes an [com.mckimquyen.model.App]. */
data class ProfileAppEntry(
    val packageName: String,
    val name: String,
    val label: CharSequence,
    val userId: Int,
    val isWorkProfile: Boolean,
    /** First-install time in ms, or 0 when unknown. Filled by [LauncherAppsDirectory]. */
    val installDate: Long = 0L
) {
    val identifier: String get() = AppIdentity.identifier(packageName, name, userId)
}

/** What [ProfileAppSource] needs from the system, so the merge rules can be tested without LauncherApps. */
interface ProfileDirectory {
    fun profileUserIds(): List<Int>
    fun appsOf(userId: Int): List<ProfileAppEntry>
    fun isManagedProfile(userId: Int): Boolean
    fun isUserUnlocked(userId: Int): Boolean
}

/**
 * FEAT-012: pure rules for listing apps across profiles. The Android-facing half is [LauncherAppsDirectory].
 */
object ProfileAppSource {
    /** Marks a launch request whose profile no longer exists. */
    const val NO_USER = -1

    /**
     * Every launchable app of every usable profile, primary user first. A locked, stopped or failing profile is
     * skipped on its own, never taking the primary user's apps down with it.
     */
    @JvmStatic
    fun collect(directory: ProfileDirectory): List<ProfileAppEntry> {
        val users = directory.profileUserIds().sortedBy { if (it == AppIdentity.PRIMARY_USER_ID) -1 else it }
        return users.flatMap { userId ->
            if (userId != AppIdentity.PRIMARY_USER_ID && !directory.isUserUnlocked(userId)) return@flatMap emptyList()
            try {
                directory.appsOf(userId)
                    .filter { it.name.isNotBlank() && it.packageName.isNotBlank() }
                    .map { it.copy(userId = userId, isWorkProfile = directory.isManagedProfile(userId)) }
            } catch (error: SecurityException) {
                Logger.e("ProfileAppSource: profile $userId unavailable", error)
                emptyList()
            } catch (error: RuntimeException) {
                Logger.e("ProfileAppSource: profile $userId failed", error)
                emptyList()
            }
        }
    }

    /**
     * Builds [App] shells for profile entries. [iconFor] / [cacheKeyFor] / [paletteFor] are supplied by the caller
     * so this stays free of PackageManager and Room. The cache key is made user specific so a work copy can never
     * read or overwrite the personal copy's bitmap.
     */
    @JvmStatic
    fun toShells(
        entries: List<ProfileAppEntry>,
        firstId: Int,
        iconFor: (ProfileAppEntry) -> Bitmap?,
        cacheKeyFor: (ProfileAppEntry) -> String,
        paletteFor: (ProfileAppEntry, Bitmap?) -> Int
    ): List<App> = entries.mapIndexed { index, entry ->
        val icon = iconFor(entry)
        App(
            id = firstId + index,
            label = entry.label,
            packageName = entry.packageName,
            name = entry.name,
            icon = icon,
            paletteColor = paletteFor(entry, icon),
            iconCacheKey = AppIdentity.cacheKeySegment(cacheKeyFor(entry), entry.userId),
            userId = entry.userId,
            isWorkProfile = entry.isWorkProfile,
            installDate = entry.installDate
        )
    }

    /** How an app must be started. */
    enum class LaunchRoute {
        /** The primary user's app: the ordinary explicit intent, exactly as before work-profile support. */
        PLAIN_INTENT,

        /** Another profile's app: only [android.content.pm.LauncherApps.startMainActivity] can reach it. */
        LAUNCHER_APPS,

        /** The profile no longer exists; nothing may be launched (never fall back to another user's copy). */
        PROFILE_GONE
    }

    @JvmStatic
    fun routeFor(appUserId: Int, liveUserIds: List<Int>): LaunchRoute = when {
        appUserId == AppIdentity.PRIMARY_USER_ID -> LaunchRoute.PLAIN_INTENT
        appUserId in liveUserIds -> LaunchRoute.LAUNCHER_APPS
        else -> LaunchRoute.PROFILE_GONE
    }

    /** The user to launch into: the app's own profile if it still exists, otherwise [NO_USER] (never another user). */
    @JvmStatic
    fun launchUserId(appUserId: Int, liveUserIds: List<Int>): Int =
        if (appUserId in liveUserIds) appUserId else NO_USER

    @JvmStatic
    fun profilesChanged(before: List<Int>, after: List<Int>): Boolean = before.toSet() != after.toSet()

    /** Stored identifiers that belong to a profile which no longer exists. */
    @JvmStatic
    fun orphanedIdentifiers(storedIdentifiers: List<String>, liveUserIds: List<Int>): List<String> =
        storedIdentifiers.filter { AppIdentity.userIdOf(it) !in liveUserIds }
}
