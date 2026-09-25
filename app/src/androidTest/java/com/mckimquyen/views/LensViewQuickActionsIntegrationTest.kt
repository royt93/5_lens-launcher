package com.mckimquyen.views

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActHome
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-022: the one gap a bare, unattached `LensView` (as `LensViewWidgetTest`/
 * `LensViewAccessibilityWidgetTest` construct it) can't prove — that the quick-actions
 * `PopupMenu` genuinely shows on a real, attached, themed window, not just "doesn't crash".
 * A real bug was found this exact way while building this story: `androidx.appcompat.widget
 * .PopupMenu.inflate()` threw on a non-Activity (bare Application) context because the
 * `PopupMenuTheme` overlay had no base Material3 theme to layer onto - this test uses the
 * real `ActHome` Activity (same pattern `AdaptiveMultiWindowIntegrationTest` already
 * established) specifically so that class of gap can't hide behind a swallowed exception.
 */
@RunWith(AndroidJUnit4::class)
class LensViewQuickActionsIntegrationTest {

    @Before
    fun setup() {
        RAppsSingleton.instance.apps = arrayListOf(
            App(packageName = "com.ui022.test.app0", name = "App0", label = "App 0")
        )
    }

    @After
    fun tearDown() {
        RAppsSingleton.instance.clearAllData()
    }

    @Test
    fun testShowAppOptionsAtIndex_realAttachedThemedWindow_actuallySucceeds() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        try {
            var result = false
            scenario.onActivity { activity ->
                val lensView = activity.findViewById<LensView>(R.id.lensViews)
                result = lensView.showAppOptionsAtIndex(0)
            }
            assertTrue(
                "a real, attached, themed LensView must actually show the quick-actions popup, not silently fail",
                result
            )
        } finally {
            scenario.close()
        }
    }

    /**
     * A11Y-001 non-gesture-path acceptance, proven end-to-end this time (not just "reaches
     * the method" as `LensViewAccessibilityWidgetTest` proves on a bare, unattached view):
     * TalkBack's real long-click action, through the real accessibility helper, on a real
     * attached+themed window, must succeed exactly like the touch long-press does.
     */
    @Test
    fun testAccessibilityLongClickAction_realAttachedThemedWindow_actuallySucceeds() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        try {
            var handled = false
            scenario.onActivity { activity ->
                val lensView = activity.findViewById<LensView>(R.id.lensViews)
                val helper = lensView.getAccessibilityHelper()
                handled = helper?.testPerformActionForVirtualView(
                    0,
                    AccessibilityNodeInfoCompat.ACTION_LONG_CLICK,
                    null
                ) ?: false
            }
            assertTrue(
                "TalkBack's long-click action must reach the same quick-actions popup and actually show it",
                handled
            )
        } finally {
            scenario.close()
        }
    }
}
