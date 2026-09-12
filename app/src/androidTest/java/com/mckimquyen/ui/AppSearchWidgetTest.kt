package com.mckimquyen.ui

import android.graphics.Color
import android.view.View
import android.view.ViewParent
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.card.MaterialCardView
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-001: updated for the real Material3 SearchBar/SearchView (was a plain EditText +
 * CardView). Drives the public SearchView API (show/hide/clearText/editText/isShowing)
 * instead of the removed etAppSearch/searchResultsCard/btClearAppSearch ids.
 */
@RunWith(AndroidJUnit4::class)
class AppSearchWidgetTest {

    private fun waitUntilShowing(searchView: SearchView, showing: Boolean, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (searchView.isShowing == showing) return
            Thread.sleep(50)
        }
    }

    @Test
    fun searchSupportsFocusInputNoResultAndClear() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var searchView: SearchView? = null
            scenario.onActivity { activity ->
                searchView = activity.findViewById(R.id.searchView)
                searchView!!.show()
            }
            waitUntilShowing(searchView!!, true)

            scenario.onActivity {
                searchView!!.editText.setText("definitely-missing-app")
            }
            scenario.onActivity { activity ->
                activity.findViewById<TextView>(R.id.tvNoSearchResults).let { emptyState ->
                    assertEquals(View.VISIBLE, emptyState.visibility)
                    assertEquals(activity.getString(R.string.no_apps_found), emptyState.text.toString())
                }
            }

            scenario.onActivity {
                searchView!!.clearText()
            }
            scenario.onActivity { activity ->
                assertEquals("", searchView!!.editText.text.toString())
                assertEquals(
                    activity.getString(R.string.search_empty_state),
                    activity.findViewById<TextView>(R.id.tvNoSearchResults).text.toString()
                )
            }
        }
    }

    @Test
    fun searchQuerySurvivesRecreationAndHideClosesIt() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var searchView: SearchView? = null
            scenario.onActivity { activity ->
                searchView = activity.findViewById(R.id.searchView)
                searchView!!.show()
            }
            waitUntilShowing(searchView!!, true)
            scenario.onActivity {
                searchView!!.editText.setText("camera")
            }

            scenario.recreate()
            scenario.onActivity { activity ->
                searchView = activity.findViewById(R.id.searchView)
                assertEquals("camera", searchView!!.editText.text.toString())
                searchView!!.hide()
            }
            waitUntilShowing(searchView!!, false)
            scenario.onActivity {
                assertEquals(false, searchView!!.isShowing)
            }
        }
    }

    @Test
    fun settingsToggleHidesAndShowsSearchBarOnResume() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        try {
            prefs.edit().putBoolean(UtilSettings.KEY_SHOW_SEARCH_BAR, false).commit()
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val searchBar = activity.findViewById<SearchBar>(R.id.searchBar)
                    assertEquals(View.GONE, searchBar.visibility)
                }
            }

            prefs.edit().putBoolean(UtilSettings.KEY_SHOW_SEARCH_BAR, true).commit()
            ActivityScenario.launch(ActHome::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val searchBar = activity.findViewById<SearchBar>(R.id.searchBar)
                    assertEquals(View.VISIBLE, searchBar.visibility)
                }
            }
        } finally {
            prefs.edit().remove(UtilSettings.KEY_SHOW_SEARCH_BAR).commit()
        }
    }

    /**
     * UI-002: the search overlay's backdrop must be dimmed (not fully opaque, not fully
     * transparent) while the result list itself stays on an opaque card so its text is always
     * readable regardless of what's behind the scrim.
     */
    @Test
    fun searchScrimIsHalfOpaque_andResultsSitOnAnOpaqueCard() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val resolvedScrim = ContextCompat.getColorStateList(
                    activity,
                    R.color.search_view_scrim_background
                )!!.defaultColor
                val alpha = Color.alpha(resolvedScrim)
                assertTrue(
                    "scrim alpha should be roughly half-opaque (~128/255), was $alpha",
                    alpha in 100..155
                )

                val results = activity.findViewById<RecyclerView>(R.id.rvSearchResults)
                var parent: ViewParent? = results.parent
                var foundCard = false
                while (parent != null) {
                    if (parent is MaterialCardView) {
                        foundCard = true
                        break
                    }
                    parent = parent.parent
                }
                assertTrue("result list must sit inside an opaque MaterialCardView panel", foundCard)
            }
        }
    }

    @Test
    fun searchResultsHaveExactlyOneDividerDecoration() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val results = activity.findViewById<RecyclerView>(R.id.rvSearchResults)
                assertEquals(1, results.itemDecorationCount)
                assertTrue(
                    "the single decoration must be a DividerItemDecoration",
                    (0 until results.itemDecorationCount).all {
                        results.getItemDecorationAt(it) is DividerItemDecoration
                    }
                )
            }
        }
    }
}
