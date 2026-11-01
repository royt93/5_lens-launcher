package com.mckimquyen.ui.dialog

import android.content.DialogInterface
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.ui.LensManagementActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LensDialogsMultiChoiceCapWidgetTest {
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

    private fun adapter(activity: LensManagementActivity) = ArrayAdapter(
        activity,
        android.R.layout.simple_list_item_multiple_choice,
        (0 until 7).map { "App $it" }
    )

    @Test
    fun selectionCapRejectsAdditionalItemsAndTruncatesInitialSelection() = withActivity { activity ->
        val confirmed = ArrayList<Set<Int>>()
        val initial = (0..6).toSet()
        val dialog = onMain {
            LensDialogs.multiChoice(
                activity, R.string.lens_choose_apps, adapter(activity), initial,
                android.R.string.ok, { confirmed.add(it) }, null,
                maxSelected = 6, limitMessageRes = android.R.string.ok
            ).also { it.show() }
        }
        instrumentation.waitForIdleSync()
        onMain {
            val list = dialog.listView
            assertEquals(ListView.CHOICE_MODE_MULTIPLE, list.choiceMode)
            assertEquals((0..5).toSet(), (0 until list.count).filter { list.isItemChecked(it) }.toSet())
            list.performItemClick(list.getChildAt(6), 6, list.adapter.getItemId(6))
            assertFalse("seventh item must remain unchecked", list.isItemChecked(6))
            assertEquals((0..5).toSet(), (0 until list.count).filter { list.isItemChecked(it) }.toSet())
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        instrumentation.waitForIdleSync()
        assertEquals(listOf((0..5).toSet()), confirmed)
        assertEquals("seed set is caller-owned", (0..6).toSet(), initial)
    }

    @Test
    fun limitMessageIsFormattedWithTheCapNotShownAsARawPlaceholder() = withActivity { activity ->
        val messages = ArrayList<CharSequence>()
        LensDialogs.limitMessageSink = { messages.add(it) }
        try {
            val dialog = onMain {
                LensDialogs.multiChoice(
                    activity, R.string.lens_choose_apps, adapter(activity), setOf(0, 1),
                    android.R.string.ok, {}, null,
                    maxSelected = 2, limitMessageRes = R.string.lens_share_limit_message
                ).also { it.show() }
            }
            instrumentation.waitForIdleSync()
            onMain {
                val list = dialog.listView
                list.performItemClick(list.getChildAt(2), 2, list.adapter.getItemId(2))
            }
            assertEquals(listOf<CharSequence>(activity.getString(R.string.lens_share_limit_message, 2)), messages)
            assertFalse("no unformatted placeholder: ${messages.single()}", messages.single().contains("%"))
            onMain { dialog.dismiss() }
        } finally {
            LensDialogs.limitMessageSink = null
        }
    }

    @Test
    fun requireSelectionDisablesConfirmUntilAnItemIsChecked() = withActivity { activity ->
        val dialog = onMain {
            LensDialogs.multiChoice(
                activity, R.string.lens_choose_apps, adapter(activity), emptySet(),
                android.R.string.ok, {}, null, requireSelection = true
            ).also { it.show() }
        }
        instrumentation.waitForIdleSync()
        onMain {
            val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            assertFalse("empty selection must not confirm", positive.isEnabled)
            val list = dialog.listView
            list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0))
            assertTrue("non-empty selection may confirm", positive.isEnabled)
            list.performItemClick(list.getChildAt(0), 0, list.adapter.getItemId(0))
            assertFalse("clearing last selection disables confirm", positive.isEnabled)
        }
        onMain { dialog.dismiss() }
    }

    @Test
    fun defaultsKeepUncappedSelectionAndAllowEmptyConfirm() = withActivity { activity ->
        val confirmed = ArrayList<Set<Int>>()
        val dialog = onMain {
            LensDialogs.multiChoice(
                activity, R.string.lens_choose_apps, adapter(activity), emptySet(),
                android.R.string.ok, { confirmed.add(it) }, null
            ).also { it.show() }
        }
        instrumentation.waitForIdleSync()
        onMain {
            assertTrue(dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled)
            val list = dialog.listView
            for (index in 0 until list.count) {
                list.performItemClick(list.getChildAt(index), index, list.adapter.getItemId(index))
            }
            assertTrue((0 until list.count).all { list.isItemChecked(it) })
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
        instrumentation.waitForIdleSync()
        assertEquals(listOf((0..6).toSet()), confirmed)
    }
}
