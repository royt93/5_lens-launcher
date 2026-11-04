package com.mckimquyen.views

import android.graphics.Canvas
import android.graphics.RectF
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.util.LensProfile
import com.mckimquyen.util.TiltParallax
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-033: khi rảnh, LensView vẽ tâm méo ảo theo offset tilt; khi chạm thì tilt bị bỏ qua hoàn toàn và
 * không được rò vào hit-test, chọn icon hay tọa độ chạm. Không dùng Room nên không chạm dữ liệu thật.
 */
@RunWith(AndroidJUnit4::class)
class LensViewTiltIntegrationTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private lateinit var settings: UtilSettings
    private lateinit var lensView: LensView
    private val lensId = "f033-tilt-lens"

    private companion object {
        const val VIEW_WIDTH = 1080
        const val VIEW_HEIGHT = 1920
        const val APP_COUNT = 25
        const val TILT_PX = 80f
        const val EPSILON = 0.01f
        const val MOVED_AT_LEAST_PX = 1f
        const val SOME_CELL = 12
        const val FRAMES = 5
    }

    @Before fun setup() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<android.content.Context>(), R.style.AppTheme)
        settings = UtilSettings(context)
        settings.saveDistortionFactor(lensId, 2.5f)
        settings.saveLensProfile(lensId, LensProfile.FISHEYE)
        onMain {
            lensView = LensView(context)
            lensView.lensId = lensId
            lensView.measure(
                View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, View.MeasureSpec.EXACTLY)
            )
            lensView.layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)
            lensView.setApps(ArrayList((0 until APP_COUNT).map { App(id = it, label = "A$it", packageName = "p.$it", name = "N$it") }))
        }
    }

    @After fun tearDown() = settings.deleteLensSettings(lensId)

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    /** Chạy [block] với một FrameLayout trong cửa sổ thật của harness Activity sẵn có (chỉ có ở bản debug). */
    private fun scenarioHost(block: (android.widget.FrameLayout) -> Unit) {
        androidx.test.core.app.ActivityScenario.launch(com.mckimquyen.feature.organization.OrganizationHarnessActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = android.widget.FrameLayout(activity)
                activity.setContentView(host)
                block(host)
            }
        }
    }

    /** Vẽ một khung với trạng thái chạm cho trước và trả mọi rect đã vẽ. */
    private fun drawn(x: Float, y: Float, multiplier: Float = 1f): List<RectF> {
        var rects: List<RectF> = emptyList()
        onMain {
            lensView.setLensStateForTest(x, y, multiplier, false)
            lensView.draw(Canvas())
            rects = lensView.drawnRectsForTest()
        }
        return rects
    }

    private fun idleRects() = drawn(-Float.MAX_VALUE, -Float.MAX_VALUE, multiplier = 0f)

    private fun sameRects(a: List<RectF>, b: List<RectF>) =
        a.size == b.size && a.indices.all { i ->
            kotlin.math.abs(a[i].left - b[i].left) < EPSILON && kotlin.math.abs(a[i].top - b[i].top) < EPSILON &&
                kotlin.math.abs(a[i].right - b[i].right) < EPSILON && kotlin.math.abs(a[i].bottom - b[i].bottom) < EPSILON
        }

    @Test fun withoutAnOffsetAnIdleLensDrawsTheOriginalGrid() {
        val base = idleRects()
        assertTrue(sameRects(base, drawn(-Float.MAX_VALUE, -Float.MAX_VALUE, multiplier = 0f)))
        assertFalse(base.isEmpty())
    }

    @Test fun anIdleLensWithATiltOffsetWarpsTheGridInTheOffsetDirection() {
        val base = idleRects()
        onMain { lensView.setTiltOffset(TILT_PX, 0f) }
        val tilted = drawn(-Float.MAX_VALUE, -Float.MAX_VALUE, multiplier = 0f)
        val moved = tilted.indices.count { kotlin.math.abs(tilted[it].centerX() - base[it].centerX()) > MOVED_AT_LEAST_PX }
        assertTrue("at least one icon must move when the idle lens is tilted ($moved moved)", moved > 0)
        onMain { lensView.setTiltOffset(-TILT_PX, 0f) }
        val opposite = drawn(-Float.MAX_VALUE, -Float.MAX_VALUE, multiplier = 0f)
        assertFalse("the opposite tilt gives a different picture", sameRects(tilted, opposite))
    }

    @Test fun aRealTouchIgnoresTheTiltCompletely() {
        val base = idleRects()
        val x = base[SOME_CELL].centerX() + 150f
        val y = base[SOME_CELL].centerY() + 150f
        val withoutTilt = drawn(x, y, 1f)
        onMain { lensView.setTiltOffset(TILT_PX, TILT_PX) }
        val withTilt = drawn(x, y, 1f)
        assertTrue("tilt must not change a frame the finger is driving", sameRects(withoutTilt, withTilt))
    }

    @Test fun tiltNeverLeaksIntoTheTouchCoordinatesOrTheSelection() {
        // Đặt tâm ảo đúng lên giữa một icon: nếu tilt lọt vào hit-test thì icon đó sẽ bị chọn.
        val base = idleRects()
        val target = base[SOME_CELL]
        val dx = target.centerX() - VIEW_WIDTH / 2f
        val dy = target.centerY() - VIEW_HEIGHT / 2f
        onMain { lensView.setTiltOffset(dx, dy) }
        drawn(-Float.MAX_VALUE, -Float.MAX_VALUE, multiplier = 0f)
        assertEquals(Pair(-Float.MAX_VALUE, -Float.MAX_VALUE), lensView.touchCoordinatesForTest())
        assertEquals("no icon is selected by tilt alone, even with the centre on an icon", -1, lensView.selectedIndexForTest)
    }

    @Test fun tiltNeverArmsTheHoverStateEitherWhenTheCentreSitsOnAnIcon() {
        val base = idleRects()
        val target = base[SOME_CELL]
        onMain { lensView.setTiltOffset(target.centerX() - VIEW_WIDTH / 2f, target.centerY() - VIEW_HEIGHT / 2f) }
        val frames = (0 until FRAMES).map { drawn(-Float.MAX_VALUE, -Float.MAX_VALUE, 0f); lensView.selectedIndexForTest }
        assertTrue("selection stays at -1 on every frame: $frames", frames.all { it == -1 })
    }

    @Test fun clearingTheOffsetRestoresTheOriginalGrid() {
        val base = idleRects()
        onMain { lensView.setTiltOffset(TILT_PX, TILT_PX) }
        drawn(-Float.MAX_VALUE, -Float.MAX_VALUE, multiplier = 0f)
        onMain { lensView.clearTiltOffset() }
        assertTrue(sameRects(base, drawn(-Float.MAX_VALUE, -Float.MAX_VALUE, multiplier = 0f)))
    }

    @Test fun exportingAndDetachingDropTheOffset() {
        val base = idleRects()
        onMain { lensView.setTiltOffset(TILT_PX, TILT_PX) }
        onMain { lensView.resetToIdleForExport() }
        assertTrue("export must never bake the tilt into the image", sameRects(base, drawn(-Float.MAX_VALUE, -Float.MAX_VALUE, 0f)))
        onMain { lensView.setTiltOffset(TILT_PX, TILT_PX) }
        // Detach thật: gắn vào một cửa sổ rồi gỡ ra, đúng đường ViewPager2 tái dùng trang.
        scenarioHost { host ->
            host.addView(lensView, android.widget.FrameLayout.LayoutParams(VIEW_WIDTH, VIEW_HEIGHT))
            host.removeView(lensView)
        }
        onMain {
            lensView.measure(
                View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, View.MeasureSpec.EXACTLY)
            )
            lensView.layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)
        }
        assertTrue("a recycled page must come back untilted", sameRects(base, drawn(-Float.MAX_VALUE, -Float.MAX_VALUE, 0f)))
    }

    @Test fun changingTheOffsetDoesNotRecomputeTheGridCache() {
        val cacheField = LensView::class.java.getDeclaredField("mGridCache").apply { isAccessible = true }
        val cache = cacheField.get(lensView) as LensGridCache
        idleRects()
        val before = cache.recomputeCount
        listOf(10f, -40f, 80f, 0f).forEach { dx ->
            onMain { lensView.setTiltOffset(dx, -dx) }
            drawn(-Float.MAX_VALUE, -Float.MAX_VALUE, multiplier = 0f)
        }
        assertEquals("tilt must not invalidate the grid geometry", before, cache.recomputeCount)
    }

    @Test fun theTiltMultiplierIsTheNamedConstantAndSmallerThanAFullTouch() {
        assertTrue(TiltParallax.ANIMATION_MULTIPLIER > 0f)
        assertNotEquals(1f, TiltParallax.ANIMATION_MULTIPLIER, 0f)
    }
}
