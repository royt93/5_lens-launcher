package com.mckimquyen.util

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.mckimquyen.model.AppIdentity

/**
 * FEAT-012: the Android-facing [ProfileDirectory]. Reads every profile of the current user through
 * [LauncherApps], the only API that exposes another profile's launchable activities.
 *
 * A profile's id here is its [UserManager.getSerialNumberForUser] (public API, unlike `UserHandle.getIdentifier`).
 * Serial numbers are never reused, so a deleted and re-created work profile gets a new id and its old rows can
 * never be mistaken for the new one. The user running this app is always id 0 (see [PRIMARY_USER_ID]),
 * whatever its serial number is, so keys written before work-profile support stay valid in a secondary user too.
 */
class LauncherAppsDirectory(context: Context) : ProfileDirectory {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val myHandle = Process.myUserHandle()

    private val handles: Map<Int, UserHandle> by lazy(LazyThreadSafetyMode.NONE) {
        profileHandles().associateBy(::idOf)
    }

    /**
     * [LauncherApps.getProfiles] needs API 26; [UserManager.getUserProfiles] (API 21) returns the same set of
     * profiles of the current user, so it is used below API 26 where this app's minSdk is 25.
     */
    private fun profileHandles(): List<UserHandle> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) launcherApps?.profiles.orEmpty()
        else userManager?.userProfiles.orEmpty()

    override fun profileUserIds(): List<Int> = handles.keys.toList()

    override fun appsOf(userId: Int): List<ProfileAppEntry> {
        val handle = handles[userId] ?: return emptyList()
        val apps = launcherApps ?: return emptyList()
        return apps.getActivityList(null, handle).map { info ->
            ProfileAppEntry(
                packageName = info.componentName.packageName,
                name = info.componentName.className,
                label = info.label,
                userId = userId,
                isWorkProfile = isManagedProfile(userId)
            )
        }
    }

    /**
     * Every profile other than the one running this app is shown with the system badge. This covers work profiles
     * and any other kind of profile the system lists, since the badge itself is supplied by the system.
     */
    override fun isManagedProfile(userId: Int): Boolean = userId != PRIMARY_USER_ID

    override fun isUserUnlocked(userId: Int): Boolean {
        val handle = handles[userId] ?: return false
        return userManager?.isUserUnlocked(handle) ?: false
    }

    /** The handle for [userId], or null when that profile is gone; used to launch into the right profile. */
    fun handleOf(userId: Int): UserHandle? = handles[userId]

    /** The system-badged icon for a profile app, or null if the system cannot supply one. */
    fun badgedIcon(packageName: String, name: String, userId: Int, density: Int): android.graphics.drawable.Drawable? {
        val handle = handles[userId] ?: return null
        val apps = launcherApps ?: return null
        return try {
            apps.getActivityList(packageName, handle)
                .firstOrNull { it.componentName.className == name }
                ?.getBadgedIcon(density)
        } catch (error: SecurityException) {
            Logger.e("LauncherAppsDirectory: badged icon unavailable for $packageName", error)
            null
        }
    }

    private fun idOf(handle: UserHandle): Int =
        if (handle == myHandle) PRIMARY_USER_ID else (userManager?.getSerialNumberForUser(handle) ?: -1L).toInt()

    private companion object {
        const val PRIMARY_USER_ID = AppIdentity.PRIMARY_USER_ID
    }
}
