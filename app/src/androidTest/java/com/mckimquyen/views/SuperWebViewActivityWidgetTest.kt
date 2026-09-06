package com.mckimquyen.views

import android.content.Intent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SEC-003 widget/UI proof that hardening the allowlist/scheme checks did not regress
 * SuperWebViewActivity's visible behavior, titles or back-navigation lifecycle wiring.
 *
 * Note: uses ActivityScenario.onActivity {} + direct view state instead of Espresso onView(),
 * because this module's androidx.test:monitor is pinned to 1.6.0 by fragment-testing:1.8.6's
 * dependency constraints (androidx.test:monitor:{strictly 1.6.0}), which predates the
 * androidx.test.platform.concurrent.DirectExecutor class espresso-core:3.6.1 requires. That is a
 * pre-existing, project-wide dependency conflict unrelated to SEC-003 (no test in this repo used
 * Espresso onView()/check()/perform() before now); fixing it is out of scope for this story.
 */
@RunWith(AndroidJUnit4::class)
class SuperWebViewActivityWidgetTest {

    private val allowedUrl = "https://loitp.notion.site/loitp/Privacy-Policy-319b1cd8783942fa8923d2a3c9bce60f/"

    private fun launch(url: String, title: String? = "Privacy Policy"): ActivityScenario<SuperWebViewActivity> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, SuperWebViewActivity::class.java).apply {
            putExtra(SuperWebViewActivity.KEY_URL, url)
            if (title != null) putExtra(SuperWebViewActivity.KEY_TITLE, title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return ActivityScenario.launch(intent)
    }

    @Test
    fun toolbarShowsRequestedTitle_forAllowlistedUrl() {
        launch(allowedUrl, title = "Privacy Policy").use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertEquals("Privacy Policy", activity.supportActionBar?.title)
            }
        }
    }

    @Test
    fun toolbarTitleIsBlank_whenNoTitleExtraProvided() {
        launch(allowedUrl, title = null).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("", activity.supportActionBar?.title)
            }
        }
    }

    @Test
    fun toolbarStaysVisible_forAllowlistedUrl() {
        // Toolbar visibility is static (not tied to the page load outcome), unlike
        // webView/errorLayout which race against the real network call below.
        launch(allowedUrl).use { scenario ->
            scenario.onActivity { activity ->
                val toolbar = activity.findViewById<View>(R.id.toolbar)
                assertEquals(View.VISIBLE, toolbar.visibility)
            }
        }
    }

    /**
     * The allowlisted page load is a real network call, so it may finish (webView visible)
     * or fail (errorLayout visible) depending on the test device's connectivity - both are a
     * valid, crash-free outcome. What must always hold is the mutual-exclusion invariant coded
     * in MyWebViewClient: exactly one of webView/errorLayout is shown, never both, never neither.
     */
    @Test
    fun webViewAndErrorLayout_reachMutuallyExclusiveTerminalState() {
        launch(allowedUrl).use { scenario ->
            val deadline = System.currentTimeMillis() + 8_000
            var webViewVisible = false
            var errorVisible = false
            while (System.currentTimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    webViewVisible = activity.findViewById<View>(R.id.webView).visibility == View.VISIBLE
                    errorVisible = activity.findViewById<View>(R.id.errorLayout).visibility == View.VISIBLE
                }
                if (webViewVisible != errorVisible) break
                Thread.sleep(200)
            }
            assertTrue(
                "expected exactly one of webView/errorLayout visible, got webView=$webViewVisible errorLayout=$errorVisible",
                webViewVisible != errorVisible
            )
        }
    }

    @Test
    fun systemBackFinishesActivity_whenWebViewHasNoNavigationHistory() {
        // finish() only guarantees isFinishing=true synchronously; the OS tears the activity
        // down to DESTROYED on its own schedule, so that is what this test proves, not DESTROYED.
        launch(allowedUrl).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
                assertTrue(
                    "activity must be finishing after back press with no WebView history",
                    activity.isFinishing
                )
            }
        }
    }
}
