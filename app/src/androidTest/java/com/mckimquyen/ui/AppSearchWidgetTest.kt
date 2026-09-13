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
     * UI-009: replaced the old two-tier scrim+blur design (real RenderEffect blur caused visible
     * jank on the SearchBar<->SearchView morph) with a single near-opaque tonal scrim on every
     * API level - no blur to compensate for, so one opacity value covers all devices. The result
     * list itself still sits on an opaque card so its text is always readable regardless.
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
                assertTrue(
                    "scrim alpha should be dimmed (neither ~0 nor fully opaque), was $alpha",
                    alpha in 60..250
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
     * UI-009: while search is showing, the status/navigation bars must be painted the exact same
     * tonal color as the search scrim (not left transparent), so the whole screen reads as one
     * continuous surface; both must revert to transparent once fully hidden.
     */
    @Test
    fun searchShowingHarmonizesSystemBarColors_andHidingRevertsToTransparent() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var searchView: SearchView? = null
            scenario.onActivity { activity ->
                searchView = activity.findViewById(R.id.searchView)
                searchView!!.show()
            }
            waitUntilShowing(searchView!!, true)
            scenario.onActivity { activity ->
                val window = activity.window
                assertTrue(
                    "status bar must not be transparent while search is showing",
                    window.statusBarColor != android.graphics.Color.TRANSPARENT
                )
                assertEquals(
                    "status and navigation bar must share the same harmonized color",
                    window.statusBarColor,
                    window.navigationBarColor
                )
            }

            scenario.onActivity { searchView!!.hide() }
            waitUntilShowing(searchView!!, false)
            scenario.onActivity { activity ->
                val window = activity.window
                assertEquals(
                    "status bar must revert to transparent once search is fully hidden",
                    android.graphics.Color.TRANSPARENT,
                    window.statusBarColor
                )
                assertEquals(
                    "navigation bar must revert to transparent once search is fully hidden",
                    android.graphics.Color.TRANSPARENT,
                    window.navigationBarColor
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

    /**
     * UI-009 regression: onResume() unconditionally forced transparent system bars, which
     * clobbered the harmonized scrim color if the activity paused/resumed (Home button, app
     * switch) while SearchView was still showing - caught live on TECNO KJ7 (wallpaper showed
     * through behind the search panel instead of the matching tonal color after a resume).
     */
    @Test
    fun systemBarsStayHarmonizedAcrossPauseAndResumeWhileSearchIsShowing() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var searchView: SearchView? = null
            scenario.onActivity { activity ->
                searchView = activity.findViewById(R.id.searchView)
                searchView!!.show()
            }
            waitUntilShowing(searchView!!, true)

            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)

            scenario.onActivity { activity ->
                assertTrue(
                    "status bar must stay harmonized (not transparent) after a resume while search is showing",
                    activity.window.statusBarColor != android.graphics.Color.TRANSPARENT
                )
                assertEquals(
                    "status and navigation bar must still share the same harmonized color after resume",
                    activity.window.statusBarColor,
                    activity.window.navigationBarColor
                )
            }
        }
    }

    /** UI-009: blank query with no recent history - only the empty-state container shows. */
    @Test
    fun blankQueryWithNoHistoryShowsOnlyEmptyState() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var searchView: SearchView? = null
            scenario.onActivity { activity ->
                com.mckimquyen.search.SearchHistoryStore(activity).clear()
                searchView = activity.findViewById(R.id.searchView)
                searchView!!.show()
            }
            waitUntilShowing(searchView!!, true)
            scenario.onActivity { activity ->
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.tvNoSearchResults).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.recentHeader).visibility)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.resultsSectionHeader).visibility)
            }
        }
    }

    /** UI-009: a typed query with matches shows the "matching apps" section header, not recentHeader. */
    @Test
    fun nonEmptyQueryWithMatchesShowsResultsSectionHeader_notRecentHeader() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var searchView: SearchView? = null
            scenario.onActivity { activity ->
                searchView = activity.findViewById(R.id.searchView)
                searchView!!.show()
            }
            waitUntilShowing(searchView!!, true)
            scenario.onActivity { searchView!!.editText.setText("a") }
            scenario.onActivity { activity ->
                val hasResults = activity.findViewById<RecyclerView>(R.id.rvSearchResults).visibility == View.VISIBLE
                if (hasResults) {
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.resultsSectionHeader).visibility)
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.recentHeader).visibility)
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.tvNoSearchResults).visibility)
                }
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
