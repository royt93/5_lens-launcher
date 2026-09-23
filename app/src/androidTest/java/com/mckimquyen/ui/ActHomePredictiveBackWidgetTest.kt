package com.mckimquyen.ui

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.search.SearchView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-021: widget/instrumentation proof for the predictive-back preview wired onto
 * ActHome.searchBackCallback. `handleOnBackStarted`/`Progressed`/`Cancelled` are package-private
 * overrides reached via reflection on the named field - the same pattern
 * BaseActivityRefreshRateWidgetTest already uses for a protected method - since real predictive-
 * back gesture dispatch itself needs a real device swipe (covered by this story's Smoke section),
 * not something this layer fakes.
 */
@RunWith(AndroidJUnit4::class)
class ActHomePredictiveBackWidgetTest {

    private fun callback(activity: ActHome): OnBackPressedCallback {
        val field = ActHome::class.java.getDeclaredField("searchBackCallback")
        field.isAccessible = true
        return field.get(activity) as OnBackPressedCallback
    }

    private fun searchView(activity: ActHome): SearchView {
        val field = ActHome::class.java.getDeclaredField("searchView")
        field.isAccessible = true
        return field.get(activity) as SearchView
    }

    private fun backEvent(progress: Float) = BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    @Test
    fun progress_whenSearchNotShowing_leavesTransformUntouched() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { activity ->
            val sv = searchView(activity)
            assertTrue("precondition: search must start closed", !sv.isShowing)
            callback(activity).handleOnBackProgressed(backEvent(0.5f))
            assertEquals(1.0f, sv.scaleX)
            assertEquals(1.0f, sv.scaleY)
            assertEquals(1.0f, sv.alpha)
        }
        scenario.close()
    }

    @Test
    fun progress_whenSearchShowing_scalesAndFadesTowardMinimumAtFullProgress() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { activity ->
            val sv = searchView(activity)
            sv.show()
        }
        scenario.onActivity { activity ->
            val sv = searchView(activity)
            callback(activity).handleOnBackProgressed(backEvent(1.0f))
            assertEquals(0.95f, sv.scaleX, 0.001f)
            assertEquals(0.95f, sv.scaleY, 0.001f)
            assertEquals(0.7f, sv.alpha, 0.001f)
        }
        scenario.close()
    }

    @Test
    fun cancelled_resetsTransformToIdentity() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { activity ->
            val sv = searchView(activity)
            sv.show()
        }
        scenario.onActivity { activity ->
            callback(activity).handleOnBackProgressed(backEvent(0.8f))
            callback(activity).handleOnBackCancelled()
            val sv = searchView(activity)
            assertEquals(1.0f, sv.scaleX)
            assertEquals(1.0f, sv.scaleY)
            assertEquals(1.0f, sv.alpha)
        }
        scenario.close()
    }

    /** UI-010 regression guard: completed back press must still close the search overlay. */
    @Test
    fun pressed_whenSearchShowing_stillClosesSearch() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { activity ->
            searchView(activity).show()
        }
        scenario.onActivity { activity ->
            callback(activity).handleOnBackProgressed(backEvent(0.5f))
            callback(activity).handleOnBackPressed()
        }
        scenario.onActivity { activity ->
            val sv = searchView(activity)
            assertTrue("back press must close the search overlay (UI-010)", !sv.isShowing)
            assertEquals("transform must be reset, not left mid-gesture", 1.0f, sv.scaleX)
        }
        scenario.close()
    }

    /** UI-010 regression guard: back press must never finish the HOME activity. */
    @Test
    fun pressed_whenSearchNotShowing_doesNotFinishActivity() {
        val scenario = ActivityScenario.launch(ActHome::class.java)
        scenario.onActivity { activity ->
            assertTrue(!searchView(activity).isShowing)
            callback(activity).handleOnBackPressed()
            assertTrue("HOME activity must never finish on back", !activity.isFinishing)
        }
        scenario.close()
    }
}
