package com.mckimquyen.ui

import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.search.SearchBar
import com.mckimquyen.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-017 widget proof for the collapsed launcher layout: search sits in its own top rail and
 * LensView starts below it, so app icons are visible and touchable instead of hiding underneath.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeLayoutWidgetTest {

    @Test
    fun collapsedHomeLayoutsGridBelowCompactSearchBar() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val searchBar = activity.findViewById<SearchBar>(R.id.searchBar)
                val lensView = activity.findViewById<View>(R.id.lensViews)

                assertTrue(
                    "LensView must start below the collapsed SearchBar so icons remain visible",
                    lensView.top >= searchBar.bottom
                )
                assertTrue(
                    "SearchBar should be a compact launcher affordance",
                    searchBar.height <= (56 * activity.resources.displayMetrics.density).toInt()
                )
            }
        }
    }
}
