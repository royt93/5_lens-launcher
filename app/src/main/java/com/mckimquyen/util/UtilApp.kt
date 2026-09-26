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
import com.mckimquyen.enums.SortType
import com.mckimquyen.ext.Biometric
import com.mckimquyen.model.App
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.services.BroadcastReceivers.AppsEditedReceiver
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UtilApp {

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
        val selectedIconPack = iconPacks.find { it.mName == iconPackLabelName }
        // CORE-002: icon-pack identity/version folded into every icon cache key below,
        // computed once since it is the same for every app in this refresh.
        val iconPackToken = selectedIconPack?.let { pack ->
            "${pack.mPackageName}@${versionTokenOf(packageManager, pack.mPackageName)}"
        } ?: "system"

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
            val existingPersistent = AppDatabase.getInstance().appPersistentDao().findAnyByIdentifier(identifier)
            var paletteColor = existingPersistent?.paletteColor ?: 0
            if (paletteColor == 0 && icon != null) {
                paletteColor = UtilColor.getPaletteColorFromBitmap(icon)
                if (paletteColor != 0) {
                    AppPersistent.setAppPaletteColorAndAwait(packageName, name, paletteColor)
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
        return shells
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
        val persistentMap = AppDatabase.getInstance().appPersistentDao()
            .getAllForLens(lensId)
            .associateBy { it.identifier }

        val apps = ArrayList<App>(shells.size)
        shells.forEach { shell ->
            val identifier = AppPersistent.generateIdentifier(shell.packageName.toString(), shell.name.toString())
            val persistent = persistentMap[identifier]
            apps.add(
                shell.copy(
                    isOpened = persistent?.appOpened ?: true,
                    isVisible = persistent?.appVisible ?: true,
                    openCount = persistent?.openCount ?: 0L,
                    orderNumber = persistent?.orderNumber ?: -1,
                    isFavorite = persistent?.isFavorite ?: false,
                    folderName = persistent?.folderName,
                    pinnedZone = PinnedZone.fromStored(persistent?.pinnedZone),
                    lensId = lensId
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
        val iconPackLabelName = UtilSettings(application).getString(UtilSettings.KEY_ICON_PACK_LABEL_NAME)
            ?: UtilSettings.DEFAULT_ICON_PACK_LABEL_NAME
        val selectedIconPack = UtilIconPackManager().getAvailableIconPacksWithIcons(true, application)
            .find { it.mName == iconPackLabelName }
        selectedIconPack?.getIconForPackage(packageName, defaultBitmap) ?: defaultBitmap
    }

    /**
     * Launch app component with optional biometric authentication
     */
    @JvmStatic
    fun launchComponent(
        context: Context,
        packageName: String?,
        label: String?,
        name: String?,
        view: View?,
        bounds: Rect?
    ) {
        fun launch() {
            if (packageName == null || name == null) {
                Toast.makeText(context, R.string.error_app_not_found, Toast.LENGTH_SHORT).show()
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
                AppPersistent.incrementAppCount(packageName, name)

                // Resort apps if sorting by open count
                val utilSettings = UtilSettings(context)
                if (utilSettings.sortType in listOf(SortType.OPEN_COUNT_ASCENDING, SortType.OPEN_COUNT_DESCENDING)) {
                    context.sendBroadcast(Intent(context, AppsEditedReceiver::class.java))
                }
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.error_app_not_found, Toast.LENGTH_SHORT).show()
            }
        }

        // Check if app is locked with biometric
        val isAppOpened = AppPersistent.getAppOpened(packageName, name)
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
