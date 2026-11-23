package com.mckimquyen.ui

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.mckimquyen.R
import com.mckimquyen.adt.LensPagerAdapter
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.views.LensView
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * With three or more lenses a swiped-away page can come back from the RecyclerView cache without being bound again, and LensView.onDetachedFromWindow
 * has nulled its owner callbacks. A review claimed the page then loses pull-down search, edge swipe and the lens menu. Log tag F700PAGE.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeLensPageRecycleListenersTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val TAG = "F700PAGE"
        const val MANY_LENSES = 5
        const val THREE_LENSES = 3
        const val SETTLE_MS = 600L
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before fun setup() { cleanDb() }
    @After fun tearDown() {
        cleanDb()
        com.mckimquyen.util.UtilSettings(instrumentation.targetContext).save(com.mckimquyen.util.UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
    }

    private fun cleanDb(): Unit = runBlocking {
        AppDatabase.init(instrumentation.targetContext)
        val dao = AppDatabase.getInstance().lensWorkspaceDao()
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
    }

    private fun settle() { instrumentation.waitForIdleSync(); Thread.sleep(SETTLE_MS); instrumentation.waitForIdleSync() }

    private fun lensViewAt(pager: ViewPager2, position: Int): LensView? {
        val rv = pager.getChildAt(0) as RecyclerView
        return (rv.findViewHolderForAdapterPosition(position) as? LensPagerAdapter.PageHolder)?.lensView
    }

    private fun report(label: String, v: LensView?): String {
        val s = if (v == null) "no view" else "search=${v.onSearchSwipeDownListener != null} edge=${v.onEdgeSwipeListener != null} longPress=${v.onEmptySpaceLongPressListener != null} drag=${v.onAppDragStartListener != null} curvature=${v.onCurvatureAdjustedListener != null}"
        android.util.Log.i(TAG, "$label: $s")
        return s
    }

    @Test fun threeLensesSwipedForwardAndBackKeepTheOwnerCallbacks() = swipeOutAndBack(THREE_LENSES)

    @Test fun manyLensesSwipedForwardAndBackKeepTheOwnerCallbacks() = swipeOutAndBack(MANY_LENSES)

    private fun swipeOutAndBack(LENS_COUNT: Int) {
        cleanDb()
        runBlocking {
            val dao = AppDatabase.getInstance().lensWorkspaceDao()
            for (i in 1 until LENS_COUNT) dao.insertOrUpdate(LensWorkspace(id = "l$i", name = "Lens $i", orderIndex = i))
        }
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            settle()
            var pager: ViewPager2? = null
            scenario.onActivity { pager = it.findViewById(R.id.lensPager) }
            val p = requireNotNull(pager)
            var first: LensView? = null
            instrumentation.runOnMainSync { first = lensViewAt(p, 0) }
            val firstView = requireNotNull(first) { "positive control: page 0 has a LensView" }
            instrumentation.runOnMainSync { report("lenses=$LENS_COUNT page0-initial", firstView) }
            assertNotNull("positive control: the owner bound the search callback on first bind", firstView.onSearchSwipeDownListener)
            assertEquals("positive control: all pages exist", LENS_COUNT, p.adapter?.itemCount)

            // swipe forward one page at a time with ViewPager2's own fake drag (the same path a finger takes), past the offscreen window and the
            // RecyclerView cache, then back. Record whether page 0's view was ever detached.
            var everDetached = false
            val watcher = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) { everDetached = true }
            }
            instrumentation.runOnMainSync { firstView.addOnAttachStateChangeListener(watcher) }
            fun fakeSwipe(forward: Boolean) {
                instrumentation.runOnMainSync {
                    val w = p.width.toFloat()
                    p.beginFakeDrag()
                    p.fakeDragBy(if (forward) -w else w)
                    p.endFakeDrag()
                }
                settle()
            }
            repeat(LENS_COUNT - 1) { fakeSwipe(forward = true) }
            var detachedSeen = false
            instrumentation.runOnMainSync { detachedSeen = everDetached }
            android.util.Log.i(TAG, "lenses=$LENS_COUNT: page 0 ever detached while swiping to the last page: $detachedSeen (current item ${p.currentItem})")
            repeat(LENS_COUNT - 1) { fakeSwipe(forward = false) }

            var back: LensView? = null
            var desc = ""
            instrumentation.runOnMainSync { back = lensViewAt(p, 0); desc = report("lenses=$LENS_COUNT page0-after-return", back) }
            val backView = requireNotNull(back)
            android.util.Log.i(TAG, "same view instance as before: ${backView === firstView}; attached=${backView.isAttachedToWindow} visibility=${backView.visibility == View.VISIBLE}")
            assertTrue("positive control: page 0 really was detached on the way (else this proves nothing)", detachedSeen)
            assertNotNull("pull-down search callback must be present on a page that came back: $desc", backView.onSearchSwipeDownListener)
            assertNotNull("edge-swipe callback must be present on a page that came back: $desc", backView.onEdgeSwipeListener)
            assertNotNull("empty-space long-press callback must be present on a page that came back: $desc", backView.onEmptySpaceLongPressListener)
        }
    }
}
