package com.mckimquyen.views

import android.content.ClipData
import android.content.Context
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-038b on a real device: hold then release opens the menu, hold then move starts a drag. */
@RunWith(AndroidJUnit4::class)
class LensViewDragGestureWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var context: Context
    private lateinit var lensView: LensView
    private val menuRequests = ArrayList<Int>()
    private val dragRequests = ArrayList<LensDragPayload>()
    private var dragAccepted = true

    @Before
    fun setup() {
        context = ContextThemeWrapper(instrumentation.targetContext, R.style.AppTheme)
        instrumentation.runOnMainSync {
            lensView = LensView(context)
            lensView.appOptionsOpener = { index -> menuRequests.add(index); true }
            lensView.dragStarter = { _: LensView, _: ClipData, _: View.DragShadowBuilder, payload: Any ->
                dragRequests.add(payload as LensDragPayload)
                dragAccepted
            }
        }
    }

    private fun slop(): Float {
        val field = LensView::class.java.getDeclaredField("mTouchSlop")
        field.isAccessible = true
        return field.getFloat(lensView)
    }

    private fun holdTriggered(): Boolean {
        val field = LensView::class.java.getDeclaredField("mLongPressTriggered")
        field.isAccessible = true
        return field.getBoolean(lensView)
    }

    private fun touch(action: Int, x: Float, y: Float) {
        val now = System.currentTimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        lensView.dispatchTouchEvent(event)
        event.recycle()
    }

    /** Lays out a one-icon grid, presses its centre and draws once so the press selects icon 0. */
    private fun pressIcon(hasOtherLenses: Boolean): Pair<Float, Float> {
        var point = 0f to 0f
        instrumentation.runOnMainSync {
            lensView.setHasOtherLenses(hasOtherLenses)
            lensView.setApps(arrayListOf(App(id = 1, label = "Drag App", packageName = "com.test.drag", name = "Main")))
            lensView.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
            )
            lensView.layout(0, 0, 1080, 1920)
            lensView.draw(Canvas())
            val bounds = android.graphics.Rect()
            assertTrue(lensView.getAppBounds(0, bounds))
            point = bounds.exactCenterX() to bounds.exactCenterY()
            touch(MotionEvent.ACTION_DOWN, point.first, point.second)
            lensView.draw(Canvas())
        }
        return point
    }

    private fun waitPastLongPress() {
        Thread.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 250)
        instrumentation.waitForIdleSync()
    }

    @Test
    fun multiLens_holdThenRelease_opensMenuOnlyOnRelease() {
        val point = pressIcon(hasOtherLenses = true)
        waitPastLongPress()
        instrumentation.runOnMainSync {
            assertTrue("the hold must be recognised", holdTriggered())
            assertTrue("a multi-lens hold must not open the menu at the timeout", menuRequests.isEmpty())
            touch(MotionEvent.ACTION_UP, point.first, point.second)
        }
        assertEquals("releasing without moving opens the menu for the held icon", listOf(0), menuRequests)
        assertTrue("no drag was requested", dragRequests.isEmpty())
    }

    @Test
    fun multiLens_holdThenMove_requestsDragAndNeverOpensMenu() {
        val point = pressIcon(hasOtherLenses = true)
        waitPastLongPress()
        instrumentation.runOnMainSync {
            touch(MotionEvent.ACTION_MOVE, point.first + slop() + 40f, point.second)
        }
        assertEquals("moving past slop after the hold requests exactly one drag", 1, dragRequests.size)
        val payload = dragRequests.single()
        assertEquals("com.test.drag", payload.app.packageName.toString())
        assertNotNull(payload.sourceLensId)
        instrumentation.runOnMainSync { touch(MotionEvent.ACTION_CANCEL, point.first, point.second) }
        assertTrue("a drag must never also open the app menu", menuRequests.isEmpty())
    }

    @Test
    fun multiLens_holdWithinSlop_doesNotStartADrag() {
        val point = pressIcon(hasOtherLenses = true)
        waitPastLongPress()
        instrumentation.runOnMainSync {
            touch(MotionEvent.ACTION_MOVE, point.first + slop() / 2f, point.second)
            touch(MotionEvent.ACTION_UP, point.first + slop() / 2f, point.second)
        }
        assertTrue("jitter under touch slop must not start a drag", dragRequests.isEmpty())
        assertEquals("a held icon released after jitter still opens the menu", listOf(0), menuRequests)
    }

    @Test
    fun multiLens_dragThenUp_doesNotOpenMenu() {
        val point = pressIcon(hasOtherLenses = true)
        waitPastLongPress()
        instrumentation.runOnMainSync {
            touch(MotionEvent.ACTION_MOVE, point.first + slop() + 40f, point.second)
            touch(MotionEvent.ACTION_UP, point.first + slop() + 40f, point.second)
        }
        assertEquals(1, dragRequests.size)
        assertTrue("finishing a drag with ACTION_UP must not open the app menu", menuRequests.isEmpty())
    }

    @Test
    fun multiLens_moveBeforeTimeout_isAPanNotADrag() {
        val point = pressIcon(hasOtherLenses = true)
        instrumentation.runOnMainSync {
            touch(MotionEvent.ACTION_MOVE, point.first + 400f, point.second + 400f)
        }
        waitPastLongPress()
        instrumentation.runOnMainSync { touch(MotionEvent.ACTION_UP, point.first + 400f, point.second + 400f) }
        assertTrue("a pan must not start a drag", dragRequests.isEmpty())
        assertTrue("a pan must not open the menu", menuRequests.isEmpty())
    }

    @Test
    fun singleLens_holdOpensMenuAtTimeout_andNeverDrags() {
        val point = pressIcon(hasOtherLenses = false)
        waitPastLongPress()
        instrumentation.runOnMainSync {
            assertEquals("a single-lens hold opens the menu at the timeout, as before", listOf(0), menuRequests)
            touch(MotionEvent.ACTION_MOVE, point.first + slop() + 40f, point.second)
            touch(MotionEvent.ACTION_UP, point.first, point.second)
        }
        assertTrue("with one lens there is nowhere to drop, so no drag", dragRequests.isEmpty())
        assertEquals("release must not open a second menu", 1, menuRequests.size)
    }

    @Test
    fun refusedDrag_fallsBackToTheMenu() {
        dragAccepted = false
        val point = pressIcon(hasOtherLenses = true)
        waitPastLongPress()
        instrumentation.runOnMainSync {
            touch(MotionEvent.ACTION_MOVE, point.first + slop() + 40f, point.second)
        }
        assertEquals(1, dragRequests.size)
        assertEquals("a refused drag must not leave the hold doing nothing", listOf(0), menuRequests)
        instrumentation.runOnMainSync { touch(MotionEvent.ACTION_UP, point.first, point.second) }
        assertFalse("release after the fallback must not open the menu twice", menuRequests.size > 1)
    }
}
