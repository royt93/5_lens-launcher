package com.mckimquyen.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dependency audit (2026-09-27): every FISH-008 widget test in this package drives production
 * callbacks and real Dialog/View.performClick() directly instead of Espresso, because
 * espresso-core 3.6.1's event injector called the now-removed reflective
 * `InputManager.getInstance()` and threw `NoSuchMethodException` on TECNO KJ7 (API 37) - for
 * *any* `Espresso.onView(...)` call, not only ones that inject a touch. espresso-core 3.7.0
 * (per its official release notes) replaces that call with `getSystemService()`.
 *
 * This test passes here on TECNO BG6 (API 33), which was never affected by the removed API in
 * the first place - so a pass here does not by itself prove the API-37 crash is fixed. Treat it
 * as a live canary against a *regression* (a future downgrade breaking Espresso generally), not
 * as confirmation the original KJ7 defect is resolved; that requires re-running this on KJ7 (or
 * another real API 37+ device) once one is available.
 */
@RunWith(AndroidJUnit4::class)
class EspressoSanityCheckTest {

    @Test
    fun espressoCanClickARealViewOnThisDevice() {
        ActivityScenario.launch(ActHome::class.java).use {
            // Tapping the search bar opens the full-screen SearchView - the assertion after the
            // click proves the injected touch actually reached and was handled by the app, not
            // just that onView() resolved a matcher.
            onView(withId(R.id.searchBar))
                .check(matches(isDisplayed()))
                .perform(click())

            onView(withId(R.id.searchView)).check(matches(isDisplayed()))
        }
    }
}
