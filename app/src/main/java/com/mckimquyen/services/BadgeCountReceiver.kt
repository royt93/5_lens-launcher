package com.mckimquyen.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.AppPersistent

/**
 * UI-024: receives the de-facto "badge count" broadcast convention (popularized by
 * ShortcutBadger) that some notifying apps (Gmail and others) send, explicitly targeted at the
 * current default launcher's package, when their own badge count changes. This is intentionally
 * NOT a NotificationListenerService: no notification content is ever read, no special/dangerous
 * permission is required, and no Play Console Restricted Permissions review applies - the
 * tradeoff is partial coverage (only apps that actively send this broadcast get a badge).
 *
 * Registered statically in AndroidManifest.xml (not programmatically, unlike this app's other
 * receivers - see BroadcastReceivers.kt) because this broadcast is explicit (the sender targets
 * this app's package directly), so it is not subject to the Android 8+ implicit-broadcast
 * background-execution limits that made RApplication switch its own receivers to programmatic
 * registration; an explicit broadcast to a manifest-declared receiver still wakes this app's
 * process even when it isn't running.
 */
class BadgeCountReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        val app = RAppsSingleton.instance.apps.orEmpty()
            .firstOrNull { it.packageName.toString() == packageName } ?: return
        val count = parseBadgeCount(intent.extras)
        AppPersistent.setNotificationCount(packageName, app.name.toString(), count)
        AppEventManager.notifyAppsEdited()
    }

    companion object {
        const val ACTION_BADGE_COUNT_UPDATE = "android.intent.action.BADGE_COUNT_UPDATE"
        const val EXTRA_PACKAGE_NAME = "badge_count_package_name"
        const val EXTRA_COUNT = "badge_count"

        // Accepted per the convention's payload shape but not read: matching by packageName
        // alone is sufficient for this feature (one badge per installed app, not per activity).
        const val EXTRA_CLASS_NAME = "badge_count_class_name"

        private const val MAX_STORED_NOTIFICATION_COUNT = 9999

        /** Pure, unit-testable: extras -> a validated, clamped count. Never throws. */
        @JvmStatic
        fun parseBadgeCount(extras: Bundle?): Int =
            (extras?.getInt(EXTRA_COUNT, 0) ?: 0).coerceIn(0, MAX_STORED_NOTIFICATION_COUNT)
    }
}
