package com.mckimquyen.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Collection of BroadcastReceivers for handling app events.
 * <p>
 * Migrated from deprecated Observable pattern to AppEventManager (LiveData).
 * Each receiver notifies AppEventManager when corresponding event occurs.
 * <p>
 * Fix: Removed deprecated Observable wrappers, use AppEventManager directly
 */
class BroadcastReceivers {

    /**
     * Receives broadcast when apps list is updated (e.g., app installed/uninstalled)
     */
    class AppsUpdatedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppEventManager.notifyAppsUpdated()
        }
    }

    /**
     * Receives broadcast when an app is edited (e.g., order changed)
     */
    class AppsEditedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppEventManager.notifyAppsEdited()
        }
    }

    /**
     * Receives broadcast when app visibility is changed (show/hide)
     */
    class AppsVisibilityChangedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppEventManager.notifyVisibilityChanged()
        }
    }

    /**
     * Receives broadcast when app lock status is changed (biometric protection)
     */
    class AppsLockChangedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppEventManager.notifyLockChanged()
        }
    }

    /**
     * Receives broadcast when apps are loaded for the first time
     */
    class AppsLoadedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppEventManager.notifyAppsLoaded()
        }
    }

    /**
     * Receives broadcast when background/wallpaper is changed
     */
    class BackgroundChangedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppEventManager.notifyBackgroundChanged()
        }
    }

    /**
     * Receives broadcast when night mode setting is changed
     */
    class NightModeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AppEventManager.notifyNightModeChanged()
        }
    }
}
