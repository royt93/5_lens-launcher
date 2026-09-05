package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSearchWidgetTest {
    @Test
    fun searchSupportsFocusInputNoResultAndClear() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.etAppSearch).apply {
                    requestFocus()
                    setText("definitely-missing-app")
                }
                activity.findViewById<TextView>(R.id.tvNoSearchResults).let { emptyState ->
                    assertEquals(View.VISIBLE, emptyState.visibility)
                    assertEquals(activity.getString(R.string.no_apps_found), emptyState.text.toString())
                }
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.searchResultsCard).visibility)
                assertEquals(View.VISIBLE, activity.findViewById<ImageButton>(R.id.btClearAppSearch).visibility)
            }

            scenario.onActivity { activity ->
                activity.findViewById<ImageButton>(R.id.btClearAppSearch).performClick()
                assertEquals(
                    activity.getString(R.string.search_empty_state),
                    activity.findViewById<TextView>(R.id.tvNoSearchResults).text.toString()
                )
                assertEquals(View.GONE, activity.findViewById<ImageButton>(R.id.btClearAppSearch).visibility)
            }
        }
    }

    @Test
    fun searchQuerySurvivesRecreationAndBackClearsIt() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.etAppSearch).apply {
                    requestFocus()
                    setText("camera")
                }
            }

            scenario.recreate()
            scenario.onActivity { activity ->
                val search = activity.findViewById<EditText>(R.id.etAppSearch)
                assertEquals("camera", search.text.toString())
                activity.onBackPressedDispatcher.onBackPressed()
                assertEquals("", search.text.toString())
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.searchResultsCard).visibility)
                activity.onBackPressedDispatcher.onBackPressed()
                assertEquals(View.GONE, activity.findViewById<View>(R.id.searchResultsCard).visibility)
                assertEquals(false, search.hasFocus())
            }
        }
    }
}
