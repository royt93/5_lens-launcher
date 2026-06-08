package com.mckimquyen.util

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.mckimquyen.ui.ActFakeLauncher

/**
 * Utility for managing launcher related operations
 */
object UtilLauncher {

    private const val ACTION_HOME_SETTINGS = "android.settings.HOME_SETTINGS"

    @JvmStatic
    fun isDefaultLauncher(application: Application): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }

        val res = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            application.packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            application.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        // Debug logging
        val currentPackage = application.packageName
        val resolvedPackage = res?.activityInfo?.packageName
        val resolvedActivity = res?.activityInfo?.name
        Logger.d("UtilLauncher", "isDefaultLauncher check:")
        Logger.d("UtilLauncher", "  Current package: $currentPackage")
        Logger.d("UtilLauncher", "  Resolved package: $resolvedPackage")
        Logger.d("UtilLauncher", "  Resolved activity: $resolvedActivity")

        // Check if resolved package matches current app
        val isDefault = res?.activityInfo?.let {
            it.packageName != "android" && it.packageName == currentPackage
        } ?: false

        Logger.d("UtilLauncher", "  Result: $isDefault")
        return isDefault
    }

    fun getNameHomeLauncher(application: Application): String {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val res = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            application.packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            application.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return res?.activityInfo?.loadLabel(application.packageManager)?.toString() ?: ""
    }

    @JvmStatic
    fun resetPreferredLauncherAndOpenChooser(context: Context) {
        val intent = Intent(ACTION_HOME_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return
        }

        // Fallback: Use fake launcher to trigger launcher chooser
        val packageManager = context.packageManager
        val componentName = ComponentName(context, ActFakeLauncher::class.java)

        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        val selector = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(selector)

        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP
        )
    }
}
