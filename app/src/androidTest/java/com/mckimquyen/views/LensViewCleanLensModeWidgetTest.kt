package com.mckimquyen.views

import android.content.Context
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Clean the Lens: real LensView draw path. Badge draw counter is the production test hook
 * (same convention as LensViewNotificationBadgeWidgetTest); the TalkBack label is read through
 * the real accessibility helper wired by LensView.
 */
@RunWith(AndroidJUnit4::class)
class LensViewCleanLensModeWidgetTest {

    private companion object {
        const val WIDTH_PX = 1080
        const val HEIGHT_PX = 1920
        const val BADGE_COUNT = 3
    }

    private lateinit var context: Context
    private lateinit var settings: UtilSettings
    private lateinit var lensView: LensView

    @Before
    fun setup() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(target).edit().clear().commit()
        context = ContextThemeWrapper(target, R.style.AppTheme)
        settings = UtilSettings(context)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { lensView = LensView(context) }
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    private fun layoutBadgedApp() {
        settings.save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, true)
        lensView.setApps(
            arrayListOf(
                App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity", notificationCount = BADGE_COUNT)
            )
        )
        lensView.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY)
        )
        lensView.layout(0, 0, WIDTH_PX, HEIGHT_PX)
    }

    @Test
    fun cleanModeOff_badgeIsDrawn() {
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
        layoutBadgedApp()
        val before = lensView.badgeDrawCallCount

        lensView.draw(Canvas())

        assertEquals(before + 1, lensView.badgeDrawCallCount)
    }

    @Test
    fun cleanModeOn_badgeIsNeverDrawn() {
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        layoutBadgedApp()
        val before = lensView.badgeDrawCallCount

        lensView.draw(Canvas())

        assertEquals("clean mode must suppress the badge draw path", before, lensView.badgeDrawCallCount)
    }

    @Test
    fun cleanModeToggledAtRuntime_takesEffectOnNextFrame() {
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
        layoutBadgedApp()
        lensView.draw(Canvas())
        val afterVisibleFrame = lensView.badgeDrawCallCount

        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        lensView.draw(Canvas())

        assertEquals("no new badge draw after clean mode switched on", afterVisibleFrame, lensView.badgeDrawCallCount)
    }

    @Test
    fun talkBackAnnouncesBadgeOnlyWhenCleanModeOff() {
        layoutBadgedApp()
        val helper = requireNotNull(lensView.getAccessibilityHelper())
        val withBadge = describeFirstNode(helper)

        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
        val cleanLabel = describeFirstNode(helper)

        assertTrue("sanity: badge count is announced when clean mode is off", withBadge.contains(BADGE_COUNT.toString()))
        assertFalse("clean mode must not announce a badge TalkBack-side either", cleanLabel.contains(BADGE_COUNT.toString()))
    }

    private fun describeFirstNode(helper: LensAccessibilityHelper): String {
        var text = ""
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val node = androidx.core.view.accessibility.AccessibilityNodeInfoCompat.obtain()
            helper.testPopulateNodeForVirtualView(0, node)
            text = node.contentDescription?.toString().orEmpty()
        }
        return text
    }
}
