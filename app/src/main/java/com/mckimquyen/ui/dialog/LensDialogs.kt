package com.mckimquyen.ui.dialog

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.color.MaterialColors
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
    fun confirm(
        context: Context,
        @StringRes titleRes: Int,
        message: CharSequence,
        @StringRes positiveRes: Int,
        destructive: Boolean,
        onConfirm: Runnable,
        onDismiss: Runnable?,
    ): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(context, R.style.MaterialYouDialogTheme)
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(positiveRes) { _, _ -> onConfirm.run() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setCancelable(true)
        dialog.setOnDismissListener { onDismiss?.run() }
        if (destructive) {
            dialog.setOnShowListener {
                val error = MaterialColors.getColor(dialog.context, androidx.appcompat.R.attr.colorError, Color.RED)
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(error)
            }
        }
        return dialog
    }

    private inline fun TextInputEditText.doAfterTextChanged(crossinline action: () -> Unit) {
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = action()
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
    }
}
