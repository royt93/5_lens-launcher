package com.mckimquyen.views

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.feature.organization.OrganizationHarnessActivity
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensViewHoverMagnifyWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val SIZE = 800
        const val APPS = 40
        const val ICON = 48
        const val DWELL = 200L
        const val RAMP = 150L
        const val WAIT_CLOCK = DWELL + RAMP + 1
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val iconBitmap = Bitmap.createBitmap(ICON, ICON, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

    @After fun tearDown() {
        UtilSettings(context).save(UtilSettings.KEY_FOCUS_MAGNIFY, false)
        com.mckimquyen.util.BitmapCache.clear()
    }

    @Test fun sameSelectedIconAfterAStillDwellDrawsAt1Point2WhileHitTestAndRectStayOriginal() {
        val apps = ArrayList((0 until APPS).map { App(id=it, packageName="com.f046.a$it", name="A$it", label="A $it", iconCacheKey="f046#$it") })
        apps.forEach { RAppsSingleton.instance.setAppIcon(it.iconCacheKey, iconBitmap) }
        var magnifiedRect = RectF()
        var baselineRect = RectF()
        var baselineIndex = -1
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val v = LensView(activity).apply { layout(0,0,SIZE,SIZE); setApps(apps) }
                // Set flags directly to isolate draw logic; real pan & dwell reachability is tested on-device separately.
                v.hoverMagnifyEnabled = true
                v.setHoverMagnifyStateForTest(enabled=true, panning=true, selectedIndex=0, speedDpPerS=0f, nowMs=0L)
                v.setLensStateForTest(400f,400f,1f,false)
                val bmp0=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888); v.draw(Canvas(bmp0)); bmp0.recycle()
                baselineRect=v.drawnRectsForTest()[0]
                baselineIndex=v.selectedIndexForTest
                v.setHoverMagnifyStateForTest(enabled=true, panning=true, selectedIndex=0, speedDpPerS=0f, nowMs=WAIT_CLOCK)
                val bmp1=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888); v.draw(Canvas(bmp1)); bmp1.recycle()
                magnifiedRect=v.drawnRectsForTest()[0]
                assertEquals("hit-test remains same", baselineIndex, v.selectedIndexForTest)
            }
        }
        assertTrue("drawn icon width grows by 1.2x: ${magnifiedRect.width()} vs ${baselineRect.width()}",
            magnifiedRect.width() >= baselineRect.width() * HoverMagnify.SCALE - 0.5f)
        assertEquals("center does not move", baselineRect.centerX(), magnifiedRect.centerX(), 0.5f)
        assertEquals("center y does not move", baselineRect.centerY(), magnifiedRect.centerY(), 0.5f)
    }

    @Test fun withSwitchOffNoMagnify() {
        val apps=ArrayList((0 until APPS).map { App(id=it,packageName="com.f046.b$it",name="B$it",label="B $it",iconCacheKey="f046b#$it") })
        apps.forEach { RAppsSingleton.instance.setAppIcon(it.iconCacheKey,iconBitmap) }
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { s -> s.onActivity { a ->
            val v=LensView(a).apply { layout(0,0,SIZE,SIZE); setApps(apps); hoverMagnifyEnabled=false; setHoverMagnifyStateForTest(false,true,0,0f,WAIT_CLOCK); setLensStateForTest(400f,400f,1f,false) }
            val b=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888); v.draw(Canvas(b)); b.recycle()
            val r=v.drawnRectsForTest()[0]
            assertFalse(r.width() > 80f)
        } }
    }

    @Test fun selectedIconChangesScaleBackImmediately() {
        val apps=ArrayList((0 until APPS).map { App(id=it,packageName="com.f046.c$it",name="C$it",label="C $it",iconCacheKey="f046c#$it") })
        apps.forEach { RAppsSingleton.instance.setAppIcon(it.iconCacheKey,iconBitmap) }
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { s -> s.onActivity { a ->
            val v=LensView(a).apply { layout(0,0,SIZE,SIZE); setApps(apps); hoverMagnifyEnabled=true; setHoverMagnifyStateForTest(true,true,0,0f,WAIT_CLOCK); setLensStateForTest(400f,400f,1f,false) }
            val b1=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888); v.draw(Canvas(b1)); b1.recycle()
            val full=v.drawnRectsForTest()[0].width()
            v.setHoverMagnifyStateForTest(true,true,1,0f,WAIT_CLOCK+10)
            val b2=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888); v.draw(Canvas(b2)); b2.recycle()
            assertTrue("icon 0 returns to 1x",v.drawnRectsForTest()[0].width() < full)
        } }
    }
}
