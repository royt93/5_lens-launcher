package com.mckimquyen.util

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import com.mckimquyen.R
import com.mckimquyen.enums.SortType
import com.mckimquyen.ext.Biometric
import com.mckimquyen.model.App
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.services.BroadcastReceivers.AppsEditedReceiver
import java.util.*

object UtilApp {

    /**
     * Get all available apps for launcher
     */
    @JvmStatic
    fun getApps(
        packageManager: PackageManager,
        context: Context?,
        application: Application?,
        iconPackLabelName: String,
        sortType: SortType?
    ): ArrayList<App> {
        val apps = ArrayList<App>()
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val availableActivities = try {
            packageManager.queryIntentActivities(intent, 0)
        } catch (e: RuntimeException) {
            e.printStackTrace()
            Toast.makeText(context, R.string.error_too_many_apps, Toast.LENGTH_SHORT).show()
            return apps
        }

        // Query all DB records once to avoid N database queries in loop
        val allPersistents = try {
            AppDatabase.getInstance().appPersistentDao().getAll()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList<AppPersistent>()
        }
        val persistentMap = allPersistents.associateBy { it.identifier }

        // Find selected icon pack
        val iconPacks = UtilIconPackManager().getAvailableIconPacksWithIcons(true, application)
        val selectedIconPack = iconPacks.find { it.mName == iconPackLabelName }

        for ((index, resolveInfo) in availableActivities.withIndex()) {
            // Extract install date
            val installDate = try {
                packageManager.getPackageInfo(resolveInfo.activityInfo.packageName, 0).firstInstallTime
            } catch (_: PackageManager.NameNotFoundException) {
                0L
            }

            // Extract app info
            val label = resolveInfo.loadLabel(packageManager)
            val packageName = resolveInfo.activityInfo.packageName
            val name = resolveInfo.activityInfo.name
            val iconResId = resolveInfo.activityInfo.iconResource

            // Get icon bitmap (use icon pack if available)
            val defaultBitmap = UtilBitmap.packageNameToBitmap(packageManager, packageName, iconResId)
            val icon = selectedIconPack?.getIconForPackage(packageName, defaultBitmap) ?: defaultBitmap

            val identifier = AppPersistent.generateIdentifier(packageName, name)
            val persistent = persistentMap[identifier]

            val isOpened = persistent?.appOpened ?: true
            val isVisible = persistent?.appVisible ?: true
            val openCount = persistent?.openCount ?: 0L

            // Cache palette color (Issue 3)
            var paletteColor = persistent?.paletteColor ?: 0
            if (paletteColor == 0 && icon != null) {
                paletteColor = UtilColor.getPaletteColorFromBitmap(icon)
                if (paletteColor != 0) {
                    AppPersistent.setAppPaletteColor(packageName, name, paletteColor)
                }
            }

            // Create App instance
            val app = App(
                id = index,
                label = label,
                packageName = packageName,
                name = name,
                iconResId = iconResId,
                icon = icon,
                installDate = installDate,
                paletteColor = paletteColor,
                isOpened = isOpened,
                isVisible = isVisible,
                openCount = openCount
            )
            apps.add(app)
        }

        UtilAppSorter.sort(apps, sortType)
        return apps
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
