package com.mckimquyen.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Collection of BroadcastReceivers for handling app events.
 *
 * Fix BUG-13: Bypass deprecated Observable layer — gọi AppEventManager trực tiếp.
 * Trước đây: BroadcastReceiver → XxxObservable.instance.update() → AppEventManager
 * Bây giờ:   BroadcastReceiver → AppEventManager (direct, clean, no deprecated wrapper)
 *
 * XxxObservable classes vẫn giữ lại (không xóa) để tránh compile error nếu còn
 * bất kỳ chỗ nào reference, nhưng không được gọi từ đây nữa.
 */
class BroadcastReceivers {

    class AppsUpdatedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            android.util.Log.d("roy93~", "BroadcastReceivers: AppsUpdatedReceiver onReceive! Action: ${intent.action}")
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
