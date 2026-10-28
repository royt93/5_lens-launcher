package com.mckimquyen.ui.dialog

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mckimquyen.R

/** One Material You implementation for every lens-owned dialog. */
object LensDialogs {
    private const val REJECT_API_R = 30

    @JvmStatic
    @JvmOverloads
    fun textInput(
        context: Context,
        @StringRes titleRes: Int,
        @StringRes hintRes: Int,
        initialText: String?,
        @StringRes blankErrorRes: Int,
        onConfirm: (String) -> Unit,
        onDismiss: Runnable?,
        dismissesOnConfirm: Boolean = true,
    ): AlertDialog {
        val input = TextInputEditText(context).apply {
            id = android.R.id.edit
            isSingleLine = true
            setHint(hintRes)
            if (initialText != null) {
                setText(initialText)
                setSelection(text?.length ?: 0)
            } else {
                setSelectAllOnFocus(true)
            }
        }
        val field = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = context.getString(hintRes)
            addView(input)
        }
        input.doAfterTextChanged { field.error = null }
        val content = FrameLayout(context).apply {
            val horizontal = context.resources.getDimensionPixelSize(R.dimen.dialog_text_padding)
            val vertical = context.resources.getDimensionPixelSize(R.dimen.dialog_text_padding_vertical)
            setPadding(horizontal, vertical, horizontal, 0)
            addView(field)
        }
        val dialog = MaterialAlertDialogBuilder(context, R.style.MaterialYouDialogTheme)
            .setTitle(titleRes)
            .setView(content)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setCancelable(true)
        dialog.setOnDismissListener { onDismiss?.run() }
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val value = LensNameRules.normalize(input.text)
                if (value == null) {
                    field.error = context.getString(blankErrorRes)
                    val reject = if (android.os.Build.VERSION.SDK_INT >= REJECT_API_R) {
                        android.view.HapticFeedbackConstants.REJECT
                    } else android.view.HapticFeedbackConstants.VIRTUAL_KEY
                    input.performHapticFeedback(reject)
                    return@setOnClickListener
                }
                field.error = null
                if (dismissesOnConfirm) dialog.dismiss()
                onConfirm(value)
            }
        }
        return dialog
    }

    @JvmStatic
    @JvmOverloads
    fun confirm(
        context: Context,
        @StringRes titleRes: Int,
        message: CharSequence,
        @StringRes positiveRes: Int,
        destructive: Boolean,
        onConfirm: Runnable,
        onDismiss: Runnable?,
        dismissesOnConfirm: Boolean = true,
    ): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(context, R.style.MaterialYouDialogTheme)
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(positiveRes) { _, _ -> if (dismissesOnConfirm) onConfirm.run() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setCancelable(true)
        dialog.setOnDismissListener { onDismiss?.run() }
        dialog.setOnShowListener {
            if (!dismissesOnConfirm) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { onConfirm.run() }
            }
            if (destructive) {
                val error = MaterialColors.getColor(dialog.context, androidx.appcompat.R.attr.colorError, Color.RED)
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(error)
            }
        }
        return dialog
    }

    @JvmStatic
    fun <T> multiChoice(
        context: Context,
        @StringRes titleRes: Int,
        adapter: ArrayAdapter<T>,
        initiallyChecked: Set<Int>,
        @StringRes positiveRes: Int,
        onConfirm: (Set<Int>) -> Unit,
        onDismiss: Runnable?,
    ): AlertDialog {
        val selected = initiallyChecked.toMutableSet()
        val dialog = MaterialAlertDialogBuilder(context, R.style.MaterialYouDialogTheme)
            .setTitle(titleRes)
            .setAdapter(adapter, null)
            .setPositiveButton(positiveRes, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnDismissListener { onDismiss?.run() }
        dialog.setOnShowListener {
            val list = dialog.listView
            list.choiceMode = ListView.CHOICE_MODE_MULTIPLE
            initiallyChecked.forEach { if (it in 0 until list.count) list.setItemChecked(it, true) }
            list.setOnItemClickListener { _, _, position, _ ->
                if (list.isItemChecked(position)) selected.add(position) else selected.remove(position)
            }
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val result = selected.toSet()
                dialog.dismiss()
                onConfirm(result)
            }
        }
        return dialog
    }

    @JvmStatic
    fun singleChoice(
        context: Context,
        @StringRes titleRes: Int,
        labels: List<String>,
        onPick: (Int) -> Unit,
        onDismiss: Runnable?,
    ): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(context, R.style.MaterialYouDialogTheme)
            .setTitle(titleRes)
            .setItems(labels.toTypedArray()) { _, which -> onPick(which) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnDismissListener { onDismiss?.run() }
        return dialog
    }

    @JvmStatic
    fun choiceSheet(
        context: Context,
        @StringRes titleRes: Int,
        labels: List<String>,
        @StringRes createRes: Int,
        onPick: (Int) -> Unit,
        onCreate: Runnable,
        onCancel: Runnable?,
    ): BottomSheetDialog {
        val dialog = BottomSheetDialog(context, R.style.TransBottomSheetDialog)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 12), 0, dp(context, 8))
        }
        val title = TextView(context, null, com.google.android.material.R.attr.textAppearanceTitleMedium).apply {
            id = R.id.lensGroupChoiceTitle
            text = context.getString(titleRes)
            gravity = Gravity.CENTER_VERTICAL
            val horizontal = dp(context, 24)
            setPadding(horizontal, dp(context, 12), horizontal, dp(context, 12))
        }
        content.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val rows = labels + context.getString(createRes)
        val list = ListView(context).apply {
            id = R.id.lensGroupChoiceList
            dividerHeight = 0
            adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, rows)
            setOnItemClickListener { _, _, position, _ ->
                dialog.dismiss()
                if (position < labels.size) onPick(position) else onCreate.run()
            }
        }
        content.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        dialog.setContentView(content)
        dialog.setOnCancelListener { onCancel?.run() }
        dialog.setOnShowListener {
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
        return dialog
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private inline fun TextInputEditText.doAfterTextChanged(crossinline action: () -> Unit) {
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = action()
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
    }
}
