package com.mckimquyen.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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
