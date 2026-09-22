package com.mckimquyen.ui

import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.search.SearchBar
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-019 regression: ActHome.applyHomeColumnInsets() used to read searchBar's CURRENT
 * layoutParams.topMargin as its "base" margin on every call - but that margin had already been
 * overwritten with (base + inset) by the previous call. Every onConfigurationChanged (rotation,
 * etc.) re-ran applyHomeColumnInsets, so the top gap grew a little larger on every single
 * config change instead of staying fixed. The fix reads a fixed dimen instead. This proves the
 * search bar's top margin is identical before and after two consecutive config-change passes.
 */
@RunWith(AndroidJUnit4::class)
class ActHomeMarginStabilityWidgetTest {

    private fun currentTopMargin(activity: ActHome): Int {
        val searchBar = activity.findViewById<SearchBar>(R.id.searchBar)
        return (searchBar.layoutParams as ViewGroup.MarginLayoutParams).topMargin
    }

    /** Insets are dispatched asynchronously after requestApplyInsets(); poll for settle. */
    private fun waitForStableTopMargin(activity: ActHome): Int {
        val deadline = System.currentTimeMillis() + 5_000
        var last = currentTopMargin(activity)
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(150)
            val next = currentTopMargin(activity)
            if (next == last) return next
            last = next
        }
        return last
    }

    @Test
    fun topMarginDoesNotCompoundAcrossRepeatedConfigChanges() {
        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            var baseline = 0
            scenario.onActivity { activity ->
                baseline = waitForStableTopMargin(activity)
            }
            assert(baseline > 0) { "expected a real inset-derived top margin, got $baseline" }

            scenario.onActivity { activity ->
                activity.onConfigurationChanged(activity.resources.configuration)
                ViewCompat.requestApplyInsets(activity.findViewById(R.id.rootLayout))
            }
            var afterFirst = 0
            scenario.onActivity { activity -> afterFirst = waitForStableTopMargin(activity) }
            assertEquals(
                "top margin must not grow after 1 config change",
                baseline,
                afterFirst
            )

            scenario.onActivity { activity ->
                activity.onConfigurationChanged(activity.resources.configuration)
                ViewCompat.requestApplyInsets(activity.findViewById(R.id.rootLayout))
            }
            var afterSecond = 0
            scenario.onActivity { activity -> afterSecond = waitForStableTopMargin(activity) }
            assertEquals(
                "top margin must not grow after 2 config changes",
                baseline,
                afterSecond
            )
        }
    }
}
