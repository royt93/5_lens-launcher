package com.mckimquyen.views

import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActHome
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A ViewPager2 page is a RecyclerView item: when the user swipes away it is detached, and when the
 * user swipes back the SAME view instance is attached again. `onDetachedFromWindow` frees the
 * view's references, so unless the view restores them on attach, `onDraw` finds its settings null
 * and draws nothing: the page comes back blank (found on TECNO KJ7 and Samsung S24 Ultra with a
 * second lens, present before FISH-018).
 *
 * The view has to live in a REAL window (an Activity's content), otherwise `onDetachedFromWindow`
 * never runs and the test would pass against the buggy code.
 */
@RunWith(AndroidJUnit4::class)
class LensViewReattachIntegrationTest {

    private companion object {
        const val VIEW_WIDTH = 1080
        const val VIEW_HEIGHT = 1920
        const val APP_COUNT = 12
        const val FIRST_INDEX = 0
        const val SMALL_DP = 16f
        const val LARGE_DP = 50f
        const val REATTACH_COUNT = 3
    }

    private fun apps() = ArrayList(
        (0 until APP_COUNT).map { App(id = it, label = "App $it", packageName = "com.t.a$it", name = "Act$it") }
    )

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    /** Runs [block] on the main thread with a fresh container attached to the real window. */
    private fun withAttachedContainer(block: (host: FrameLayout, newView: () -> LensView) -> Unit) {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = FrameLayout(activity)
                activity.addContentView(host, ViewGroup.LayoutParams(VIEW_WIDTH, VIEW_HEIGHT))
                block(host) { LensView(activity) }
            }
        }
    }

    private fun attach(host: FrameLayout, view: LensView) {
        host.addView(view, FrameLayout.LayoutParams(VIEW_WIDTH, VIEW_HEIGHT))
    }

    private fun draw(view: LensView) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT)
        view.draw(Canvas())
    }

    private fun drawWithApps(view: LensView) {
        view.setApps(apps())
        draw(view)
    }

    /** What a pager does when the user swipes away and back: detach, then attach the same view. */
    private fun detachAndReattach(host: FrameLayout, view: LensView) {
        host.removeView(view)
        attach(host, view)
    }

    /**
     * True only if the grid geometry exists after the last redraw. `onDetachedFromWindow` clears
     * the grid cache, and only a real `drawGrid` (which needs the view's settings) fills it again,
     * so this tells a drawn page from a blank one.
     */
    private fun hasDrawnGrid(view: LensView): Boolean = view.getAppBounds(FIRST_INDEX, Rect())

    private fun firstCellWidth(view: LensView): Int {
        val bounds = Rect()
        assertTrue("grid must have been drawn", view.getAppBounds(FIRST_INDEX, bounds))
        return bounds.width()
    }

    @Test
    fun aViewThatWasDetachedAndAttachedAgain_stillDrawsItsGrid() {
        withAttachedContainer { host, newView ->
            val view = newView()
            attach(host, view)
            drawWithApps(view)
            assertTrue("sanity: the first attach draws a grid", hasDrawnGrid(view))

            detachAndReattach(host, view)
            drawWithApps(view)

            assertTrue("a re-attached page must draw its grid, not come back blank", hasDrawnGrid(view))
        }
    }

    @Test
    fun aReattachedView_drawsWithoutBeingHandedItsAppsAgain() {
        withAttachedContainer { host, newView ->
            val view = newView()
            attach(host, view)
            drawWithApps(view)

            detachAndReattach(host, view)
            // No setApps here: a pager only re-hands the list on onPageSelected, and a page can be
            // recycled (lens added/renamed/deleted) without a selection event.
            draw(view)

            assertTrue("a re-attached page must keep its apps, not wait for setApps", hasDrawnGrid(view))
        }
    }

    @Test
    fun aViewDetachedAndAttachedSeveralTimes_keepsDrawing() {
        withAttachedContainer { host, newView ->
            val view = newView()
            attach(host, view)
            drawWithApps(view)
            repeat(REATTACH_COUNT) {
                detachAndReattach(host, view)
                drawWithApps(view)
                assertTrue("blank after re-attach number ${it + 1}", hasDrawnGrid(view))
            }
        }
    }

    @Test
    fun aReattachedView_keepsItsAccessibilityNodes() {
        withAttachedContainer { host, newView ->
            val view = newView()
            attach(host, view)
            drawWithApps(view)
            assertNotNull("sanity: accessibility helper exists after the first attach", view.getAccessibilityHelper())

            detachAndReattach(host, view)
            drawWithApps(view)

            assertNotNull("TalkBack navigation must survive a swipe away and back", view.getAccessibilityHelper())
        }
    }

    @Test
    fun aReattachedView_canStillLaunchApps() {
        withAttachedContainer { host, newView ->
            val view = newView()
            view.setPackageManager(host.context.packageManager)
            attach(host, view)
            drawWithApps(view)

            detachAndReattach(host, view)

            // launchApp / launchAppAtIndex are guarded by this field; ActHome sets it only once.
            val field = LensView::class.java.getDeclaredField("mPackageManager").apply { isAccessible = true }
            assertNotNull("a tap on an icon of a re-attached page must still launch the app", field.get(view))
        }
    }

    @Test
    fun aViewThatIsNeverDetached_isUnaffected() {
        withAttachedContainer { host, newView ->
            val view = newView()
            attach(host, view)
            drawWithApps(view)
            drawWithApps(view)
            assertTrue(hasDrawnGrid(view))
        }
    }

    @Test
    fun aReattachedView_drawsTheCurrentIconSizeOfItsLens() {
        withAttachedContainer { host, newView ->
            val settings = UtilSettings(host.context)
            settings.save(UtilSettings.KEY_ICON_SIZE, SMALL_DP)
            val view = newView()
            attach(host, view)
            drawWithApps(view)
            val before = firstCellWidth(view)

            detachAndReattach(host, view)
            settings.save(UtilSettings.KEY_ICON_SIZE, LARGE_DP)
            drawWithApps(view)

            val after = firstCellWidth(view)
            assertTrue("a re-attached page must read settings again ($before -> $after)", after > before)
        }
    }

    @Test
    fun aDetachedView_resetsTouchAndSelectionState() {
        withAttachedContainer { host, newView ->
            val view = newView()
            attach(host, view)
            drawWithApps(view)

            val selectIndexField = LensView::class.java.getDeclaredField("mSelectIndex").apply { isAccessible = true }
            selectIndexField.setInt(view, 2)
            view.gestureState = LensGestureState.PANNING

            detachAndReattach(host, view)

            assertEquals("a re-attached page must not keep a stale selected cell", -1, view.selectedIndexForTest)
            assertEquals("a re-attached page must not resume mid-gesture", LensGestureState.IDLE, view.gestureState)
        }
    }

    @Test
    fun aDetachedView_clearsAccessibilityDelegate() {
        withAttachedContainer { host, newView ->
            val view = newView()
            attach(host, view)
            drawWithApps(view)
            assertTrue("sanity: delegate installed while attached", ViewCompat.hasAccessibilityDelegate(view))

            host.removeView(view)

            assertFalse(
                "the delegate this view installed on itself must not outlive it",
                ViewCompat.hasAccessibilityDelegate(view)
            )
        }
    }
}
