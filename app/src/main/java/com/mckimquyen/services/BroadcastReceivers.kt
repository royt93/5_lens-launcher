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
}
