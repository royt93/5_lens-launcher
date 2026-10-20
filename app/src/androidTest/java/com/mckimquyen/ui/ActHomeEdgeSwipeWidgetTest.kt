package com.mckimquyen.ui

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.EdgeSwipeMode
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import com.mckimquyen.views.LensView
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActHomeEdgeSwipeWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()


    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val settings get() = UtilSettings(context)
    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private var originalLenses: List<LensWorkspace> = emptyList()
    private var originalEdgeSwipeMode: String? = null
    private var hadStoredEdgeSwipeMode = false
    private var originalActiveLensId: String? = null

    @Before
    fun setup(): Unit = runBlocking {
        AppDatabase.init(context)
        originalLenses = dao.getAll()
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        hadStoredEdgeSwipeMode = prefs.contains(UtilSettings.KEY_EDGE_SWIPE_LENS_SWITCH)
        originalEdgeSwipeMode = prefs.getString(UtilSettings.KEY_EDGE_SWIPE_LENS_SWITCH, null)
        originalActiveLensId = prefs.getString(UtilSettings.KEY_ACTIVE_LENS_ID, null)

        if (originalLenses.none { it.id == LensWorkspace.DEFAULT_LENS_ID }) {
            dao.insertIfAbsent(LensWorkspace.createDefault())
            originalLenses = dao.getAll()
        }
        if (originalLenses.size < 2) {
            dao.insertIfAbsent(LensWorkspace(id = "lens_second", name = "Second", orderIndex = 1))
        }
        settings.saveEdgeSwipeMode(EdgeSwipeMode.BOTH)
        Unit
    }

    @After
    fun tearDown(): Unit = runBlocking {
        dao.deleteAll()
        dao.insertAll(originalLenses)
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().apply {
            if (hadStoredEdgeSwipeMode) {
                putString(UtilSettings.KEY_EDGE_SWIPE_LENS_SWITCH, originalEdgeSwipeMode)
            } else {
                remove(UtilSettings.KEY_EDGE_SWIPE_LENS_SWITCH)
            }
            if (originalActiveLensId != null) {
                putString(UtilSettings.KEY_ACTIVE_LENS_ID, originalActiveLensId)
            } else {
                remove(UtilSettings.KEY_ACTIVE_LENS_ID)
            }
        }.commit()
        Unit
    }

    private fun injectSwipe(view: LensView, startX: Float, startY: Float, endX: Float, endY: Float) {
        instrumentation.runOnMainSync {
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, startX, startY, 0)
            view.dispatchTouchEvent(down)
            down.recycle()

            val move = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_MOVE, endX, endY, 0)
            view.dispatchTouchEvent(move)
            move.recycle()

            val up = MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_UP, endX, endY, 0)
            view.dispatchTouchEvent(up)
            up.recycle()
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
    }

    @Test
    fun rightEdgeSwipeLeft_switchesToNextLens() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            var pager: ViewPager2? = null
            var lensView: LensView? = null

            scenario.onActivity { activity ->
                pager = activity.findViewById(R.id.lensPager)
                lensView = activity.findViewById(R.id.lensViews)
            }
            instrumentation.waitForIdleSync()

            assertEquals(0, pager?.currentItem)
            val view = requireNotNull(lensView)

            val width = view.width.toFloat()
            val height = view.height.toFloat()

            // Right edge to left swipe: from width - 10px to width - 200px
            injectSwipe(view, width - 10f, height * 0.5f, width - 200f, height * 0.5f)

            scenario.onActivity {
                assertEquals("Vuốt mép phải sang trái phải chuyển sang Lens tiếp theo (trang 1)", 1, pager?.currentItem)
            }

            // Reacquire the LensView bound to the selected second page; ViewPager2 pages own
            // distinct LensView instances, so the first page's old view is no longer active.
            var secondPageView: LensView? = null
            scenario.onActivity { activity ->
                secondPageView = activity.findViewById<ViewPager2>(R.id.lensPager)
                    .findViewById<LensView>(R.id.lensViews)
            }
            val activeView = requireNotNull(secondPageView)
            val activeWidth = activeView.width.toFloat()
            val activeHeight = activeView.height.toFloat()

            // Left edge to right swipe: from 10px to 200px
            injectSwipe(activeView, 10f, activeHeight * 0.5f, 200f, activeHeight * 0.5f)

            scenario.onActivity {
                assertEquals("Vuốt mép trái sang phải phải chuyển về Lens liền trước (trang 0)", 0, pager?.currentItem)
            }
        }
    }
}
