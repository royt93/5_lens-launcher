package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.model.App
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-024 root-cause regression test. `assignApps()` skips re-rendering when
 * `isSameAppList(oldList, newList)` returns true - a deliberate perf optimization comparing only
 * a hand-picked field subset (packageName/name/label/isVisible/isOpened/openCount), not full
 * `App.equals()`. Real device smoke on the S24U caught that `notificationCount` was missing from
 * that subset: a badge broadcast updated `RAppsSingleton` and fired `AppEventManager.appsEdited`
 * correctly, but the list was judged "identical" and LensView never redrew - the badge silently
 * never appeared. This pins `isSameAppList` directly (same convention as `onLensMenuItemSelected`
 * - call the real package-private production method, not a reimplementation of it).
 */
@RunWith(AndroidJUnit4::class)
class ActHomeNotificationBadgeRefreshWidgetTest {

    private fun app(count: Int) = App(
        label = "Camera",
        packageName = "pkg.camera",
        name = "CameraActivity",
        notificationCount = count
    )

    @Test
    fun listsDifferingOnlyByNotificationCountAreNotConsideredIdentical() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val same = activity.isSameAppList(listOf(app(0)), listOf(app(4)))
                assertFalse(
                    "a changed notification count must trigger a re-render, not be skipped as an identical list",
                    same
                )
            }
        }
    }
}
