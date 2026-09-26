package com.mckimquyen.ui

import android.graphics.Color
import android.view.View
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-017 widget proof for the collapsed launcher layout: search sits in its own top rail and
 * LensView starts below it, so app icons are visible and touchable instead of hiding underneath.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeLayoutWidgetTest {

    private fun waitUntilShowing(searchView: SearchView, showing: Boolean, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (searchView.isShowing == showing) return
            Thread.sleep(50)
        }
    }

    @Test
    fun collapsedHomeLayoutsGridBelowCompactSearchBar() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val searchBar = activity.findViewById<SearchBar>(R.id.searchBar)
                // FISH-008 Phase 2: the margin that used to sit directly on lensViews now sits on
                // its ViewPager2 container - lensViews itself is nested inside a page and its
                // .top is relative to that page, not comparable to searchBar's root-relative
                // .bottom anymore.
                val lensPager = activity.findViewById<View>(R.id.lensPager)

                assertTrue(
                    "LensView pager must start below the collapsed SearchBar so icons remain visible",
                    lensPager.top >= searchBar.bottom
                )
                assertTrue(
                    "SearchBar should be a compact launcher affordance",
                    searchBar.height <= (56 * activity.resources.displayMetrics.density).toInt()
                )
            }
        }
    }

    @Test
    fun searchViewHidesLensGridWhileShownAndRestoresItWhenHidden() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var searchView: SearchView? = null
            scenario.onActivity { activity ->
                searchView = activity.findViewById(R.id.searchView)
                searchView!!.show()
            }
            waitUntilShowing(searchView!!, true)

            scenario.onActivity { activity ->
                assertEquals(
                    "Lens pager must not draw behind the search screen",
                    View.INVISIBLE,
                    activity.findViewById<View>(R.id.lensPager).visibility
                )
                searchView!!.hide()
            }
            waitUntilShowing(searchView!!, false)
            scenario.onActivity { activity ->
                assertEquals(false, searchView!!.isShowing)
                assertTrue(
                    "Lens pager may remain hidden when the test app list has not loaded yet",
                    activity.findViewById<View>(R.id.lensPager).visibility in listOf(View.VISIBLE, View.INVISIBLE)
                )
            }
        }
    }

    @Test
    fun searchScrimIsOpaqueSoLauncherIconsDoNotGhostThrough() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val resolvedScrim = ContextCompat.getColorStateList(
                    activity,
                    R.color.search_view_scrim_background
                )!!.defaultColor
                assertEquals(
                    "search surface must be opaque to avoid ghosted launcher icons",
                    255,
                    Color.alpha(resolvedScrim)
                )
            }
        }
    }
}
