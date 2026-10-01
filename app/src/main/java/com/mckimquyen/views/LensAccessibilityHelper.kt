package com.mckimquyen.views

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.VisibleForTesting
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.mckimquyen.R
import com.mckimquyen.model.App

/**
 * A11Y-001: Virtual accessibility node hierarchy for [LensView].
 *
 * Exposes each app icon drawn on the custom canvas as an accessible virtual view
 * to screen readers (TalkBack), supporting touch-exploration, D-pad/keyboard navigation,
 * action clicks, and action long-clicks.
 */
class LensAccessibilityHelper(
    private val host: LensView,
    private val appProvider: () -> List<App>?,
    private val rectProvider: (index: Int, outRect: Rect) -> Boolean,
    private val onAppClicked: (index: Int) -> Unit,
    private val onAppLongClicked: ((index: Int) -> Boolean)? = null,
    // UI-024: defaults to "off" so existing tests that don't pass this still get the pre-badge
    // announcement behavior rather than silently depending on a real SharedPreferences read.
    private val showBadgesSettingProvider: () -> Boolean = { false }
) : ExploreByTouchHelper(host) {

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        val apps = appProvider() ?: return INVALID_ID
        val rect = Rect()
        val xi = x.toInt()
        val yi = y.toInt()
        for (i in apps.indices) {
            if (rectProvider(i, rect) && rect.contains(xi, yi)) {
                return i
            }
        }
        return INVALID_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        val apps = appProvider() ?: return
        for (i in apps.indices) {
            virtualViewIds.add(i)
        }
    }

    override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
        val apps = appProvider()
        if (apps == null || virtualViewId !in apps.indices) {
            node.text = ""
            node.contentDescription = ""
            node.setBoundsInParent(Rect(0, 0, 1, 1))
            return
        }

        val app = apps[virtualViewId]
        // UI-024: same gate the visual badge paths use (LensView/AppAdapter) - TalkBack must not
        // announce an unread count the user has turned the badge display off for.
        val label = if (LensView.shouldDrawNotificationBadge(showBadgesSettingProvider(), app.notificationCount)) {
            host.context.getString(
                R.string.notification_badge_count_description,
                app.label.toString(),
                app.notificationCount
            )
        } else {
            app.label.toString()
        }

        node.text = label
        node.contentDescription = label
        node.className = "android.widget.Button"
        node.isFocusable = true
        node.isClickable = true
        node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        if (onAppLongClicked != null) {
            node.isLongClickable = true
            node.addAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)
        }

        val bounds = Rect()
        if (rectProvider(virtualViewId, bounds) && !bounds.isEmpty) {
            node.setBoundsInParent(bounds)
        } else {
            node.setBoundsInParent(Rect(0, 0, 1, 1))
        }
    }

    override fun onPerformActionForVirtualView(
        virtualViewId: Int,
        action: Int,
        arguments: Bundle?
    ): Boolean {
        val apps = appProvider() ?: return false
        if (virtualViewId !in apps.indices) return false

        return when (action) {
            AccessibilityNodeInfoCompat.ACTION_CLICK -> {
                onAppClicked(virtualViewId)
                true
            }
            AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> {
                onAppLongClicked?.invoke(virtualViewId) ?: false
            }
            else -> false
        }
    }

    override fun onPopulateEventForVirtualView(virtualViewId: Int, event: AccessibilityEvent) {
        val apps = appProvider()
        if (apps != null && virtualViewId in apps.indices) {
            val label = apps[virtualViewId].label.toString()
            event.contentDescription = label
            event.text.add(label)
        }
    }

    @VisibleForTesting
    internal fun testGetVirtualViewAt(x: Float, y: Float): Int = getVirtualViewAt(x, y)

    @VisibleForTesting
    internal fun testGetVisibleVirtualViews(ids: MutableList<Int>) = getVisibleVirtualViews(ids)

    @VisibleForTesting
    internal fun testPopulateNodeForVirtualView(id: Int, node: AccessibilityNodeInfoCompat) =
        onPopulateNodeForVirtualView(id, node)

    @VisibleForTesting
    internal fun testPerformActionForVirtualView(id: Int, action: Int, args: Bundle?) =
        onPerformActionForVirtualView(id, action, args)
}
