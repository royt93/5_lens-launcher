package com.mckimquyen.ui

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.adt.FragmentPagerAdapter
import com.mckimquyen.app.RApplication
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Lens and Apps tabs of Settings on whatever display the device is in (the Settings tab is measured by [ActSettingsDisplaySizesTest]):
 * can the last element be scrolled to a position inside the viewport and above the system bar. Geometry is logged with tag F265TABS so
 * the profiles can be compared. Same first-launch seeding and the same run scripts as the Settings tab test (rotation and the closing of a
 * test ad with KEYCODE_BACK are done by `run-display-tests-adguard.sh`, never by this test).
 */
@RunWith(AndroidJUnit4::class)
class ActSettingsLensAppsTabsDisplaySizesTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private companion object {
        const val TAG = "F265TABS"
        const val EXPECT_ARG = "displayExpectOrientation"
        const val LANGUAGE_SELECTED_KEY = "Locale.Helper.Language.Selected"
        const val SETTLE_MS = 900L
        const val WAIT_MS = 8_000L
        const val POLL_MS = 50L
        const val BAR_TOLERANCE_PX = 1
        const val FAKE_APP_COUNT = 60
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before fun seedFirstLaunchKeys() {
        (context.applicationContext as RApplication).appRefreshPipeline.cancel()
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(UtilSettings.KEY_READ_POLICY, true).putBoolean(LANGUAGE_SELECTED_KEY, true).commit()
    }

    private fun checkOrientation(scenario: ActivityScenario<ActSettings>) {
        var portrait = true
        var dims = ""
        scenario.onActivity {
            val dm = it.resources.displayMetrics
            portrait = dm.heightPixels >= dm.widthPixels
            dims = "${dm.widthPixels}x${dm.heightPixels}"
        }
        val expected = InstrumentationRegistry.getArguments().getString(EXPECT_ARG)
        Log.i(TAG, "orientation check expected=$expected activity=$dims portrait=$portrait")
        if (expected != null) assertEquals("the Activity is not in the orientation this run asked for (rotate from the script first)", expected, if (portrait) "portrait" else "landscape")
    }

