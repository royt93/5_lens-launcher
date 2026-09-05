package com.mckimquyen.search

import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import com.mckimquyen.model.App
import com.mckimquyen.ui.ActHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchResultAdapterWidgetTest {

    @Test
    fun submitBindAndClickExposeCurrentAppAndAccessibleContent() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val first = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
                val second = App(label = "Notes", packageName = "pkg.notes", name = "NotesActivity")
                var clickedApp: App? = null
                var clickedSource: android.view.View? = null
                val adapter = SearchResultAdapter { app, source ->
                    clickedApp = app
                    clickedSource = source
                }

                assertFalse(adapter.hasStableIds())
                assertNull(adapter.firstOrNull())
                adapter.submitList(listOf(first, second))
                assertEquals(2, adapter.itemCount)
                assertSame(first, adapter.firstOrNull())

                val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                adapter.onBindViewHolder(holder, 0)
                assertEquals(
                    "Camera",
                    holder.itemView.findViewById<TextView>(R.id.tvSearchResultLabel).text.toString()
                )
                assertEquals(
                    "pkg.camera",
                    holder.itemView.findViewById<TextView>(R.id.tvSearchResultPackage).text.toString()
                )
                assertEquals(activity.getString(R.string.search_open_app, "Camera"), holder.itemView.contentDescription)

                holder.itemView.performClick()
                assertSame(first, clickedApp)
                assertSame(holder.itemView, clickedSource)

                adapter.submitList(listOf(second))
                assertEquals(1, adapter.itemCount)
                assertSame(second, adapter.firstOrNull())
            }
        }
    }
}
