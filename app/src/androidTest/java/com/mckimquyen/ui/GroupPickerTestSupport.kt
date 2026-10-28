package com.mckimquyen.ui

import android.app.Dialog
import android.widget.ListView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mckimquyen.R
import org.junit.Assert.assertTrue

/** Drives the assign-to-group picker through its real rows, whether it is a sheet or a list dialog. */
internal object GroupPickerTestSupport {
    fun list(dialog: Dialog): ListView =
        requireNotNull(dialog.findViewById(R.id.lensGroupChoiceList)) { "group picker list not found" }

    fun labels(dialog: Dialog): List<String> =
        list(dialog).let { l -> (0 until l.count).map { l.getItemAtPosition(it).toString() } }

    fun isSheet(dialog: Dialog) = dialog is BottomSheetDialog

    fun click(dialog: Dialog, label: String) {
        val list = list(dialog)
        val labels = labels(dialog)
        val index = labels.indexOf(label)
        assertTrue("Expected '$label' in group picker rows: $labels", index >= 0)
        list.performItemClick(list.getChildAt(index), index, list.adapter.getItemId(index))
    }
}