    private inline fun <reified F : androidx.fragment.app.Fragment> showTab(scenario: ActivityScenario<ActSettings>, tab: Int, crossinline ready: (F) -> Boolean) {
        scenario.onActivity { it.viewpager.setCurrentItem(tab, false) }
        val deadline = SystemClock.uptimeMillis() + WAIT_MS
        var ok = false
        while (!ok && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.fragments.filterIsInstance<F>().firstOrNull()
                ok = fragment?.view?.let { it.isAttachedToWindow && it.height > 0 && ready(fragment) } ?: false
            }
            if (!ok) Thread.sleep(POLL_MS)
        }
        assertTrue("the tab must be laid out", ok)
        SystemClock.sleep(SETTLE_MS)
        instrumentation.waitForIdleSync()
    }

    private fun loc(v: View) = IntArray(2).also { v.getLocationOnScreen(it) }[1]

    @Test fun theLensTabEndsAboveTheSystemBar() {
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            checkOrientation(scenario)
            showTab<FrmLens>(scenario, FragmentPagerAdapter.TAB_LENS) { it.view?.findViewById<View>(R.id.tvLensTiltParallaxDesc) != null }
            scenario.onActivity { activity ->
                val dm = activity.resources.displayMetrics
                val fragment = activity.supportFragmentManager.fragments.filterIsInstance<FrmLens>().first()
                val scroll = fragment.view as NestedScrollView
                val last = scroll.findViewById<View>(R.id.tvLensTiltParallaxDesc)
                assertNotNull("positive control: the last element exists", last)
                val frame = Rect().also { activity.window.decorView.getWindowVisibleDisplayFrame(it) }
                val content = scroll.getChildAt(0)
                scroll.scrollTo(0, content.height)
                val scrollTop = loc(scroll)
                val scrollBottom = scrollTop + scroll.height
                val lastTop = loc(last)
                val lastBottom = lastTop + last.height
                Log.i(TAG, "LENS display ${dm.widthPixels}x${dm.heightPixels} @${dm.densityDpi}dpi frameBottom=${frame.bottom} scroll=${scroll.width}x${scroll.height} " +
                    "content=${content.height}px (${"%.0f".format(content.height / dm.density)}dp) scrollMax=${content.height - scroll.height} scrollTop=$scrollTop scrollBottom=$scrollBottom " +
                    "lastTop=$lastTop lastBottom=$lastBottom gapToFrame=${"%.0f".format((frame.bottom - lastBottom) / dm.density)}dp overlayVisible=${activity.findViewById<View>(R.id.flAdOpenApp)?.visibility == View.VISIBLE}")
                assertTrue("the last element of the Lens tab (bottom $lastBottom) must end above the system bar (${frame.bottom})", lastBottom <= frame.bottom + BAR_TOLERANCE_PX)
                assertTrue("the last element must be inside the scroll viewport: top=$lastTop viewport=[$scrollTop,$scrollBottom]", lastTop >= scrollTop - BAR_TOLERANCE_PX && lastBottom <= scrollBottom + BAR_TOLERANCE_PX)
                scroll.scrollTo(0, 0)
            }
        }
    }

    @Test fun theAppsTabLastRowEndsAboveTheSystemBar() {
        val original = RAppsSingleton.instance.apps
        try {
            RAppsSingleton.instance.apps = ArrayList((0 until FAKE_APP_COUNT).map { App(label = "App$it", packageName = "com.tabs.a$it", name = "Main", isVisible = true) })
            ActivityScenario.launch(ActSettings::class.java).use { scenario ->
                checkOrientation(scenario)
                showTab<FrmApps>(scenario, FragmentPagerAdapter.TAB_APPS) { f ->
                    val rv = f.view?.findViewById<RecyclerView>(R.id.rvApps)
                    rv != null && rv.visibility == View.VISIBLE && (rv.adapter?.itemCount ?: 0) > 0
                }
                var count = 0
                scenario.onActivity { activity ->
                    val rv = activity.supportFragmentManager.fragments.filterIsInstance<FrmApps>().first().view!!.findViewById<RecyclerView>(R.id.rvApps)
                    count = rv.adapter?.itemCount ?: 0
                    assertTrue("positive control: the Apps tab lists the $FAKE_APP_COUNT fake apps, had $count", count >= FAKE_APP_COUNT)
                    rv.scrollToPosition(count - 1)
                }
                // waitForIdleSync and sleep must not run on the main thread, so the scroll above and the measurement below are two calls.
                instrumentation.waitForIdleSync()
                SystemClock.sleep(SETTLE_MS)
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val dm = activity.resources.displayMetrics
                    val fragment = activity.supportFragmentManager.fragments.filterIsInstance<FrmApps>().first()
                    val rv = fragment.view!!.findViewById<RecyclerView>(R.id.rvApps)
                    val frame = Rect().also { activity.window.decorView.getWindowVisibleDisplayFrame(it) }
                    val lastView = rv.findViewHolderForAdapterPosition(count - 1)?.itemView
                    assertNotNull("the last row must be laid out after scrolling to it", lastView)
                    val rvTop = loc(rv)
                    val rvBottom = rvTop + rv.height
                    val lastTop = loc(lastView!!)
                    val lastBottom = lastTop + lastView.height
                    Log.i(TAG, "APPS display ${dm.widthPixels}x${dm.heightPixels} @${dm.densityDpi}dpi frameBottom=${frame.bottom} rv=${rv.width}x${rv.height} items=$count " +
                        "paddingBottom=${rv.paddingBottom} rvTop=$rvTop rvBottom=$rvBottom lastTop=$lastTop lastBottom=$lastBottom " +
                        "gapToFrame=${"%.0f".format((frame.bottom - lastBottom) / dm.density)}dp canScrollDown=${rv.canScrollVertically(1)}")
                    assertTrue("the last row of the Apps tab (bottom $lastBottom) must end above the system bar (${frame.bottom})", lastBottom <= frame.bottom + BAR_TOLERANCE_PX)
                    assertTrue("the last row must be inside the list viewport: top=$lastTop viewport=[$rvTop,$rvBottom]", lastTop >= rvTop - BAR_TOLERANCE_PX && lastBottom <= rvBottom + BAR_TOLERANCE_PX)
                }
            }
        } finally {
            RAppsSingleton.instance.apps = original
        }
    }
}
