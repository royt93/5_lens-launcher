package com.mckimquyen.views

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.ui.ActHome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-008 Phase 2, the flagged highest-risk part of the epic: LensView's own pan/pinch gesture
 * machine and the new ViewPager2 ancestor both want every ACTION_MOVE. These tests drive real
 * MotionEvent streams through the real attached view tree on a real device - the one thing the
 * pure `isHorizontalSwipeIntent` unit tests cannot prove - and assert who actually won.
 */
@RunWith(AndroidJUnit4::class)
class LensViewPagerGestureIntegrationTest {

    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()

    @Before
    fun setUp(): Unit = runBlocking {
        AppDatabase.init(InstrumentationRegistry.getInstrumentation().targetContext)
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
        dao.insertOrUpdate(LensWorkspace(id = "second", name = "Second Lens", orderIndex = 1))
        Unit
    }

    @After
    fun tearDown(): Unit = runBlocking {
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        Unit
    }

    /**
     * Drives a drag as a real DOWN / MOVE... / UP stream on the UI thread and returns the LensView's
     * gesture state at the end of the MOVE phase (before UP resets it).
     */
    private fun drag(
        scenario: ActivityScenario<ActHome>,
        totalDx: Float,
        totalDy: Float,
        steps: Int = 10
    ): Pair<LensGestureState, Boolean> {
        var stateAtEnd = LensGestureState.IDLE
        var pagerIsDragging = false

        scenario.onActivity { activity ->
            val pager = activity.findViewById<ViewPager2>(R.id.lensPager)
            val lens = pager.findViewById<LensView>(R.id.lensViews) ?: return@onActivity
            val startX = lens.width / 2f
            val startY = lens.height / 2f
            val down = SystemClock.uptimeMillis()

            fun send(action: Int, x: Float, y: Float, time: Long) {
                val ev = MotionEvent.obtain(down, time, action, x, y, 0)
                // Dispatch from the pager so the ancestor gets its real interception chance.
                pager.dispatchTouchEvent(ev)
                ev.recycle()
            }

            send(MotionEvent.ACTION_DOWN, startX, startY, down)
            for (i in 1..steps) {
                val t = down + i * 16L
                send(MotionEvent.ACTION_MOVE, startX + totalDx * i / steps, startY + totalDy * i / steps, t)
            }
            stateAtEnd = lens.gestureState
            pagerIsDragging = pager.scrollState != ViewPager2.SCROLL_STATE_IDLE
            send(MotionEvent.ACTION_UP, startX + totalDx, startY + totalDy, down + (steps + 1) * 16L)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        return stateAtEnd to pagerIsDragging
    }

    @Test
    fun flatHorizontalDrag_isHandedToThePagerNotTheLens() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            val (lensState, pagerDragging) = drag(scenario, totalDx = 400f, totalDy = 8f)

            assertEquals(
                "A flat horizontal drag must not put the lens into PANNING",
                LensGestureState.IDLE,
                lensState
            )
            assertTrue("The pager must have taken the horizontal drag", pagerDragging)
        }
    }

    @Test
    fun verticalDrag_staysWithTheLens() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            val (lensState, pagerDragging) = drag(scenario, totalDx = 6f, totalDy = 400f)

            assertEquals("A vertical drag must pan the lens", LensGestureState.PANNING, lensState)
            assertTrue("The pager must not scroll on a vertical drag", !pagerDragging)
        }
    }

    @Test
    fun diagonalDrag_staysWithTheLens() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            // 300:200 is a 1.5:1 ratio - below the 2:1 dominance threshold, so the lens keeps it.
            val (lensState, pagerDragging) = drag(scenario, totalDx = 300f, totalDy = 200f)

            assertEquals("A diagonal drag must pan the lens", LensGestureState.PANNING, lensState)
            assertTrue("The pager must not steal a diagonal drag", !pagerDragging)
        }
    }
}
