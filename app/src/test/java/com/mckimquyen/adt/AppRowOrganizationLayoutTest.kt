package com.mckimquyen.adt

import android.content.Context
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.model.PinnedZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Release smoke 2026-11-23 (KJ7): with favorite + folder + pin the Apps-tab row drew the app name
 * and the organization summary on top of each other. This measures the real row layout and the
 * real adapter bind, with and without a summary, and requires the summary to sit below the label.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppRowOrganizationLayoutTest {

    private fun context(): Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.AppTheme)

    private fun boundsInRow(row: View, child: View): Rect {
        val bounds = Rect(0, 0, child.width, child.height)
        (row as ViewGroup).offsetDescendantRectToMyCoords(child, bounds)
        return bounds
    }

    private fun laidOutRow(app: App): View {
        val context = context()
        val adapter = AppAdapter(context, mutableListOf(app))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)
        val row = holder.itemView
        row.measure(
            View.MeasureSpec.makeMeasureSpec(ROW_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)
        return row
    }

    @Test
    fun summaryStartsBelowTheLabelWhenFavoriteFolderAndPinAreSet() {
        val row = laidOutRow(
            App(
                label = "1.1.1.1", packageName = "com.example.overlap", name = "Overlap.Main",
                isFavorite = true, folderName = "SmokeF", pinnedZone = PinnedZone.START
            )
        )
        val label = row.findViewById<TextView>(R.id.tvAppLabel)
        val summary = row.findViewById<TextView>(R.id.tvAppOrganization)
        assertEquals(View.VISIBLE, summary.visibility)
        val labelBounds = boundsInRow(row, label)
        val summaryBounds = boundsInRow(row, summary)
        assertTrue("summary $summaryBounds starts above label bottom $labelBounds", summaryBounds.top >= labelBounds.bottom)
        assertTrue(
            "label $labelBounds / summary $summaryBounds leave the row height ${row.measuredHeight}",
            labelBounds.top >= 0 && summaryBounds.bottom <= row.measuredHeight
        )
    }

    @Test
    fun labelStaysCenteredWhenThereIsNoSummary() {
        val row = laidOutRow(App(label = "Plain", packageName = "com.example.plain", name = "Plain.Main"))
        val label = row.findViewById<TextView>(R.id.tvAppLabel)
        assertEquals(View.GONE, row.findViewById<TextView>(R.id.tvAppOrganization).visibility)
        val bounds = boundsInRow(row, label)
        val centerDelta = Math.abs((bounds.top + bounds.bottom) / 2 - row.measuredHeight / 2)
        assertTrue("label $bounds is not centered in height ${row.measuredHeight}", centerDelta <= CENTER_TOLERANCE_PX)
    }

    private companion object {
        const val ROW_WIDTH_PX = 1080
        const val CENTER_TOLERANCE_PX = 2
    }
}
