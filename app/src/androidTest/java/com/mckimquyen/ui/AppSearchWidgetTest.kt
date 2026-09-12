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

    private fun waitUntilVisibility(view: View, visibility: Int, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (view.visibility == visibility) return
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
    fun searchScrimIsDimmedNotOpaqueOrTransparent_andResultsSitOnAnOpaqueCard() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val resolvedScrim = ContextCompat.getColorStateList(
                    activity,
                    R.color.search_view_scrim_background
                )!!.defaultColor
                val alpha = Color.alpha(resolvedScrim)
                // UI-002 follow-up: two-tier design - API 31+ pairs a lighter scrim (~0.35) with
                // a real RenderEffect blur (ActHome.setLensBlurred) to hide detail behind the
                // panel; pre-31 devices have no blur API, so they lean on a heavier scrim
                // (~0.85) alone. Either way it must be dimmed, never fully opaque/transparent.
                assertTrue(
                    "scrim alpha should be dimmed (neither ~0 nor ~255), was $alpha",
                    alpha in 60..240
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

    /**
     * UI-002 follow-up: on Android 12+, showing/hiding the search overlay must toggle a real
     * blur behind it (`ActHome.setLensBlurred`), not just rely on the scrim's own opacity.
     * Pre-12 devices have no RenderEffect API at all - the check is skipped there, matching the
     * production code's own version guard.
     */
    @Test
    fun searchShowingAppliesBlur_andHidingClearsIt() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var searchView: SearchView? = null
            scenario.onActivity { activity ->
                searchView = activity.findViewById(R.id.searchView)
                searchView!!.show()
            }
            waitUntilShowing(searchView!!, true)
            scenario.onActivity { activity ->
                assertTrue(
                    "lens grid must be blurred while search is showing",
                    activity.lensBlurActive
                )
            }

            scenario.onActivity { searchView!!.hide() }
            waitUntilShowing(searchView!!, false)
            scenario.onActivity { activity ->
                assertTrue(
                    "lens grid blur must be cleared once search is fully hidden",
                    !activity.lensBlurActive
                )
            }
        }
    }

    /**
     * UI-007 fix: `SearchBar` was never actually hidden by the SearchBar->SearchView morph - it
     * relied entirely on the SearchView panel being opaque enough to cover it. Once UI-007 made
     * that panel translucent (real blur behind it instead), the SearchBar's own hint text
     * ("Tìm ứng dụng") started showing through, doubled up with the live SearchView edit text's
     * hint at almost the same on-screen position - a confusing ghosted-text overlap the owner
     * caught live on TECNO KJ7. Fixed by explicitly hiding SearchBar once SHOWN, restoring it at
     * HIDDEN.
     */
    @Test
    fun searchBarIsHiddenWhileSearchViewShown_toAvoidGhostedOverlappingHintText() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var searchView: SearchView? = null
            var searchBar: View? = null
            scenario.onActivity { activity ->
                searchBar = activity.findViewById(R.id.searchBar)
                assertEquals(View.VISIBLE, searchBar!!.visibility)
                searchView = activity.findViewById(R.id.searchView)
                searchView!!.show()
            }
            waitUntilShowing(searchView!!, true)
            // isShowing() flips true as soon as the SHOWING morph starts, before it settles into
            // SHOWN (where production code hides SearchBar) - poll for the real end state instead
            // of asserting immediately after isShowing().
            waitUntilVisibility(searchBar!!, View.INVISIBLE)
            scenario.onActivity {
                assertEquals(
                    "SearchBar must be hidden once SearchView is fully shown, or its hint text " +
                        "ghosts through the (intentionally translucent) panel on top of it",
                    View.INVISIBLE,
                    searchBar!!.visibility
                )
            }

            scenario.onActivity { searchView!!.hide() }
            waitUntilShowing(searchView!!, false)
            waitUntilVisibility(searchBar!!, View.VISIBLE)
            scenario.onActivity {
                assertEquals(
                    "SearchBar must reappear once SearchView is fully hidden",
                    View.VISIBLE,
                    searchBar!!.visibility
                )
            }
        }
    }

    /** SEARCH-002: calculator quick action renders above the normal results. */
    @Test
    fun quickActionRowShowsCalculatorResult() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var searchView: SearchView? = null
            scenario.onActivity { activity ->
                searchView = activity.findViewById(R.id.searchView)
                searchView!!.show()
            }
            waitUntilShowing(searchView!!, true)

            scenario.onActivity { searchView!!.editText.setText("12*7") }

            scenario.onActivity { activity ->
                val row = activity.findViewById<View>(R.id.quickActionRow)
                val label = activity.findViewById<TextView>(R.id.tvQuickActionLabel)
                val value = activity.findViewById<TextView>(R.id.tvQuickActionValue)
                assertEquals(View.VISIBLE, row.visibility)
                assertEquals("12*7", label.text.toString())
                assertEquals("84", value.text.toString())
            }
        }
    }

    /** A query that matches no quick-action sub-parser must keep the row hidden. */
    @Test
    fun quickActionRowHiddenForOrdinaryAppQuery() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var searchView: SearchView? = null
            scenario.onActivity { activity ->
                searchView = activity.findViewById(R.id.searchView)
                searchView!!.show()
            }
            waitUntilShowing(searchView!!, true)

            scenario.onActivity { searchView!!.editText.setText("definitely-missing-app") }

            scenario.onActivity { activity ->
                val row = activity.findViewById<View>(R.id.quickActionRow)
                assertEquals(View.GONE, row.visibility)
            }
        }
    }

    /** SEARCH-002: a mapped Settings keyword shows the quick action row, ready to deep-link. */
    @Test
    fun quickActionRowShowsSettingsShortcut() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var searchView: SearchView? = null
            scenario.onActivity { activity ->
                searchView = activity.findViewById(R.id.searchView)
                searchView!!.show()
            }
            waitUntilShowing(searchView!!, true)

            scenario.onActivity { searchView!!.editText.setText("wifi") }

            scenario.onActivity { activity ->
                val row = activity.findViewById<View>(R.id.quickActionRow)
                val label = activity.findViewById<TextView>(R.id.tvQuickActionLabel)
                assertEquals(View.VISIBLE, row.visibility)
                assertEquals("wifi", label.text.toString())
                assertTrue("row must be clickable", row.isClickable || row.hasOnClickListeners())
            }
        }
    }

    /**
     * SEARCH-002: `resolveBattery` needs a real Context (sticky broadcast), so it's covered here
     * instead of in the pure unit tests.
     */
    @Test
    fun quickActionBatteryResolvesARealPercentageOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = com.mckimquyen.search.QuickActionEngine.resolveBattery(context, "pin")
        assertTrue("expected a battery percentage like '42%', got $result", result != null && result.value.endsWith("%"))
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
