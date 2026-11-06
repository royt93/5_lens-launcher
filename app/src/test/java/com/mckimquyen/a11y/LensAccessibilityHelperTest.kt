package com.mckimquyen.a11y

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.model.App
import com.mckimquyen.views.LensAccessibilityHelper
import com.mckimquyen.views.LensView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A11Y-001: Unit tests for [LensAccessibilityHelper] virtual view hierarchy.
 *
 * Verifies that custom canvas coordinates are correctly resolved to virtual view IDs,
 * accessibility node info is properly populated with app labels and button semantics,
 * and accessibility click/long-click actions dispatch to registered handlers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LensAccessibilityHelperTest {

    private lateinit var context: Context
    private lateinit var mockLensView: LensView
    private lateinit var sampleApps: List<App>
    private val appRects = mutableMapOf<Int, Rect>()
    private var lastClickedIndex = -1
    private var lastLongClickedIndex = -1

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockLensView = mock(LensView::class.java)
        // Unit tests run without merged Android resources (see FISH-011: enabling them runs the real
        // RApplication), so the three FISH-029 strings are stubbed on a mocked context.
        val stringContext = mock(Context::class.java)
        `when`(stringContext.getString(com.mckimquyen.R.string.lens_focus_lock_action)).thenReturn(ACTION_TITLE)
        `when`(stringContext.getString(com.mckimquyen.R.string.lens_focus_locked)).thenReturn(STATE_LOCKED)
        `when`(stringContext.getString(com.mckimquyen.R.string.lens_focus_unlocked)).thenReturn(STATE_RELEASED)
        `when`(mockLensView.context).thenReturn(stringContext)

        sampleApps = listOf(
            App(id = 1, label = "Calculator", packageName = "com.android.calculator2", name = "CalculatorActivity"),
            App(id = 2, label = "Camera", packageName = "com.android.camera2", name = "CameraActivity"),
            App(id = 3, label = "Settings", packageName = "com.android.settings", name = "SettingsActivity")
        )

        appRects[0] = Rect(10, 10, 50, 50)
        appRects[1] = Rect(60, 10, 100, 50)
        appRects[2] = Rect(110, 10, 150, 50)

        lastClickedIndex = -1
        lastLongClickedIndex = -1
    }

    private var focusPinned = false
    private var focusToggleCalls = 0
    private var focusToggleResult = true

    private fun createHelper(
        apps: List<App>? = sampleApps,
        showBadgesSetting: Boolean = false,
        withFocusLock: Boolean = false
    ): LensAccessibilityHelper {
        return LensAccessibilityHelper(
            host = mockLensView,
            appProvider = { apps },
            rectProvider = { index, outRect ->
                val r = appRects[index]
                if (r != null) {
                    outRect.set(r)
                    true
                } else {
                    false
                }
            },
            onAppClicked = { index -> lastClickedIndex = index },
            onAppLongClicked = { index ->
                lastLongClickedIndex = index
                true
            },
            showBadgesSettingProvider = { showBadgesSetting },
            focusLockPinnedProvider = { focusPinned },
            onFocusLockToggle = if (withFocusLock) {
                { focusToggleCalls++; if (focusToggleResult) focusPinned = !focusPinned; focusToggleResult }
            } else null
        )
    }

    @Test
    fun `getVirtualViewAt - resolves correct app index when touching inside item bounds`() {
        val helper = createHelper()

        // Point inside App 0 (Rect: 10, 10, 50, 50)
        val id0 = helper.testGetVirtualViewAt(25f, 25f)
        assertEquals(0, id0)

        // Point inside App 1 (Rect: 60, 10, 100, 50)
        val id1 = helper.testGetVirtualViewAt(80f, 30f)
        assertEquals(1, id1)

        // Point inside App 2 (Rect: 110, 10, 150, 50)
        val id2 = helper.testGetVirtualViewAt(120f, 40f)
        assertEquals(2, id2)
    }

    @Test
    fun `getVirtualViewAt - returns INVALID_ID when touching outside any item bounds`() {
        val helper = createHelper()

        val idOutside = helper.testGetVirtualViewAt(200f, 200f)
        assertEquals(ExploreByTouchHelper.INVALID_ID, idOutside)
    }

    @Test
    fun `getVirtualViewAt - returns INVALID_ID when app list is null`() {
        val helper = createHelper(apps = null)

        val id = helper.testGetVirtualViewAt(25f, 25f)
        assertEquals(ExploreByTouchHelper.INVALID_ID, id)
    }

    @Test
    fun `getVisibleVirtualViews - populates all virtual view IDs`() {
        val helper = createHelper()
        val ids = mutableListOf<Int>()

        helper.testGetVisibleVirtualViews(ids)

        assertEquals(listOf(0, 1, 2), ids)
    }

    @Test
    fun `onPopulateNodeForVirtualView - populates label, class name and actions`() {
        val helper = createHelper()
        val platformNode = AccessibilityNodeInfo.obtain()
        val node = AccessibilityNodeInfoCompat.wrap(platformNode)

        helper.testPopulateNodeForVirtualView(1, node)

        assertEquals("Camera", node.contentDescription)
        assertEquals("Camera", node.text)
        assertEquals("android.widget.Button", node.className)
        assertTrue(node.isClickable)
        assertTrue(node.isFocusable)
        assertTrue(node.isLongClickable)

        val bounds = Rect()
        @Suppress("DEPRECATION")
        node.getBoundsInParent(bounds)
        assertEquals(Rect(60, 10, 100, 50), bounds)
    }

    @Test
    fun `onPopulateNodeForVirtualView - UI-024 stays silent on unread count when badges setting is off`() {
        val appsWithBadge = listOf(sampleApps[0].copyWithNotificationCount(3), sampleApps[1], sampleApps[2])
        val helper = createHelper(apps = appsWithBadge, showBadgesSetting = false)
        val node = AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain())

        helper.testPopulateNodeForVirtualView(0, node)

        assertEquals("Calculator", node.contentDescription)
    }

    @Test
    fun `onPerformActionForVirtualView - ACTION_CLICK triggers onAppClicked`() {
        val helper = createHelper()

        val result = helper.testPerformActionForVirtualView(
            id = 2,
            action = AccessibilityNodeInfoCompat.ACTION_CLICK,
            args = null
        )

        assertTrue(result)
        assertEquals(2, lastClickedIndex)
    }

    @Test
    fun `onPerformActionForVirtualView - ACTION_LONG_CLICK triggers onAppLongClicked`() {
        val helper = createHelper()

        val result = helper.testPerformActionForVirtualView(
            id = 0,
            action = AccessibilityNodeInfoCompat.ACTION_LONG_CLICK,
            args = null
        )

        assertTrue(result)
        assertEquals(0, lastLongClickedIndex)
    }

    @Test
    fun `onPerformActionForVirtualView - unknown action returns false`() {
        val helper = createHelper()

        val result = helper.testPerformActionForVirtualView(
            id = 0,
            action = AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD,
            args = null
        )

        assertFalse(result)
        assertEquals(-1, lastClickedIndex)
    }

    // ============================================================ FISH-029 (narrow TalkBack exception)

    @Test
    fun `host node exposes exactly one toggle focus lock action and app nodes do not`() {
        val helper = createHelper(withFocusLock = true)
        val host = AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain())
        helper.testPopulateNodeForHost(host)
        val ids = host.actionList.map { it.id }
        assertEquals(1, ids.count { it == com.mckimquyen.R.id.action_toggle_focus_lock })
        assertEquals(ACTION_TITLE, host.actionList.first { it.id == com.mckimquyen.R.id.action_toggle_focus_lock }.label)

        val app = AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain())
        helper.testPopulateNodeForVirtualView(0, app)
        assertFalse(app.actionList.any { it.id == com.mckimquyen.R.id.action_toggle_focus_lock })
    }

    @Test
    fun `host action toggles deterministically and reports state description`() {
        val helper = createHelper(withFocusLock = true)
        assertTrue(helper.testPerformActionForHost(com.mckimquyen.R.id.action_toggle_focus_lock))
        assertTrue(focusPinned)
        val pinned = AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain())
        helper.testPopulateNodeForHost(pinned)
        assertEquals(STATE_LOCKED, pinned.stateDescription)

        assertTrue(helper.testPerformActionForHost(com.mckimquyen.R.id.action_toggle_focus_lock))
        assertFalse(focusPinned)
        val released = AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain())
        helper.testPopulateNodeForHost(released)
        assertEquals(STATE_RELEASED, released.stateDescription)
        assertEquals(2, focusToggleCalls)
    }

    @Test
    fun `host action reports failure and leaves state unchanged when the view cannot toggle`() {
        focusToggleResult = false
        val helper = createHelper(withFocusLock = true)
        assertFalse(helper.testPerformActionForHost(com.mckimquyen.R.id.action_toggle_focus_lock))
        assertFalse(focusPinned)
    }

    @Test
    fun `no focus lock action without a toggle handler`() {
        val helper = createHelper(withFocusLock = false)
        val host = AccessibilityNodeInfoCompat.wrap(AccessibilityNodeInfo.obtain())
        helper.testPopulateNodeForHost(host)
        assertFalse(host.actionList.any { it.id == com.mckimquyen.R.id.action_toggle_focus_lock })
        assertFalse(helper.testPerformActionForHost(com.mckimquyen.R.id.action_toggle_focus_lock))
    }

    private companion object {
        const val ACTION_TITLE = "Toggle focus lock"
        const val STATE_LOCKED = "Focus locked"
        const val STATE_RELEASED = "Focus released"
    }
}
