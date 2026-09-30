package com.mckimquyen.views

import android.content.Context
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-024: state-based verification only (no real pixel assertion), mirroring
 * LensGridCacheTest's "production counter field doubling as a test hook" convention - not
 * LensViewDepthOfFieldWidgetTest's pixel-sampling convention, which this feature doesn't need.
 */
@RunWith(AndroidJUnit4::class)
class LensViewNotificationBadgeWidgetTest {

    private lateinit var context: Context
    private lateinit var lensView: LensView

    @Before
    fun setup() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        context = ContextThemeWrapper(targetContext, R.style.AppTheme)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lensView = LensView(context)
        }
    }

    private fun layoutSingleApp(notificationCount: Int) {
        UtilSettings(context).save(UtilSettings.KEY_SHOW_NOTIFICATION_BADGES, true)
        lensView.setApps(arrayListOf(App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity", notificationCount = notificationCount)))
        lensView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
        )
        lensView.layout(0, 0, 1080, 1920)
    }

    @Test
    fun drawingAnAppWithAPositiveCountInvokesTheBadgeDrawPath() {
        layoutSingleApp(3)
        val before = lensView.badgeDrawCallCount

        lensView.draw(Canvas())

        assertEquals(before + 1, lensView.badgeDrawCallCount)
    }

    @Test
    fun drawingAnAppWithZeroCountNeverInvokesTheBadgeDrawPath() {
        layoutSingleApp(0)
        val before = lensView.badgeDrawCallCount

        lensView.draw(Canvas())

        assertEquals(before, lensView.badgeDrawCallCount)
    }
}
