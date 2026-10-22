package com.mckimquyen.views

import android.graphics.Canvas
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensViewStaleSelectionIntegrationTest {
    @get:Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    @Test
    fun drawingLabelWithStaleSelectedIndexDoesNotCrash() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val view = LensView(context)
            val apps = arrayListOf(
                App(label = "First", packageName = "com.example.first", name = "FirstActivity"),
                App(label = "Selected", packageName = "com.example.selected", name = "SelectedActivity")
            )
            view.setApps(apps)
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(VIEW_SIZE, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(VIEW_SIZE, android.view.View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, VIEW_SIZE, VIEW_SIZE)
            view.draw(Canvas())
            val staleIndex = apps.lastIndex
            view.setApps(arrayListOf(apps.first()))
            LensView::class.java.getDeclaredField("mSelectIndex").apply { isAccessible = true }.setInt(view, staleIndex)
            LensView::class.java.getDeclaredField("mMoving").apply { isAccessible = true }.setBoolean(view, true)
            UtilSettings(context).save(UtilSettings.KEY_SHOW_NAME_APP_HOVER, true)
            val drawAppName = LensView::class.java.getDeclaredMethod("drawAppName", Canvas::class.java, RectF::class.java)
                .apply { isAccessible = true }

            assertTrue("fixture index must be stale after shrink", staleIndex >= view.displayedApps.size)
            try {
                drawAppName.invoke(view, Canvas(), RectF(0f, 0f, VIEW_SIZE.toFloat(), VIEW_SIZE.toFloat()))
            } catch (failure: java.lang.reflect.InvocationTargetException) {
                throw AssertionError("drawing a stale selected app index must not crash", failure.cause)
            }
        }
    }

    @Test
    fun launchingWithStaleSelectedIndexDoesNotCrash() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val view = LensView(context)
            view.setApps(arrayListOf(App(label = "Only", packageName = "com.example.only", name = "OnlyActivity")))
            view.setPackageManager(context.packageManager)
            view.setApps(arrayListOf())
            LensView::class.java.getDeclaredField("mSelectIndex").apply { isAccessible = true }.setInt(view, 0)
            val launch = LensView::class.java.getDeclaredMethod("launchApp").apply { isAccessible = true }

            try {
                launch.invoke(view)
            } catch (failure: java.lang.reflect.InvocationTargetException) {
                throw AssertionError("launching with stale selected index must not crash", failure.cause)
            }
        }
    }

    private companion object {
        const val VIEW_SIZE = 1_000
    }
}
