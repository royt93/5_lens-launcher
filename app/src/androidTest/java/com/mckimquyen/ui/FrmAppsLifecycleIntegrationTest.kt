package com.mckimquyen.ui

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.adt.FragmentPagerAdapter
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
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

    private fun currentRecyclerItemCount(activity: ActSettings): Int {
        // FragmentPagerAdapter attaches its pages directly to the Activity's
        // supportFragmentManager (it is built as `FragmentStateAdapter(fragmentActivity)`).
        val fragment = activity.supportFragmentManager.fragments
            .filterIsInstance<FrmApps>()
            .firstOrNull()
        val rv = fragment?.view?.findViewById<RecyclerView>(R.id.rvApps)
        return rv?.adapter?.itemCount ?: -1
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
}
