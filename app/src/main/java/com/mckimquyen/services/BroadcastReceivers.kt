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

    class AppsUpdatedReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            UpdatedObservable.instance.update()
        }
    }

    class AppsEditedReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            EditedObservable.instance.update()
        }
    }

    class AppsVisibilityChangedReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            VisibilityChangedObservable.instance.update()
        }
    }

    class AppsLockChangedReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            LockChangedObservable.instance.update()
        }
    }

    class AppsLoadedReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            LoadedObservable.instance.update()
        }
    }

    class BackgroundChangedReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            BackgroundChangedObservable.instance.update()
        }
    }

    class NightModeReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            NightModeObservable.instance.update()
        }
    }
}
