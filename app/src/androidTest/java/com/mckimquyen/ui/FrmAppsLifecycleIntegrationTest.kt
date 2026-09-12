package com.mckimquyen.ui

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.adt.FragmentPagerAdapter
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.services.AppEventManager
import com.mckimquyen.util.BitmapCache
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests cho LEAK-001, tái hiện đúng đường đi thật trong production:
 * `ActSettings` host `FrmApps` bên trong `ViewPager2` dùng `FragmentStateAdapter`
 * (`FragmentPagerAdapter.kt`), nên MỖI LẦN người dùng chuyển sang tab khác,
 * `FrmApps.onDestroyView()` được hệ thống gọi thật — không phải giả lập qua
 * `FragmentScenario` cô lập như ở widget test. Đây là ranh giới Activity +
 * ViewPager2 + Fragment + `RAppsSingleton` mà story LEAK-001 nhắm tới.
 *
 * Chứng minh:
 * 1. Chuyển tab đi rồi quay lại Apps nhiều lần không crash, dữ liệu từ
 *    `RAppsSingleton` được nạp lại đúng mỗi lần `FrmApps` tạo view mới.
 * 2. Activity recreate() (xoay màn hình) khi đang ở tab Apps không crash,
 *    danh sách app vẫn hiển thị đúng sau khi tái tạo.
 */
@RunWith(AndroidJUnit4::class)
class FrmAppsLifecycleIntegrationTest {

    @Before
    fun setup() {
        BitmapCache.clear()
        val apps = ArrayList<App>().apply {
            repeat(5) { i -> add(App(packageName = "com.leak001.test.app$i", name = "App $i", icon = null)) }
        }
        RAppsSingleton.instance.apps = apps
    }

    @After
    fun tearDown() {
        BitmapCache.clear()
        RAppsSingleton.instance.clearAllData()
    }

    private fun currentRecyclerView(activity: ActSettings): RecyclerView? {
        // FragmentPagerAdapter attaches its pages directly to the Activity's
        // supportFragmentManager (it is built as `FragmentStateAdapter(fragmentActivity)`).
        val fragment = activity.supportFragmentManager.fragments
            .filterIsInstance<FrmApps>()
            .firstOrNull()
        return fragment?.view?.findViewById(R.id.rvApps)
    }

    private fun currentRecyclerItemCount(activity: ActSettings): Int =
        currentRecyclerView(activity)?.adapter?.itemCount ?: -1

    private fun currentRecyclerAppPackages(activity: ActSettings): List<String> {
        val adapter = currentRecyclerView(activity)?.adapter as? com.mckimquyen.adt.AppAdapter ?: return emptyList()
        return (0 until adapter.itemCount).map { adapter.getItemForPosition(it).packageName.toString() }
    }

    @Test
    fun testFrmApps_switchTabsRepeatedly_recreatesViewAndReloadsAppsEachTime() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            repeat(4) { cycle ->
                scenario.onActivity { activity ->
                    val viewPager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewpager)
                    viewPager.setCurrentItem(FragmentPagerAdapter.TAB_APPS, false)
                }
                instrumentation.waitForIdleSync()

                scenario.onActivity { activity ->
                    val count = currentRecyclerItemCount(activity)
                    assertEquals(
                        "Cycle $cycle: FrmApps must reload the 5 seeded apps from RAppsSingleton " +
                            "after its view is recreated by FragmentStateAdapter",
                        5,
                        count
                    )
                }

                // Navigate away — this is exactly what triggers FrmApps.onDestroyView()
                // in production (FragmentStateAdapter destroys off-screen page views).
                scenario.onActivity { activity ->
                    val viewPager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewpager)
                    viewPager.setCurrentItem(FragmentPagerAdapter.TAB_SETTINGS, false)
                }
                instrumentation.waitForIdleSync()
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun testFrmApps_activityRecreate_onAppsTab_doesNotCrashAndReloadsApps() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            scenario.onActivity { activity ->
                val viewPager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewpager)
                viewPager.setCurrentItem(FragmentPagerAdapter.TAB_APPS, false)
            }
            instrumentation.waitForIdleSync()

            try {
                scenario.recreate()
            } catch (e: Exception) {
                fail("Activity.recreate() while on the Apps tab must not crash: ${e.message}")
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                assertFalse("ActSettings must not be finishing after recreate", activity.isFinishing)
                val count = currentRecyclerItemCount(activity)
                assertEquals(
                    "After Activity.recreate(), FrmApps must still show the 5 seeded apps",
                    5,
                    count
                )
            }
        } finally {
            scenario.close()
        }
    }

    /**
     * NotifyDataSetChanged lint fix: this is the one path the tab-switch/recreate tests
     * above never exercise, because they always destroy and recreate `FrmApps`'s view
     * (and its `AppAdapter`) from scratch. Here the Apps tab stays alive and only the
     * data changes underneath it — the exact real-world trigger for `AppAdapter.updateApps`'s
     * new `DiffUtil` path (`ActSettings` observes `AppEventManager.appsLoaded` and forwards
     * to the already-attached `FrmApps` via `AppsInterface.onAppsUpdated`).
     */
    @Test
    fun testFrmApps_appsLoadedEvent_updatesExistingAdapterInPlaceWithCorrectContent() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            scenario.onActivity { activity ->
                val viewPager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewpager)
                viewPager.setCurrentItem(FragmentPagerAdapter.TAB_APPS, false)
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                assertEquals(5, currentRecyclerItemCount(activity))
                assertEquals(
                    (0 until 5).map { "com.leak001.test.app$it" },
                    currentRecyclerAppPackages(activity)
                )
            }

            // Change the data (remove app1/app3, add app5, reorder) without touching the view.
            val updatedApps = ArrayList<App>().apply {
                add(App(packageName = "com.leak001.test.app4", name = "App 4", icon = null))
                add(App(packageName = "com.leak001.test.app0", name = "App 0", icon = null))
                add(App(packageName = "com.leak001.test.app2", name = "App 2", icon = null))
                add(App(packageName = "com.leak001.test.app5", name = "App 5", icon = null))
            }
            RAppsSingleton.instance.apps = updatedApps
            AppEventManager.notifyAppsLoaded()
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                assertEquals(
                    "FrmApps's existing AppAdapter must reflect the new app set in the new order",
                    listOf(
                        "com.leak001.test.app4",
                        "com.leak001.test.app0",
                        "com.leak001.test.app2",
                        "com.leak001.test.app5"
                    ),
                    currentRecyclerAppPackages(activity)
                )
            }
        } finally {
            scenario.close()
        }
    }
}
