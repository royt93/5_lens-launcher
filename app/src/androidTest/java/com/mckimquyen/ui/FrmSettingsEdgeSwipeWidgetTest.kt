package com.mckimquyen.ui

import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.EdgeSwipeMode
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrmSettingsEdgeSwipeWidgetTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)
    private val settings get() = UtilSettings(context)
    private var originalMode: String? = null
    private var hadStoredMode = false

    @Before
    fun setUp() {
        val prefs = rawPrefs()
        hadStoredMode = prefs.contains(UtilSettings.KEY_EDGE_SWIPE_LENS_SWITCH)
        originalMode = prefs.getString(UtilSettings.KEY_EDGE_SWIPE_LENS_SWITCH, null)
        prefs.edit().remove(UtilSettings.KEY_EDGE_SWIPE_LENS_SWITCH).commit()
    }

    @After
    fun tearDown() {
        val prefs = rawPrefs()
        prefs.edit().apply {
            if (hadStoredMode) {
                putString(UtilSettings.KEY_EDGE_SWIPE_LENS_SWITCH, originalMode)
            } else {
                remove(UtilSettings.KEY_EDGE_SWIPE_LENS_SWITCH)
            }
        }.commit()
    }

    @Test
    fun defaultSummaryReflectsBothAndClickOpensChoiceDialog() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val tvSummary = fragment.requireView().findViewById<TextView>(R.id.tvSelectedEdgeSwipeMode)
                assertNotNull("tvSelectedEdgeSwipeMode must exist in layout", tvSummary)
                assertEquals(context.getString(R.string.edge_swipe_mode_both), tvSummary.text.toString())

                fragment.requireView().findViewById<android.view.View>(R.id.llEdgeSwipeMode).performClick()

                val dialog = fragment.edgeSwipeDialogForTest
                assertNotNull("Clicking row must show single-choice dialog", dialog)

                // Select index 2: RIGHT_ONLY
                dialog!!.listView.performItemClick(null, 2, 0)
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(EdgeSwipeMode.RIGHT_ONLY, settings.getEdgeSwipeMode())

            scenario.onFragment { fragment ->
                val tvSummary = fragment.requireView().findViewById<TextView>(R.id.tvSelectedEdgeSwipeMode)
                assertEquals(context.getString(R.string.edge_swipe_mode_right), tvSummary.text.toString())
            }
        }
    }
}
