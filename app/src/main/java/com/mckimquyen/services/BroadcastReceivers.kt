package com.mckimquyen.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mckimquyen.util.Logger

/**
 * Collection of BroadcastReceivers for handling app events.
 *
 * Fix BUG-13: Bypass deprecated Observable layer — gọi AppEventManager trực tiếp.
 * Trước đây: BroadcastReceiver → XxxObservable.instance.update() → AppEventManager
 * Bây giờ:   BroadcastReceiver → AppEventManager (direct, clean, no deprecated wrapper)
 *
 * The XxxObservable wrapper classes this comment used to reference have since been deleted
 * outright (an audit confirmed every call site had migrated to AppEventManager) — there is
 * no compat layer left to bypass, only this direct path.
 */
class BroadcastReceivers {

    class AppsUpdatedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Logger.d("BroadcastReceivers: AppsUpdatedReceiver onReceive! Action: ${intent.action}")
            val application = context.applicationContext as? com.mckimquyen.app.RApplication
            val changedPackage = intent.data?.schemeSpecificPart
            val selectedPackage = com.mckimquyen.util.UtilSettings(context)
                .getString(com.mckimquyen.util.UtilSettings.KEY_ICON_PACK_PACKAGE_NAME)
            val packageChanged = when (intent.action) {
                Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_PACKAGE_ADDED ->
                    !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                Intent.ACTION_PACKAGE_REPLACED, Intent.ACTION_PACKAGE_CHANGED -> true
                else -> false
            }
            if (packageChanged && !changedPackage.isNullOrEmpty() && changedPackage == selectedPackage) {
                application?.iconPackManager?.invalidate()
            }
            AppEventManager.notifyAppsUpdated()
        }
    }

    class AppsEditedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppEventManager.notifyAppsEdited()
        }
    }

    class AppsVisibilityChangedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppEventManager.notifyVisibilityChanged()
        }
    }

    class AppsLockChangedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppEventManager.notifyLockChanged()
        }
    }

    class AppsLoadedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppEventManager.notifyAppsLoaded()
        }
    }

    class BackgroundChangedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppEventManager.notifyBackgroundChanged()
        }
    }

    class NightModeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppEventManager.notifyNightModeChanged()
        }
    }

    class LocaleChangedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_LOCALE_CHANGED) {
                AppEventManager.notifyAppsUpdated()
            }
        }
    }
}
