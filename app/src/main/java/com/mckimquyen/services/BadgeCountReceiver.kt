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
 *
 * Accepted, disclosed tradeoff: like every other launcher implementing this de-facto convention,
 * the broadcast carries no sender authentication - any installed app can claim to be any other
 * installed app's `EXTRA_PACKAGE_NAME` and set its displayed count. Impact is bounded to a
 * cosmetic unread-count digit (never real notification content, and only for a package that is
 * actually installed), the same trust model this convention has always had industry-wide; adding
 * real authentication would require a new non-standard contract that real senders (Gmail etc.)
 * don't implement, defeating the feature.
 */
class BadgeCountReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
        // UI-024 fix: a package can expose more than one launcher activity (more than one App
        // entry sharing this packageName) - apply the same count to every one of them instead of
        // just the first match, so the badge doesn't land on an arbitrary/order-dependent entry.
        val matches = RAppsSingleton.instance.apps.orEmpty()
            .filter { it.packageName.toString() == packageName }
        if (matches.isEmpty()) return
        val count = parseBadgeCount(intent.extras)
        matches.forEach { AppPersistent.setNotificationCount(packageName, it.name.toString(), count) }
        AppEventManager.notifyAppsEdited()
    }

    companion object {
        const val ACTION_BADGE_COUNT_UPDATE = "android.intent.action.BADGE_COUNT_UPDATE"
        const val EXTRA_PACKAGE_NAME = "badge_count_package_name"
        const val EXTRA_COUNT = "badge_count"

        // Accepted per the convention's payload shape but not read: matching by packageName
        // alone is sufficient for this feature (one badge per installed app, not per activity -
        // applied to every App entry sharing that packageName, see onReceive).
        const val EXTRA_CLASS_NAME = "badge_count_class_name"

        /** Pure, unit-testable: extras -> a validated, clamped count. Never throws. */
        @JvmStatic
        fun parseBadgeCount(extras: Bundle?): Int =
            (extras?.getInt(EXTRA_COUNT, 0) ?: 0).coerceIn(0, AppPersistent.MAX_STORED_NOTIFICATION_COUNT)
    }
}
