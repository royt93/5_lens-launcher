package com.mckimquyen.ui

import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-023: widget coverage for `FrmSettings`'s custom search-hint-text `AlertDialog`
 * (`showSearchHintDialog`) - the second of two dialogs found to lose their typed text across a
 * config change because their `EditText` was built with no id (same root cause and fix already
 * applied to the "Set folder" dialog, see `AppAdapterFolderDialogWidgetTest`).
 */
@RunWith(AndroidJUnit4::class)
class FrmSettingsSearchHintWidgetTest {

    private fun findEditText(dialog: AlertDialog): EditText {
        var found: EditText? = null
        fun walk(view: android.view.View) {
            if (view is EditText) {
                found = view
                return
            }
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
            }
        }
        walk(dialog.window!!.decorView)
        return requireNotNull(found) { "Search hint EditText not found in dialog" }
    }

    /**
     * Same mechanism proven in `AppAdapterFolderDialogWidgetTest`: a `View` with no id is
     * skipped by `View.dispatchSaveInstanceState`/`dispatchRestoreInstanceState` (documented
     * AOSP contract) - exercising the real `saveHierarchyState`/`restoreHierarchyState` pair
     * directly proves the exact mechanism a config change drives, independent of which OS event
     * actually triggers it on a given device/config.
     */
    @Test
    fun searchHintDialogInput_survivesAHierarchyStateRoundTrip() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.requireView().findViewById<View>(R.id.llSearchHintText).performClick()
                val dialog = fragment.searchHintDialog!!
                val input = findEditText(dialog)
                input.setText("SurviveMe")

                val states = android.util.SparseArray<android.os.Parcelable>()
                val root = dialog.window!!.decorView
                root.saveHierarchyState(states)
                input.setText("")
                root.restoreHierarchyState(states)

                assertEquals(
                    "typed search hint must survive a hierarchy-state save/restore round trip - the exact mechanism a config change drives",
                    "SurviveMe",
                    input.text.toString()
                )
            }
        }
    }
}
