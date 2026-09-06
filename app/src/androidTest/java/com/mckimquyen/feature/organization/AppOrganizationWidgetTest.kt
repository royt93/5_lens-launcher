package com.mckimquyen.feature.organization

import android.view.ContextThemeWrapper
import android.view.View
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppOrganizationWidgetTest {
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

    private fun themedContext(): android.content.Context {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        return ContextThemeWrapper(appContext, R.style.AppTheme)
    }

    private fun app(label: String) = App(
        label = label,
        packageName = "com.example.${label.lowercase()}",
        name = "$label.Main"
    )
}
