package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-026: an overscrolled drag past the left/top edge yields a NEGATIVE lens centre. The draw
 * path must still treat that as an active lens (not the "no touch" sentinel), or the grid snaps
 * back to rest while the finger is still down.
 */
@RunWith(AndroidJUnit4::class)
class LensViewElasticOverscrollDrawWidgetTest {

    private companion object {
        const val SIZE_PX = 800
        const val APP_COUNT = 25
        const val ICON_PX = 48
        const val FULL_ANIMATION = 1f
        const val OVERSCROLLED_PX = -12f
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val iconBitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
    private lateinit var lensView: LensView // assigned in setUp before every test

    @Before
    fun setUp() {
        val apps = ArrayList((0 until APP_COUNT).map { i ->
            App(id = i, packageName = "com.fish026.app$i", name = "App$i", label = "App $i", iconCacheKey = "fish026#$i")
        })
        apps.forEach { RAppsSingleton.instance.setAppIcon(it.iconCacheKey, iconBitmap) }
        instrumentation.runOnMainSync {
            lensView = LensView(context).apply {
                layout(0, 0, SIZE_PX, SIZE_PX)
                setApps(apps)
            }
        }
    }

    @After
    fun tearDown() {
        com.mckimquyen.util.BitmapCache.clear()
    }

    private fun render() {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        instrumentation.runOnMainSync { lensView.draw(Canvas(bitmap)) }
        bitmap.recycle()
    }

    @Test
    fun negativeOverscrolledCentre_isStillDrawnAsAnActiveLens() {
        lensView.depthOfFieldEnabled = true
        lensView.setLensStateForTest(OVERSCROLLED_PX, OVERSCROLLED_PX, FULL_ANIMATION, reduceMotion = false)
        render()
        assertEquals(
            "an overscrolled (negative) centre must keep the lens active",
            DepthOfField.Mode.ALPHA,
            lensView.dofLastMode
        )
    }

    @Test
    fun restSentinel_isStillInactive() {
        lensView.depthOfFieldEnabled = true
        lensView.setLensStateForTest(-Float.MAX_VALUE, -Float.MAX_VALUE, 0f, reduceMotion = false)
        render()
        assertEquals(DepthOfField.Mode.OFF, lensView.dofLastMode)
    }
}
