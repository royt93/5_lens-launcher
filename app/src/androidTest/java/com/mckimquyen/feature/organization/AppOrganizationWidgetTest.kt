package com.mckimquyen.feature.organization

import android.view.ContextThemeWrapper
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import com.mckimquyen.adt.AppAdapter
import com.mckimquyen.model.App
import com.mckimquyen.model.PinnedZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppOrganizationWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    @Test
    fun organizationSummaryAndLongPressActionAreAccessible() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val context = ContextThemeWrapper(appContext, R.style.AppTheme)
        val app = App(
            label = "Mail",
            packageName = "com.example.mail",
            name = "Mail.Main",
            isFavorite = true,
            folderName = "Work",
            pinnedZone = PinnedZone.START
        )
        val adapter = AppAdapter(context, mutableListOf(app))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)

        adapter.onBindViewHolder(holder, 0)

        val summary = holder.itemView.findViewById<TextView>(R.id.tvAppOrganization)
        assertEquals(View.VISIBLE, summary.visibility)
        assertTrue(summary.text.contains(context.getString(R.string.organization_favorite_label)))
        assertTrue(summary.text.contains("Work"))
        assertTrue(summary.text.contains(context.getString(R.string.organization_pinned_start_label)))
        assertTrue(holder.itemView.isLongClickable)
    }

    @Test
    fun organizationSummaryHidesWhenNoMetadataExists() {
        val context = themedContext()
        val adapter = AppAdapter(context, mutableListOf(app("Plain")))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)

        adapter.onBindViewHolder(holder, 0)

        val summary = holder.itemView.findViewById<TextView>(R.id.tvAppOrganization)
        assertEquals(View.GONE, summary.visibility)
        assertTrue(summary.text.isEmpty())
    }

    @Test
    fun dragOrderingMovesAdjacentItemsAndRejectsEveryInvalidBoundary() {
        val adapter = AppAdapter(themedContext(), mutableListOf(app("One"), app("Two"), app("Three")))

        assertTrue(adapter.moveItem(0, 1, false))
        assertEquals(listOf("Two", "One", "Three"), (0 until adapter.itemCount).map {
            adapter.getItemForPosition(it).label
        })
        assertFalse(adapter.moveItem(-1, 0, false))
        assertFalse(adapter.moveItem(0, -1, false))
        assertFalse(adapter.moveItem(0, adapter.itemCount, false))
        assertFalse(adapter.moveItem(adapter.itemCount, 0, false))
        assertFalse(adapter.moveItem(1, 1, false))
    }

    /**
     * Real-device finding (release smoke 2026-11-23): with favorite + folder + pin the row drew the
     * app name and the organization summary on top of each other (label 71-144, summary 85-141).
     * Measures the real row layout and requires the summary to start at or below the label's bottom.
     */
    @Test
    fun organizationSummaryDoesNotOverlapTheAppLabel() {
        val context = themedContext()
        val application = App(
            label = "1.1.1.1",
            packageName = "com.example.overlap",
            name = "Overlap.Main",
            isFavorite = true,
            folderName = "SmokeF",
            pinnedZone = PinnedZone.START
        )
        val adapter = AppAdapter(context, mutableListOf(application))
        val parent = FrameLayout(context)
        val holder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(holder, 0)

        val row = holder.itemView
        row.measure(
            View.MeasureSpec.makeMeasureSpec(ROW_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)

        val label = row.findViewById<TextView>(R.id.tvAppLabel)
        val summary = row.findViewById<TextView>(R.id.tvAppOrganization)
        assertNotNull(label)
        assertEquals(View.VISIBLE, summary.visibility)
        val labelBounds = boundsInRow(row, label)
        val summaryBounds = boundsInRow(row, summary)
        assertTrue(
            "summary $summaryBounds must not start above the label bottom $labelBounds",
            summaryBounds.top >= labelBounds.bottom
        )
        assertTrue(
            "label $labelBounds and summary $summaryBounds must stay inside the row height ${row.measuredHeight}",
            labelBounds.top >= 0 && summaryBounds.bottom <= row.measuredHeight
        )
    }

    private fun boundsInRow(row: View, child: View): Rect {
        val bounds = Rect(0, 0, child.width, child.height)
        (row as ViewGroup).offsetDescendantRectToMyCoords(child, bounds)
        return bounds
    }

    private fun themedContext(): android.content.Context {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        return ContextThemeWrapper(appContext, R.style.AppTheme)
    }

    private fun app(label: String) = App(
        label = label,
        packageName = "com.example.${label.lowercase()}",
        name = "$label.Main"
    )

    private companion object {
        const val ROW_WIDTH_PX = 1080
    }
}
