package com.mckimquyen.ui.dialog

import android.content.DialogInterface
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mckimquyen.R
import com.mckimquyen.ui.LensManagementActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Material You lens dialogs, built by the shared layer on a real themed Activity. */
@RunWith(AndroidJUnit4::class)
class LensDialogsWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    /** Dialog show/dismiss listeners are posted to the main handler, so every step is followed by an idle wait. */
    private inner class Steps(val scenario: ActivityScenario<LensManagementActivity>) {
        fun <T> main(block: (LensManagementActivity) -> T): T {
            var result: Any? = null
            scenario.onActivity { result = block(it) }
            instrumentation.waitForIdleSync()
            @Suppress("UNCHECKED_CAST")
            return result as T
        }
    }

    private fun withSteps(block: Steps.() -> Unit) {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            Steps(scenario).block()
        }
    }

    private fun layoutOf(dialog: AlertDialog): TextInputLayout {
        val edit = dialog.findViewById<EditText>(android.R.id.edit)!!
        var v: View? = edit
        while (v != null && v !is TextInputLayout) v = v.parent as? View
        return v as TextInputLayout
    }

    private fun name(
        activity: LensManagementActivity,
        initial: String? = null,
        confirmed: MutableList<String> = ArrayList(),
        dismissed: MutableList<String> = ArrayList(),
        dismissesOnConfirm: Boolean = true,
    ): AlertDialog = LensDialogs.textInput(
        activity, R.string.lens_group_create, R.string.lens_group_name_hint, initial,
        R.string.lens_group_error_blank, { confirmed.add(it) }, { dismissed.add("dismissed") }, dismissesOnConfirm,
    ).also { it.show() }

    private fun click(dialog: AlertDialog, which: Int) = dialog.getButton(which).performClick()

    @Test
    fun nameFieldIsAnOutlinedTextInputLayoutKeepingTheEditId() = withSteps {
        val dialog = main { name(it, initial = "Daily") }
        main { activity ->
            val layout = layoutOf(dialog)
            assertEquals(TextInputLayout.BOX_BACKGROUND_OUTLINE, layout.boxBackgroundMode)
            val edit = dialog.findViewById<EditText>(android.R.id.edit)!!
            assertTrue("the inner field must be a TextInputEditText", edit is TextInputEditText)
            assertEquals("Daily", edit.text.toString())
            assertEquals(activity.getString(R.string.lens_group_name_hint), layout.hint.toString())
            dialog.dismiss()
        }
    }

    @Test
    fun blankNameShowsTheErrorOnTheLayoutAndKeepsTheDialogOpen() = withSteps {
        val confirmed = ArrayList<String>()
        val dialog = main { name(it, confirmed = confirmed) }
        main { dialog.findViewById<EditText>(android.R.id.edit)!!.setText("   "); click(dialog, DialogInterface.BUTTON_POSITIVE) }
        main { activity ->
            assertTrue(dialog.isShowing)
            assertEquals(activity.getString(R.string.lens_group_error_blank), layoutOf(dialog).error.toString())
            assertTrue("a blank name must never be confirmed", confirmed.isEmpty())
            dialog.dismiss()
        }
    }

    @Test
    fun anOverlongNameIsRejectedWithTheSameError() = withSteps {
        val confirmed = ArrayList<String>()
        val dialog = main { name(it, confirmed = confirmed) }
        main { dialog.findViewById<EditText>(android.R.id.edit)!!.setText("a".repeat(LensNameRules.MAX_LENGTH + 1)); click(dialog, DialogInterface.BUTTON_POSITIVE) }
        main { activity ->
            assertTrue(dialog.isShowing)
            assertEquals(activity.getString(R.string.lens_group_error_blank), layoutOf(dialog).error.toString())
            assertTrue(confirmed.isEmpty())
            dialog.dismiss()
        }
    }

    @Test
    fun typingAfterAnErrorClearsIt() = withSteps {
        val dialog = main { name(it) }
        main { dialog.findViewById<EditText>(android.R.id.edit)!!.setText(" "); click(dialog, DialogInterface.BUTTON_POSITIVE) }
        main { assertNotNull(layoutOf(dialog).error); dialog.findViewById<EditText>(android.R.id.edit)!!.setText("Daily") }
        main { assertNull("typing must clear the stale error", layoutOf(dialog).error); dialog.dismiss() }
    }

    @Test
    fun aValidNameIsConfirmedOnceTrimmedAndTheDialogCloses() = withSteps {
        val confirmed = ArrayList<String>()
        val dismissed = ArrayList<String>()
        val dialog = main { name(it, confirmed = confirmed, dismissed = dismissed) }
        main { dialog.findViewById<EditText>(android.R.id.edit)!!.setText("  Daily  "); click(dialog, DialogInterface.BUTTON_POSITIVE) }
        main {
            assertEquals(listOf("Daily"), confirmed)
            assertFalse(dialog.isShowing)
            assertEquals(listOf("dismissed"), dismissed)
        }
    }

    @Test
    fun theCallerCanOwnTheDismissal() = withSteps {
        val confirmed = ArrayList<String>()
        val dialog = main { name(it, confirmed = confirmed, dismissesOnConfirm = false) }
        main { dialog.findViewById<EditText>(android.R.id.edit)!!.setText("Daily"); click(dialog, DialogInterface.BUTTON_POSITIVE) }
        main {
            assertEquals(listOf("Daily"), confirmed)
            assertTrue("dismissesOnConfirm=false must leave the dialog to the caller", dialog.isShowing)
            dialog.dismiss()
        }
    }

    @Test
    fun cancelNeverConfirmsAndReportsTheDismissal() = withSteps {
        val confirmed = ArrayList<String>()
        val dismissed = ArrayList<String>()
        val dialog = main { name(it, confirmed = confirmed, dismissed = dismissed) }
        main { click(dialog, DialogInterface.BUTTON_NEGATIVE) }
        main {
            assertTrue(confirmed.isEmpty())
            assertEquals(listOf("dismissed"), dismissed)
        }
    }

    @Test
    fun theDestructiveConfirmUsesTheThemeErrorColour() = withSteps {
        val dialog = main { LensDialogs.confirm(it, R.string.lens_delete, "Delete Work?", R.string.lens_delete, true, {}, null).also { d -> d.show() } }
        main {
            val expected = MaterialColors.getColor(dialog.context, androidx.appcompat.R.attr.colorError, 0)
            assertNotEquals("the theme must define colorError", 0, expected)
            assertEquals(expected, dialog.getButton(DialogInterface.BUTTON_POSITIVE).currentTextColor)
            dialog.dismiss()
        }
    }

    @Test
    fun aNormalConfirmKeepsTheDefaultButtonColour() = withSteps {
        val dialog = main { LensDialogs.confirm(it, R.string.lens_unfreeze_title, "Sure?", R.string.lens_unfreeze_positions, false, {}, null).also { d -> d.show() } }
        main {
            val error = MaterialColors.getColor(dialog.context, androidx.appcompat.R.attr.colorError, 0)
            assertNotEquals(error, dialog.getButton(DialogInterface.BUTTON_POSITIVE).currentTextColor)
            dialog.dismiss()
        }
    }

    @Test
    fun confirmRunsOnceAndCancelNeverRunsIt() = withSteps {
        var ran = 0
        val dismissed = ArrayList<String>()
        val ok = main { LensDialogs.confirm(it, R.string.lens_delete, "Delete?", R.string.lens_delete, true, { ran++ }, { dismissed.add("d") }).also { d -> d.show() } }
        main { click(ok, DialogInterface.BUTTON_POSITIVE) }
        main { assertEquals(1, ran); assertFalse(ok.isShowing) }
        val cancel = main { LensDialogs.confirm(it, R.string.lens_delete, "Delete?", R.string.lens_delete, true, { ran++ }, null).also { d -> d.show() } }
        main { click(cancel, DialogInterface.BUTTON_NEGATIVE) }
        main {
            assertEquals("cancel must not run the action", 1, ran)
            assertEquals(listOf("d"), dismissed)
        }
    }

    @Test
    fun theMessageIsRenderedWithTheTitle() = withSteps {
        val dialog = main { LensDialogs.confirm(it, R.string.lens_delete, "Delete Work?", R.string.lens_delete, true, {}, null).also { d -> d.show() } }
        main {
            val texts = ArrayList<String>()
            fun walk(v: View) { if (v is android.widget.TextView) texts.add(v.text.toString()); if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i)) }
            walk(dialog.window!!.decorView)
            assertTrue(texts.contains("Delete Work?"))
            dialog.dismiss()
        }
    }
}
