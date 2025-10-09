package com.mckimquyen.util

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
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
import com.mckimquyen.services.BroadcastReceivers.AppsEditedReceiver
import com.mckimquyen.util.UtilIconPackManager.IconPack
import java.util.*

object UtilApp {
    // Get all available apps for launcher
    @JvmStatic
    fun getApps(
        packageManager: PackageManager,
        context: Context?,
        application: Application?,
        iconPackLabelName: String,
        sortType: SortType?,
    ): ArrayList<App> {
        val apps = ArrayList<App>()
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        var availableActivities: List<ResolveInfo>? = null
        try {
            availableActivities = packageManager.queryIntentActivities(intent, 0)
        } catch (e: RuntimeException) {
            e.printStackTrace()
            Toast.makeText(
                /* context = */ context,
                /* resId = */ R.string.error_too_many_apps,
                /* duration = */ Toast.LENGTH_SHORT
            ).show()
        }
        if (availableActivities != null) {
            var selectedIconPack: IconPack? = null
            val iconPacks = UtilIconPackManager().getAvailableIconPacksWithIcons(
                /* forceReload = */ true,
                /* application = */ application
            )
            for (iconPack in iconPacks) {
                if (iconPack.mName == iconPackLabelName) {
                    selectedIconPack = iconPack
                }
            }
            for (i in availableActivities.indices) {
                val resolveInfo = availableActivities[i]

                // Extract install date
                val installDate = try {
                    packageManager.getPackageInfo(
                        resolveInfo.activityInfo.packageName,
                        0
                    ).firstInstallTime
                } catch (e: PackageManager.NameNotFoundException) {
                    0L
                }

                // Extract app info
                val label = resolveInfo.loadLabel(packageManager)
                val packageName = resolveInfo.activityInfo.packageName
                val name = resolveInfo.activityInfo.name
                val iconResId = resolveInfo.activityInfo.iconResource

                // Get icon bitmap
                val defaultBitmap = UtilBitmap.packageNameToBitmap(
                    /* packageManager = */ packageManager,
                    /* packageName = */ packageName,
                    /* resId = */ iconResId
                )
                val icon = if (selectedIconPack != null) {
                    selectedIconPack.getIconForPackage(
                        Objects.requireNonNull(packageName).toString(),
                        defaultBitmap
                    )
                } else {
                    defaultBitmap
                }

                // Create immutable App instance
                val tempApp = App(
                    id = i,
                    label = label,
                    packageName = packageName,
                    name = name,
                    iconResId = iconResId,
                    icon = icon,
                    installDate = installDate,
                    paletteColor = 0 // Will be set next
                )

                // Calculate and set palette color
                val app = tempApp.copyWithPaletteColor(
                    UtilColor.getPaletteColorFromApp(tempApp)
                )

                apps.add(app)
            }
        }
        UtilAppSorter.sort(/* apps = */ apps, /* sortType = */ sortType)
        return apps
    }

    // Launch apps, for launcher :-P
    @JvmStatic
    fun launchComponent(
        context: Context,
        packageName: String?,
        label: String?,
        name: String?,
        view: View?,
        bounds: Rect?,
    ) {
        fun launch() {
            if (packageName != null && name != null) {
                val componentIntent = Intent()
                componentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                componentIntent.component = ComponentName(/* pkg = */ packageName, /* cls = */ name)
                if (packageName != PKG_NAME) {
                    componentIntent.action = Intent.ACTION_MAIN
                }
                componentIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                try {
                    // Launch Component
                    ContextCompat.startActivity(
                        /* context = */ context,
                        /* intent = */ componentIntent,
                        /* options = */ getLauncherOptionsBundle(
                            context = context, source = view, bounds = bounds
                        )
                    )
                    // Increment app open count
                    AppPersistent.incrementAppCount(packageName, name)
                    // Resort apps (if open count selected)
                    val utilSettings = UtilSettings(context)
                    if (utilSettings.sortType == SortType.OPEN_COUNT_ASCENDING || utilSettings.sortType == SortType.OPEN_COUNT_DESCENDING) {
                        val editAppsIntent = Intent(context, AppsEditedReceiver::class.java)
                        context.sendBroadcast(editAppsIntent)
                    }
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(
                        /* context = */ context,
                        /* resId = */ R.string.error_app_not_found,
                        /* duration = */ Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    /* context = */ context,
                    /* resId = */R.string.error_app_not_found,
                    /* duration = */Toast.LENGTH_SHORT
                ).show()
            }
        }

        val isAppOpened = AppPersistent.getAppOpened(packageName, name)
        if (isAppOpened) {
            launch()
        } else {
            //biometric
            (context as? AppCompatActivity)?.let { a ->
                packageName?.let { pk ->
                    Biometric.toggleLockApp(
                        a = a,
                        appName = label ?: "",
                        packageName = pk,
                        isAppLock = false
                    ) { _: String?, _: Boolean? ->
                        launch()
                    }
                }
            }
        }
    }

    private fun getLauncherOptionsBundle(
        context: Context, source: View?, bounds: Rect?,
    ): Bundle? {
        var optionsBundle: Bundle? = null
        if (source != null) {
            val options: ActivityOptionsCompat =
                if (bounds != null) {
                    // Clip reveal animation for Marshmallow and above
                    ActivityOptionsCompat.makeClipRevealAnimation(
                        /* source = */ source,
                        /* startX = */ bounds.left,
                        /* startY = */ bounds.top,
                        /* width = */ bounds.width(),
                        /* height = */ bounds.height()
                    )
                } else {
                    // Fade animation otherwise
                    ActivityOptionsCompat.makeCustomAnimation(
                        /* context = */ context,
                        /* enterResId = */ R.anim.a_fade_in,
                        /* exitResId = */ R.anim.a_fade_out
                    )
                }
            optionsBundle = options.toBundle()
        }
        return optionsBundle
    }
}
