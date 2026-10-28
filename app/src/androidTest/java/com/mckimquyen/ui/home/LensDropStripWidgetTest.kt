package com.mckimquyen.ui.home

import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.model.App
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.ui.ActHome
import com.mckimquyen.views.LensDragPayload
import com.mckimquyen.views.LensView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** FISH-038b: the temporary drop strip, driven with real DragEvents on a real Activity. */
@RunWith(AndroidJUnit4::class)
class LensDropStripWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()
    private val app = App(label = "Drop App", packageName = "com.test.dropstrip", name = "Main")

    private data class Drop(val source: String, val target: String, val appId: String)
    private class Fixture(val activity: ActHome, var lenses: List<LensWorkspace>) {
        val root = FrameLayout(activity)
        val drops = ArrayList<Drop>()
        lateinit var strip: LensDropStrip
        lateinit var payload: LensDragPayload
    }

    private fun lens(id: String, name: String) = LensWorkspace(id, name, 0, 0L, null)

    private fun withFixture(lenses: List<LensWorkspace>, block: (Fixture) -> Unit) {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val f = Fixture(activity, lenses)
                (activity.window.decorView as ViewGroup).addView(f.root)
                f.payload = LensDragPayload(LensView(activity), app, "home")
                f.strip = LensDropStrip(object : LensDropStrip.Host {
                    override fun activity() = activity
                    override fun root() = f.root
                    override fun lenses() = f.lenses
                    override fun onDrop(payload: LensDragPayload, targetLensId: String, targetName: String) {
                        f.drops.add(Drop(payload.sourceLensId, targetLensId, payload.app.packageName.toString()))
                    }
                })
                try { block(f) } finally {
                    f.strip.onDestroy()
                    (f.root.parent as? ViewGroup)?.removeView(f.root)
                }
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

    private fun LensDropStrip.send(view: View, action: Int, state: Any?) = handleDrag(view, action, state)

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
}
