package com.mckimquyen.ui.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.widget.ArrayAdapter
import android.widget.ListView
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mckimquyen.R
import com.mckimquyen.ui.LensManagementActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensDialogsChoiceWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun withActivity(block: (LensManagementActivity) -> Unit) {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            lateinit var activity: LensManagementActivity
            scenario.onActivity { activity = it }
            instrumentation.waitForIdleSync()
            block(activity)
            instrumentation.waitForIdleSync()
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Any? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    @Test
    fun multiChoiceKeepsTheCallerSetAsSourceAndConfirmsCopy() = withActivity { activity ->
        val adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_multiple_choice, listOf("Alpha", "Beta", "Gamma"))
        val initial = linkedSetOf(1)
        val confirmed = ArrayList<Set<Int>>()
        val dialog = onMain { LensDialogs.multiChoice(activity, R.string.lens_choose_apps, adapter, initial, android.R.string.ok, { confirmed.add(it) }, null).also { it.show() } }
        instrumentation.waitForIdleSync()
        onMain {
            val list = dialog.listView
            assertEquals(ListView.CHOICE_MODE_MULTIPLE, list.choiceMode)
            assertTrue(list.isItemChecked(1))
            list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0))
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        instrumentation.waitForIdleSync()
        assertEquals(setOf(0, 1), confirmed.single())
        assertEquals("the dialog must not mutate the caller-owned seed", setOf(1), initial)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun singleChoiceReturnsTheClickedIndexExactlyOnce() = withActivity { activity ->
        val picks = ArrayList<Int>()
        val dialog = onMain { LensDialogs.singleChoice(activity, R.string.move_to_lens_title, listOf("Work", "Play"), { picks.add(it) }, null).also { it.show() } }
        instrumentation.waitForIdleSync()
        onMain { val list = dialog.listView; list.performItemClick(list.getChildAt(1), 1, list.adapter.getItemId(1)) }
        instrumentation.waitForIdleSync()
        assertEquals(listOf(1), picks)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun choiceSheetListsGroupsThenCreateRowAndUsesMinimumTouchTargets() = withActivity { activity ->
        var picked = -1
        var created = 0
        val dialog = onMain { LensDialogs.choiceSheet(activity, R.string.lens_group_edit_title, listOf("Daily", "Work"), R.string.lens_group_create, { picked = it }, { created++ }, null).also { it.show() } }
        instrumentation.waitForIdleSync()
        assertTrue(dialog is BottomSheetDialog)
        assertEquals(
            com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED,
            (dialog as BottomSheetDialog).behavior.state
        )
        onMain {
            val list = dialog.findViewById<ListView>(R.id.lensGroupChoiceList)!!
            assertEquals(3, list.count)
            assertEquals("Daily", list.getItemAtPosition(0).toString())
            assertEquals("Work", list.getItemAtPosition(1).toString())
            assertEquals(activity.getString(R.string.lens_group_create), list.getItemAtPosition(2).toString())
            assertTrue(list.adapter.getView(0, null, list).minimumHeight >= dp(activity, 48))
            list.performItemClick(list.getChildAt(1), 1, list.adapter.getItemId(1))
        }
        instrumentation.waitForIdleSync()
        assertEquals(1, picked)
        assertEquals(0, created)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun choiceSheetCreateRowRunsCreateCallbackAndCancelRunsNeitherAction() = withActivity { activity ->
        var picked = 0
        var created = 0
        var cancelled = 0
        val dialog = onMain { LensDialogs.choiceSheet(activity, R.string.lens_group_edit_title, listOf("Work"), R.string.lens_group_create, { picked++ }, { created++ }, { cancelled++ }).also { it.show() } }
        instrumentation.waitForIdleSync()
        onMain { val list = dialog.findViewById<ListView>(R.id.lensGroupChoiceList)!!; list.performItemClick(list.getChildAt(1), 1, list.adapter.getItemId(1)) }
        instrumentation.waitForIdleSync()
        assertEquals(1, created); assertEquals(0, picked); assertEquals(0, cancelled); assertFalse(dialog.isShowing)
        val second = onMain { LensDialogs.choiceSheet(activity, R.string.lens_group_edit_title, listOf("Work"), R.string.lens_group_create, { picked++ }, { created++ }, { cancelled++ }).also { it.show() } }
        instrumentation.waitForIdleSync(); onMain { second.cancel() }; instrumentation.waitForIdleSync()
        assertEquals(1, cancelled); assertEquals(0, picked)
    }

    @Test
    fun choiceSheetCancelCallbackIsNotCalledWhenAChoiceWasMade() = withActivity { activity ->
        var cancelled = 0
        val dialog = onMain { LensDialogs.choiceSheet(activity, R.string.lens_group_edit_title, listOf("Work"), R.string.lens_group_create, {}, {}, { cancelled++ }).also { it.show() } }
        instrumentation.waitForIdleSync()
        onMain { val list = dialog.findViewById<ListView>(R.id.lensGroupChoiceList)!!; list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0)) }
        instrumentation.waitForIdleSync()
        assertEquals(0, cancelled)
    }

    private fun dp(activity: LensManagementActivity, value: Int) =
        (value * activity.resources.displayMetrics.density).toInt()
}
