package com.mckimquyen.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FISH-012: a pinch adjustment made on a lens that was NOT the active one when the process died
 * must still resurrect correctly once the user swipes to that lens - this is the scenario
 * ActHome.maybeShowResurrectSnackbar's onPageSelected call site exists for (the lens's own
 * pending value is restored silently the moment its page is first bound, by bindLensView, but
 * the confirmation Snackbar itself only reappears once the user actually swipes onto that page).
 */
@RunWith(AndroidJUnit4::class)
class ActHomePinchPersistenceIntegrationTest {

    private val dao get() = AppDatabase.getInstance().lensWorkspaceDao()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var utilSettings: UtilSettings

    private val secondLensId = "second-lens-pinch-test"

    @Before
    fun setup() {
        utilSettings = UtilSettings(context)
        cleanDb()
        // Realistic pre-kill state: a session that had already been using the default lens (so
        // KEY_ACTIVE_LENS_ID is persisted) before the pinch-and-kill happened on the second lens
        // - not a genuinely brand-new install, which has its own separate (pre-existing,
        // out-of-scope-for-FISH-012) ambiguity in which page bindLensView treats as "the active
        // one" when no active lens has ever been recorded yet.
        utilSettings.save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
    }

    @After
    fun tearDown() {
        utilSettings.clearPendingDistortionFactor(secondLensId)
        utilSettings.deleteLensSettings(secondLensId)
        // The swipe in this test's own body persists KEY_ACTIVE_LENS_ID = secondLensId as real
        // production behavior - reset it back so that leak doesn't corrupt any other test class
        // sharing this device's on-disk SharedPreferences/Room DB in a later, separate
        // `am instrument` invocation (found live: it broke ActHomePinchWidgetTest's pre-existing
        // saveAsDefaultAction_persistsValueAndCommitsLiveDistortion in exactly this way).
        utilSettings.save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
        cleanDb()
    }

    private fun cleanDb(): Unit = runBlocking {
        AppDatabase.init(context)
        dao.getAll().filter { it.id != LensWorkspace.DEFAULT_LENS_ID }.forEach { dao.delete(it) }
        dao.insertIfAbsent(LensWorkspace.createDefault())
        dao.insertOrUpdate(LensWorkspace(id = secondLensId, name = "Second Lens", orderIndex = 1))
        Unit
    }

    private fun idle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(300)
    }

    @Test
    fun pendingValueOnANonActiveLens_resurrectsSilentlyThenShowsSnackbarOnceSwipedTo() {
        utilSettings.savePendingDistortionFactor(secondLensId, 4.6f)

        val scenario = ActivityScenario.launch(ActHome::class.java)
        idle()

        // The default (active) lens page must NOT have popped a Snackbar for a value that
        // belongs to a different, not-yet-visible lens.
        scenario.onActivity { activity ->
            assertNull(
                "the active page must not show a resurrect Snackbar for another lens's pending value",
                activity.pinchCurvatureSnackbar
            )
        }

        scenario.onActivity { activity ->
            activity.findViewById<ViewPager2>(R.id.lensPager).setCurrentItem(1, false)
        }
        idle()

        scenario.onActivity { activity ->
            assertEquals(
                "swiping to the lens with a pending value must restore it into that page's live state",
                4.6f,
                activity.lensViews.liveDistortionFactor!!,
                0.001f
            )
            val snackbar = activity.pinchCurvatureSnackbar
            assertNotNull("swiping to the pending lens must now show its resurrect Snackbar", snackbar)
            assertTrue(snackbar?.isShown == true || snackbar?.isShownOrQueued == true)
        }

        scenario.close()
    }
}
