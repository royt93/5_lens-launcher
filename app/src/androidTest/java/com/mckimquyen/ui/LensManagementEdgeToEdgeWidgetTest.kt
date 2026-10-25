package com.mckimquyen.ui

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-032 (owner: "chú ý lỗi edge to edge"): the lens manager targets SDK 37, where the system forces
 * edge-to-edge, yet it never consumed window insets. The collapsing toolbar then sat under the status
 * bar / camera cutout, and both the last list rows and the group action bar sat under the gesture bar.
 * These tests dispatch real WindowInsetsCompat values through the Activity's root view.
 */
@RunWith(AndroidJUnit4::class)
class LensManagementEdgeToEdgeWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    // The device's real lens rows and active lens, restored in tearDown (DeviceStateGuard does not cover Room).
    private var originalRoom: LensManagerRoomSnapshot? = null
    private var originalActiveLensId: String? = null

    @Before
    fun setUp() = runBlocking {
        AppDatabase.init(context)
        val dao = AppDatabase.getInstance().lensWorkspaceDao()
        originalRoom = LensManagerRoomSnapshot.capture(context)
        originalActiveLensId = UtilSettings(context).getString(UtilSettings.KEY_ACTIVE_LENS_ID)
        dao.deleteAll()
        dao.insertAll(listOf(LensWorkspace.createDefault(), LensWorkspace("work", "Work", 1, 20L)))
        UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, LensWorkspace.DEFAULT_LENS_ID)
    }

    @After
    fun tearDown() = runBlocking {
        originalRoom?.restore(context)
        val preferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        if (originalActiveLensId == null) check(preferences.edit().remove(UtilSettings.KEY_ACTIVE_LENS_ID).commit())
        else UtilSettings(context).save(UtilSettings.KEY_ACTIVE_LENS_ID, originalActiveLensId)
    }

    private fun dispatchInsets(activity: LensManagementActivity, bars: Insets) {
        val root = activity.findViewById<View>(R.id.lensManagementRoot)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), bars)
            .build()
        ViewCompat.dispatchApplyWindowInsets(root, insets)
    }

    private fun density(activity: LensManagementActivity) = activity.resources.displayMetrics.density

    @Test
    fun toolbarClearsTheStatusBarAndCutout() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                dispatchInsets(activity, Insets.of(0, TOP_INSET_PX, 0, 0))
                val appBar = activity.findViewById<View>(R.id.lensManagerAppBar)
                assertEquals("app bar must start below the status bar / cutout", TOP_INSET_PX, appBar.paddingTop)
            }
        }
    }

    @Test
    fun listAndGroupActionsClearTheNavigationBar() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val basePx = (BASE_LIST_BOTTOM_DP * density(activity)).toInt()
                dispatchInsets(activity, Insets.of(0, 0, 0, BOTTOM_INSET_PX))
                val list = activity.findViewById<View>(R.id.rvLensGroups)
                val actions = activity.findViewById<View>(R.id.lensGroupActions)
                assertTrue(
                    "last list row must scroll clear of the gesture bar (padding ${list.paddingBottom})",
                    list.paddingBottom >= BOTTOM_INSET_PX
                )
                assertTrue(
                    "group action bar must sit above the gesture bar (padding ${actions.paddingBottom})",
                    actions.paddingBottom >= BOTTOM_INSET_PX
                )
                assertTrue("list keeps its own breathing room on top of the inset", list.paddingBottom >= basePx)
            }
        }
    }

    @Test
    fun insetsAreNotAccumulatedWhenDispatchedRepeatedly() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                dispatchInsets(activity, Insets.of(0, TOP_INSET_PX, 0, BOTTOM_INSET_PX))
                val list = activity.findViewById<View>(R.id.rvLensGroups)
                val actions = activity.findViewById<View>(R.id.lensGroupActions)
                val firstList = list.paddingBottom
                val firstActions = actions.paddingBottom

                repeat(REPEAT_COUNT) { dispatchInsets(activity, Insets.of(0, TOP_INSET_PX, 0, BOTTOM_INSET_PX)) }

                assertEquals("list padding must not grow on every inset pass", firstList, list.paddingBottom)
                assertEquals("action bar padding must not grow on every inset pass", firstActions, actions.paddingBottom)
            }
        }
    }

    @Test
    fun sideInsetsAreRespectedInLandscapeWithCutout() {
        ActivityScenario.launch(LensManagementActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                dispatchInsets(activity, Insets.of(SIDE_INSET_PX, 0, SIDE_INSET_PX, 0))
                val root = activity.findViewById<View>(R.id.lensManagementRoot)
                assertEquals(SIDE_INSET_PX, root.paddingLeft)
                assertEquals(SIDE_INSET_PX, root.paddingRight)
            }
        }
    }

    private companion object {
        const val TOP_INSET_PX = 96
        const val BOTTOM_INSET_PX = 120
        const val SIDE_INSET_PX = 64
        const val BASE_LIST_BOTTOM_DP = 16
        const val REPEAT_COUNT = 5
    }
}
