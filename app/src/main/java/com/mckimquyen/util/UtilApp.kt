package com.mckimquyen.util

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.enums.SortType
import com.mckimquyen.ext.Biometric
import com.mckimquyen.model.App
import com.mckimquyen.model.AppIdentity
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.services.BroadcastReceivers.AppsEditedReceiver
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UtilApp {
    /** Density passed to LauncherActivityInfo.getBadgedIcon; 0 means the device default. */
    private const val ICON_DENSITY_DPI = 0


    /**
     * Get all available apps for launcher, merged with one lens's layout state.
     * FISH-008 Phase 2: thin back-compat facade over [getAppShells] + [mergeLensPersistence] -
     * every existing caller/test keeps compiling unchanged (lensId defaults to the pre-Phase-2
     * behavior). Prefer calling the two split functions directly when switching lenses, so the
     * expensive PackageManager scan in [getAppShells] isn't repeated on every switch.
     */
    @JvmOverloads
    @JvmStatic
    suspend fun getApps(
        packageManager: PackageManager,
        context: Context?,
        application: Application?,
        iconPackLabelName: String,
        sortType: SortType?,
        lensId: String = LensWorkspace.DEFAULT_LENS_ID
    ): ArrayList<App> = mergeLensPersistence(
        getAppShells(packageManager, context, application, iconPackLabelName),
        lensId,
        sortType
    )

    @JvmStatic
    internal fun findIconPackByPackage(
        iconPacks: List<UtilIconPackManager.IconPack>,
        packageName: String
    ): UtilIconPackManager.IconPack? = iconPacks.find { it.mPackageName == packageName }

    @JvmStatic
    internal fun iconPackTokenFor(packageName: String, versionToken: String): String =
        if (packageName.isEmpty()) "system" else "$packageName@$versionToken"

    /**
     * The expensive half of app loading: PackageManager scan, icon-pack resolution, icon
     * decode, and the (lens-independent) palette-color lookup/seed. Callers switching between
     * lenses should call this once and re-run [mergeLensPersistence] per lens instead of
     * re-scanning PackageManager on every switch.
     */
    @JvmStatic
    suspend fun getAppShells(
        packageManager: PackageManager,
        context: Context?,
        application: Application?,
        iconPackLabelName: String
    ): ArrayList<App> {
        val shells = ArrayList<App>()
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val availableActivities = try {
            packageManager.queryIntentActivities(intent, 0)
        } catch (e: RuntimeException) {
            withContext(Dispatchers.Main.immediate) {
                context?.let { Toast.makeText(it, R.string.error_too_many_apps, Toast.LENGTH_SHORT).show() }
            }
            throw e
        }

        // Find selected icon pack
        val iconPacks = UtilIconPackManager().getAvailableIconPacksWithIcons(true, application)
        val selectedPackageName = UtilSettings(application ?: context ?: throw IllegalArgumentException("Application context required"))
            .iconPackPackageName(iconPacks)
        val selectedIconPack = findIconPackByPackage(iconPacks, selectedPackageName)
        // CORE-002: stable package identity plus package version keeps locale changes out of the key.
        val iconPackToken = selectedIconPack?.let { pack ->
            iconPackTokenFor(pack.mPackageName, versionTokenOf(packageManager, pack.mPackageName))
        } ?: iconPackTokenFor(UtilSettings.DEFAULT_ICON_PACK_PACKAGE_NAME, "")

        // PERF-007: one palette read for the whole scan. First row per identifier wins, matching
        // the old findAnyByIdentifier LIMIT 1 contract (which has no ORDER BY).
        val persistentDao = AppDatabase.getInstance().appPersistentDao()
        val paletteColors = HashMap<String, Int>()
        persistentDao.getAllRows().forEach { row ->
            if (!paletteColors.containsKey(row.identifier)) paletteColors[row.identifier] = row.paletteColor
        }
        val pendingPalettes = ArrayList<AppPersistent>()

        for ((index, resolveInfo) in availableActivities.withIndex()) {
            // Extract install date and package version/update token
            val packageInfo = try {
                packageManager.getPackageInfo(resolveInfo.activityInfo.packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
            val installDate = packageInfo?.firstInstallTime ?: 0L

            // Extract app info
            val label = resolveInfo.loadLabel(packageManager)
            val packageName = resolveInfo.activityInfo.packageName
            val name = resolveInfo.activityInfo.name
            val iconResId = resolveInfo.activityInfo.iconResource

            // Get icon bitmap (use icon pack if available)
            val defaultBitmap = UtilBitmap.packageNameToBitmap(packageManager, packageName, iconResId)
            val icon = selectedIconPack?.getIconForPackage(packageName, defaultBitmap) ?: defaultBitmap

            val iconCacheKey = BitmapCache.buildKey(
                packageName = packageName,
                componentName = name,
                versionToken = packageInfo?.let { versionTokenOf(it) } ?: "0",
                iconPackToken = iconPackToken
            )

            // Palette color is global by design (not lens-scoped, see AppPersistentDao's
            // updatePaletteColor) - look it up regardless of which lens's row holds it.
            val identifier = AppPersistent.generateIdentifier(packageName, name)
            var paletteColor = paletteColors[identifier] ?: 0
            if (paletteColor == 0 && icon != null) {
                paletteColor = UtilColor.getPaletteColorFromBitmap(icon)
                if (paletteColor != 0) {
                    paletteColors[identifier] = paletteColor
                    pendingPalettes.add(AppPersistent.defaults(packageName, name).copy(paletteColor = paletteColor))
                }
            }

            shells.add(
                App(
                    id = index,
                    label = label,
                    packageName = packageName,
                    name = name,
                    iconResId = iconResId,
                    icon = icon,
                    installDate = installDate,
                    paletteColor = paletteColor,
                    iconCacheKey = iconCacheKey
                )
            )
        }
        shells += loadProfileShells(
            packageManager, context ?: application, firstId = shells.size,
            iconPack = selectedIconPack, iconPackToken = iconPackToken,
            paletteColors = paletteColors, pendingPalettes = pendingPalettes
        )
        if (pendingPalettes.isNotEmpty()) persistentDao.setPaletteColors(pendingPalettes)
        return shells
    }

    /**
     * FEAT-012: apps of every profile other than the user running this app, listed through [LauncherApps] and
     * shown with the system's work badge. Failure here must never cost the user their own apps, so any problem
     * yields an empty list.
     */
    private suspend fun loadProfileShells(
        packageManager: PackageManager,
        context: Context?,
        firstId: Int,
        iconPack: UtilIconPackManager.IconPack?,
        iconPackToken: String,
        paletteColors: MutableMap<String, Int>,
        pendingPalettes: MutableList<AppPersistent>
    ): List<App> {
        if (context == null) return emptyList()
        return try {
            val directory = LauncherAppsDirectory(context)
            // A profile that was removed leaves rows nothing can ever show again; drop them. The primary user's own
            // scan already read every row once, so reuse that read instead of querying again: only rows whose
            // identifier carries a profile suffix can be orphans.
            val orphans = ProfileAppSource.orphanedIdentifiers(
                paletteColors.keys.toList(), directory.profileUserIds()
            ).filter { AppIdentity.userIdOf(it) != AppIdentity.PRIMARY_USER_ID }
            if (directory.profileUserIds().isNotEmpty() && orphans.isNotEmpty()) {
                AppDatabase.getInstance().appPersistentDao().deleteByIdentifiers(orphans)
            }
            val entries = ProfileAppSource.collect(directory)
                .filter { it.userId != AppIdentity.PRIMARY_USER_ID }
            ProfileAppSource.toShells(
                entries = entries,
                firstId = firstId,
                iconFor = { entry ->
                    val badged = directory.badgedIcon(entry.packageName, entry.name, entry.userId, ICON_DENSITY_DPI)
                    badged?.let { UtilBitmap.drawableToBitmap(it) }
                        ?: UtilBitmap.packageNameToBitmap(packageManager, entry.packageName)
                },
                cacheKeyFor = { entry ->
                    BitmapCache.buildKey(
                        packageName = entry.packageName,
                        componentName = entry.name,
                        versionToken = versionTokenOf(packageManager, entry.packageName),
                        iconPackToken = iconPackToken
                    )
                },
                paletteFor = { entry, icon ->
                    val identifier = entry.identifier
                    var color = paletteColors[identifier] ?: 0
                    if (color == 0 && icon != null) {
                        color = UtilColor.getPaletteColorFromBitmap(icon)
                        if (color != 0) {
                            paletteColors[identifier] = color
                            pendingPalettes.add(
                                AppPersistent.defaults(entry.packageName, entry.name, userId = entry.userId)
                                    .copy(paletteColor = color)
                            )
                        }
                    }
                    color
                }
            )
        } catch (error: Exception) {
            Logger.e("UtilApp: work profile apps unavailable, showing the primary user's apps only", error)
            emptyList()
        }
    }

    /**
     * The cheap half of app loading: overlays one lens's [AppPersistent] rows onto
     * already-resolved [shells] and sorts. Safe to call repeatedly (e.g. once per lens switch)
     * without re-scanning PackageManager.
     */
    @JvmStatic
    suspend fun mergeLensPersistence(
        shells: List<App>,
        lensId: String,
        sortType: SortType?
    ): ArrayList<App> {
        val dao = AppDatabase.getInstance().appPersistentDao()
        val persistentMap = dao.getAllForLens(lensId).associateBy { it.identifier }
        // DB-005: open count is global across lenses; a lens that has no row of its own for an app,
        // or an older row that lags behind, must still show the same count as every other lens.
        val globalOpenCounts = dao.getGlobalOpenCounts().associate { it.identifier to it.openCount }

        val apps = ArrayList<App>(shells.size)
        shells.forEach { shell ->
            val identifier = shell.identifier
            val persistent = persistentMap[identifier]
            apps.add(
                shell.copy(
                    isOpened = persistent?.appOpened ?: true,
                    isVisible = persistent?.appVisible ?: true,
                    openCount = globalOpenCounts[identifier] ?: persistent?.openCount ?: 0L,
                    orderNumber = persistent?.orderNumber ?: -1,
                    isFavorite = persistent?.isFavorite ?: false,
                    folderName = persistent?.folderName,
                    pinnedZone = PinnedZone.fromStored(persistent?.pinnedZone),
                    lensId = lensId,
                    notificationCount = persistent?.notificationCount ?: 0
                )
            )
        }
        UtilAppSorter.sort(apps, sortType)
        return apps
    }

    /** Package version code plus last-update time, so a reinstall/downgrade with an unchanged
     *  version code still yields a fresh token. */
    private fun versionTokenOf(packageInfo: PackageInfo): String {
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return "$versionCode:${packageInfo.lastUpdateTime}"
    }

    private fun versionTokenOf(packageManager: PackageManager, packageName: String): String {
        return try {
            versionTokenOf(packageManager.getPackageInfo(packageName, 0))
        } catch (_: PackageManager.NameNotFoundException) {
            "unknown"
        }
    }

    /**
     * PERF-002: reloads exactly one app's icon (respecting the active icon pack), so a
     * bitmap evicted from [BitmapCache] under memory pressure can be restored on demand
     * instead of staying blank until the next full app-list refresh.
     */
    @JvmStatic
    suspend fun loadSingleAppIcon(
        application: Application,
        packageName: String,
        iconResId: Int,
    ): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        val packageManager = application.packageManager
        val defaultBitmap = UtilBitmap.packageNameToBitmap(packageManager, packageName, iconResId)
            ?: return@withContext null
        val settings = UtilSettings(application)
        val manager = (application as? com.mckimquyen.app.RApplication)?.iconPackManager
            ?: UtilIconPackManager()
        val iconPacks = manager.getAvailableIconPacksWithIcons(false, application)
        val selectedPackageName = settings.iconPackPackageName(iconPacks)
        val selectedIconPack = findIconPackByPackage(iconPacks, selectedPackageName)
        selectedIconPack?.getIconForPackage(packageName, defaultBitmap) ?: defaultBitmap
    }

    /**
     * Launch app component with optional biometric authentication
     */
    @JvmOverloads
    @JvmStatic
    fun launchComponent(
        context: Context,
        packageName: String?,
        label: String?,
        name: String?,
        view: View?,
        bounds: Rect?,
        userId: Int = AppIdentity.PRIMARY_USER_ID
    ) {
        fun launch() {
            if (packageName == null || name == null) {
                Toast.makeText(context, R.string.error_app_not_found, Toast.LENGTH_SHORT).show()
                return
            }
            if (userId != AppIdentity.PRIMARY_USER_ID) {
                launchInProfile(context, packageName, name, userId, bounds)
                return
            }

            val componentIntent = Intent().apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                component = ComponentName(packageName, name)
                if (packageName != PKG_NAME) {
                    action = Intent.ACTION_MAIN
                }
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            try {
                ContextCompat.startActivity(context, componentIntent, getLauncherOptionsBundle(context, view, bounds))

                // Increment app open count
                AppPersistent.incrementAppCount(packageName, name, userId)
                // UI-024: opening an app clears its notification badge immediately (optimistic),
                // regardless of whether the notifying app ever sends its own cleared broadcast.
                // Clears every App entry sharing this packageName, not just the launched
                // component - BadgeCountReceiver sets the same count on every entry of a
                // multi-launcher-activity package (one badge per installed app, not per
                // activity), so clearing must be symmetric or a sibling component's badge would
                // be left stale after the user opens a different entry point of the same app.
                // Skips the write entirely for an entry that was never set - avoids a Room
                // transaction on every single app launch for the common no-badge case.
                RAppsSingleton.instance.apps.orEmpty()
                    .filter { it.packageName.toString() == packageName && it.notificationCount != 0 }
                    .forEach { AppPersistent.setNotificationCount(packageName, it.name.toString(), 0) }

                // Resort apps if sorting by open count
                val utilSettings = UtilSettings(context)
                if (utilSettings.sortType in listOf(SortType.OPEN_COUNT_ASCENDING, SortType.OPEN_COUNT_DESCENDING)) {
                    context.sendBroadcast(Intent(context, AppsEditedReceiver::class.java))
                }
                com.mckimquyen.services.AppEventManager.notifyAppsEdited()
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.error_app_not_found, Toast.LENGTH_SHORT).show()
            }
        }

        // Check if app is locked with biometric
        val isAppOpened = AppPersistent.getAppOpened(packageName, name, userId)
        if (isAppOpened) {
            launch()
        } else {
            (context as? AppCompatActivity)?.let { activity ->
                packageName?.let { pkg ->
                    Biometric.toggleLockApp(activity, label ?: "", pkg, false) { _, _ -> launch() }
                }
            }
        }
    }

    /**
     * FEAT-012: starts [packageName]/[name] inside another profile. A plain intent cannot cross profiles, so this goes
     * through [LauncherApps.startMainActivity]; if the profile has been removed nothing is launched.
     */
    private fun launchInProfile(context: Context, packageName: String, name: String, userId: Int, bounds: Rect?) {
        val directory = LauncherAppsDirectory(context)
        val route = ProfileAppSource.routeFor(userId, directory.profileUserIds())
        val handle = directory.handleOf(userId)
        val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
        if (route != ProfileAppSource.LaunchRoute.LAUNCHER_APPS || handle == null || launcherApps == null) {
            Toast.makeText(context, R.string.error_app_not_found, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            launcherApps.startMainActivity(ComponentName(packageName, name), handle, bounds, null)
            AppPersistent.incrementAppCount(packageName, name, userId)
            com.mckimquyen.services.AppEventManager.notifyAppsEdited()
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.error_app_not_found, Toast.LENGTH_SHORT).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.error_app_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * SEARCH-003: shared with AppAdapter so search-result row actions and the Apps-tab popup
     * menu build the exact same "app info" intent instead of two copies drifting apart.
     */
    @JvmStatic
    fun appInfoIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:$packageName".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** SEARCH-003: shared with AppAdapter - see [appInfoIntent]. Goes through the standard
     *  system uninstall confirmation, never a silent PackageManager uninstall call. */
    @JvmStatic
    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE).apply {
            data = "package:$packageName".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Get launcher animation options bundle
     */
    private fun getLauncherOptionsBundle(context: Context, source: View?, bounds: Rect?): Bundle? {
        return source?.let {
            val options = if (bounds != null) {
                ActivityOptionsCompat.makeClipRevealAnimation(it, bounds.left, bounds.top, bounds.width(), bounds.height())
            } else {
                ActivityOptionsCompat.makeCustomAnimation(context, R.anim.a_fade_in, R.anim.a_fade_out)
            }
            options.toBundle()
        }
    }
}
