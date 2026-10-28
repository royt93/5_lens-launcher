package com.mckimquyen.ui.home

import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.ui.ActHome
import com.mckimquyen.views.LensDragPayload
import com.mckimquyen.views.LensView
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-038b: the temporary drop strip, driven with real views on a real Activity. */
@RunWith(AndroidJUnit4::class)
class LensDropStripWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app = App(label = "Drop App", packageName = "com.test.dropstrip", name = "Main")

    private data class Drop(val source: String, val target: String, val appId: String)
    private inner class Fixture(val activity: ActHome, var lenses: List<LensWorkspace>) {
        // Same parent type as production (act_home.xml's rootLayout is a CoordinatorLayout).
        val root = CoordinatorLayout(activity)
        val source = LensView(activity)
        val drops = ArrayList<Drop>()
        // Khởi tạo bởi newFixture trước khi callback kiểm thử được gọi.
        lateinit var strip: LensDropStrip
        val payload by lazy { LensDragPayload(source, app, "home") }
    }

    private fun lens(id: String, name: String) = LensWorkspace(id, name, 0, 0L, null)

    private fun newFixture(activity: ActHome, lenses: List<LensWorkspace>): Fixture {
        val f = Fixture(activity, lenses)
        (activity.window.decorView as ViewGroup).addView(f.root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        f.root.addView(f.source, ViewGroup.LayoutParams(10, 10))
        f.strip = LensDropStrip(object : LensDropStrip.Host {
            override fun activity() = activity
            override fun root() = f.root
            override fun lenses() = f.lenses
            override fun onDrop(payload: LensDragPayload, targetLensId: String, targetName: String) {
                f.drops.add(Drop(payload.sourceLensId, targetLensId, payload.app.packageName.toString()))
            }
        })
        return f
    }

    private fun destroy(f: Fixture) {
        f.strip.onDestroy()
        (f.root.parent as? ViewGroup)?.removeView(f.root)
    }

    private fun withFixture(lenses: List<LensWorkspace>, block: (Fixture) -> Unit) {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val f = newFixture(activity, lenses)
                try { block(f) } finally { destroy(f) }
            }
        }
    }

    private fun chips(f: Fixture): List<TextView> {
        val out = ArrayList<TextView>()
        fun walk(v: View) {
            if (v is TextView && v.tag is String) out.add(v)
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(f.strip.viewForTest())
        return out
    }

    private fun LensDropStrip.send(view: View, action: Int, state: Any?, x: Float = 0f) = handleDrag(view, action, state, x)

    @Test
    fun stripListsEveryLensExceptSource_andHasAccessibleChips() {
        withFixture(listOf(lens("home", "Home"), lens("work", "Work"), lens("play", "Play"))) { f ->
            assertEquals(View.GONE, f.strip.viewForTest().visibility)
            f.strip.show(f.payload)
            assertEquals(View.VISIBLE, f.strip.viewForTest().visibility)
            assertEquals(listOf("work", "play"), chips(f).map { it.tag })
            assertTrue(chips(f).all { it.minHeight >= 48 * f.activity.resources.displayMetrics.density - 1 })
            assertTrue(chips(f).all { it.contentDescription.toString().endsWith(it.text.toString()) })
        }
    }

    @Test
    fun enteredAndExitedChip_highlightsAndRestores() {
        withFixture(listOf(lens("home", "Home"), lens("work", "Work"))) { f ->
            f.strip.show(f.payload)
            val chip = chips(f).single(); val normal = chip.alpha
            f.strip.send(chip, DragEvent.ACTION_DRAG_ENTERED, f.payload)
            assertTrue(chip.alpha > normal && chip.scaleX > 1f)
            f.strip.send(chip, DragEvent.ACTION_DRAG_EXITED, f.payload)
            assertEquals(normal, chip.alpha, 0.001f); assertEquals(1f, chip.scaleX, 0.001f)
        }
    }

    @Test
    fun validDrop_callsHostExactlyOnceWithSourceTargetAndApp() {
        withFixture(listOf(lens("home", "Home"), lens("work", "Work"))) { f ->
            f.strip.show(f.payload)
            assertTrue(f.strip.send(chips(f).single(), DragEvent.ACTION_DROP, f.payload))
            assertEquals(listOf(Drop("home", "work", "com.test.dropstrip")), f.drops)
        }
    }

    @Test
    fun aSecondDropOfTheSameDrag_isIgnored() {
        withFixture(listOf(lens("home", "Home"), lens("work", "Work"))) { f ->
            f.strip.show(f.payload); val chip = chips(f).single()
            assertTrue(f.strip.send(chip, DragEvent.ACTION_DROP, f.payload))
            assertFalse("one drag moves the app once", f.strip.send(chip, DragEvent.ACTION_DROP, f.payload))
            assertEquals(1, f.drops.size)
        }
    }

    @Test
    fun aDropCarryingAnotherDragsState_isRejectedEvenIfTheStripHoldsAPayload() {
        withFixture(listOf(lens("home", "Home"), lens("work", "Work"))) { f ->
            f.strip.show(f.payload)
            assertFalse(f.strip.send(chips(f).single(), DragEvent.ACTION_DROP, "someone else's drag"))
            assertFalse(f.strip.send(chips(f).single(), DragEvent.ACTION_DROP, null))
            assertTrue(f.drops.isEmpty())
        }
    }

    @Test
    fun aDropWhoseSourceViewIsNoLongerAttached_isRejectedSoTheSystemShowsItFailed() {
        withFixture(listOf(lens("home", "Home"), lens("work", "Work"))) { f ->
            f.strip.show(f.payload)
            f.root.removeView(f.source)
            assertFalse("a detached source cannot write the move, so the drop must not be accepted",
                f.strip.send(chips(f).single(), DragEvent.ACTION_DROP, f.payload))
            assertTrue(f.drops.isEmpty())
        }
    }

    @Test
    fun deletedSource_isRejectedWithoutCallingHost() {
        withFixture(listOf(lens("home", "Home"), lens("work", "Work"))) { f ->
            f.strip.show(f.payload)
            f.lenses = listOf(lens("work", "Work"))
            assertFalse(f.strip.send(chips(f).single(), DragEvent.ACTION_DROP, f.payload))
            assertTrue(f.drops.isEmpty())
        }
    }

    @Test
    fun deletedTarget_isRejectedWithoutCallingHost() {
        withFixture(listOf(lens("home", "Home"), lens("work", "Work"))) { f ->
            f.strip.show(f.payload); val chip = chips(f).single()
            f.lenses = listOf(lens("home", "Home"))
            assertFalse(f.strip.send(chip, DragEvent.ACTION_DROP, f.payload))
            assertTrue(f.drops.isEmpty())
        }
    }

    @Test
    fun dragEnded_hidesStripAndRemovesChips() {
        withFixture(listOf(lens("home", "Home"), lens("work", "Work"))) { f ->
            f.strip.show(f.payload)
            f.strip.send(chips(f).single(), DragEvent.ACTION_DRAG_ENDED, f.payload)
            assertEquals(View.GONE, f.strip.viewForTest().visibility); assertTrue(chips(f).isEmpty())
        }
    }

    @Test
    fun foreignDragState_isNotAccepted() {
        withFixture(listOf(lens("home", "Home"), lens("work", "Work"))) { f ->
            f.strip.show(f.payload)
            assertFalse(f.strip.send(chips(f).single(), DragEvent.ACTION_DRAG_STARTED, "foreign"))
        }
    }

    @Test
    fun onDestroy_removesStripFromParent() {
        withFixture(listOf(lens("home", "Home"), lens("work", "Work"))) { f ->
            f.strip.show(f.payload); val view = f.strip.viewForTest()
            f.strip.onDestroy()
            assertNull(view.parent)
        }
    }

    @Test
    fun theStripClearsTheStatusBar_evenInsideACoordinatorLayout() {
        withFixture(listOf(lens("home", "Home"), lens("work", "Work"))) { f ->
            val top = ViewCompat.getRootWindowInsets(f.root)?.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )?.top ?: 0
            assumeTrue("needs a window with a real status bar inset", top > 0)
            f.strip.show(f.payload)
            val margin = (f.strip.viewForTest().layoutParams as ViewGroup.MarginLayoutParams).topMargin
            val gap = (8 * f.activity.resources.displayMetrics.density).toInt()
            assertTrue("strip top margin $margin must clear the status bar ($top) plus a gap", margin >= top + gap - 1)
        }
    }

    @Test
    fun holdingTheFingerAtTheRightEdgeScrollsToChipsThatDidNotFit() {
        val lenses = listOf(lens("home", "Home")) + (1..40).map { lens("l$it", "Lens number $it") }
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            // onActivity đồng bộ luôn gán fixture trước khi bước tiếp theo chạy.
            lateinit var f: Fixture
            scenario.onActivity { activity -> f = newFixture(activity, lenses); f.strip.show(f.payload) }
            instrumentation.waitForIdleSync()
            var before = -1
            scenario.onActivity {
                val scroll = (f.strip.viewForTest() as ViewGroup).getChildAt(0) as HorizontalScrollView
                assertTrue("40 chips must overflow the strip", scroll.getChildAt(0).width > scroll.width)
                before = scroll.scrollX
                val chip = chips(f).first()
                val xAtRightEdge = scroll.width - (chip.left - scroll.scrollX) - 1f
                f.strip.send(chip, DragEvent.ACTION_DRAG_LOCATION, f.payload, xAtRightEdge)
            }
            Thread.sleep(500)
            scenario.onActivity {
                val scroll = (f.strip.viewForTest() as ViewGroup).getChildAt(0) as HorizontalScrollView
                assertTrue("a pointer parked at the right edge must scroll the strip (was $before, now ${scroll.scrollX})", scroll.scrollX > before)
                f.strip.send(chips(f).first(), DragEvent.ACTION_DRAG_EXITED, f.payload)
                destroy(f)
            }
        }
    }

    @Test
    fun leavingTheEdgeStopsTheScrolling() {
        val lenses = listOf(lens("home", "Home")) + (1..40).map { lens("l$it", "Lens number $it") }
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            // onActivity đồng bộ luôn gán fixture trước khi bước tiếp theo chạy.
            lateinit var f: Fixture
            scenario.onActivity { activity -> f = newFixture(activity, lenses); f.strip.show(f.payload) }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val scroll = (f.strip.viewForTest() as ViewGroup).getChildAt(0) as HorizontalScrollView
                val chip = chips(f).first()
                f.strip.send(chip, DragEvent.ACTION_DRAG_LOCATION, f.payload, scroll.width - (chip.left - scroll.scrollX) - 1f)
            }
            Thread.sleep(200)
            var parked = -1
            scenario.onActivity {
                val scroll = (f.strip.viewForTest() as ViewGroup).getChildAt(0) as HorizontalScrollView
                val chip = chips(f).first()
                f.strip.send(chip, DragEvent.ACTION_DRAG_LOCATION, f.payload, scroll.width / 2f - (chip.left - scroll.scrollX))
                parked = scroll.scrollX
            }
            Thread.sleep(300)
            scenario.onActivity {
                val scroll = (f.strip.viewForTest() as ViewGroup).getChildAt(0) as HorizontalScrollView
                assertEquals("moving back to the middle must stop the scrolling", parked, scroll.scrollX)
                destroy(f)
            }
        }
    }
}
