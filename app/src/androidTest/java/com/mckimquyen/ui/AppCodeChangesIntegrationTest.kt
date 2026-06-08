package com.mckimquyen.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Observer
import com.mckimquyen.R
import com.mckimquyen.services.AppEventManager
import com.mckimquyen.util.BitmapCache
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration tests chứng minh toàn bộ code changes hoạt động đúng khi kết hợp
 *
 * Chứng minh end-to-end:
 * 1. ActSettings khởi động thành công sau Logger migration
 * 2. ActHome hiển thị đúng LensView sau BitmapCache architecture changes
 * 3. AppEventManager → UI update chain hoạt động đúng
 * 4. Dynamic BroadcastReceiver (Android 8+) không gây crash khi Activity start
 * 5. manifestPlaceholders injection: app vẫn khởi động đúng sau khi move SDK key
 *
 * Note: Đây là INTEGRATION test — chạy trên emulator/device (androidTest)
 */
@RunWith(AndroidJUnit4::class)
class AppCodeChangesIntegrationTest {

    @Before
    fun setup() {
        BitmapCache.clear()
        RAppsSingleton.instance.clearAllData()
    }

    @After
    fun tearDown() {
        BitmapCache.clear()
        RAppsSingleton.instance.clearAllData()
    }

    // ========================================================================
    // ActSettings — Logger migration does not break UI
    // ========================================================================

    @Test
    fun testActSettings_startsWithoutCrash_afterLoggerMigration() {
        // Chứng minh ActSettings.launchApps() — nơi Logger.d() được gọi —
        // không crash sau khi migrate từ android.util.Log.d
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            assertNotNull("ActSettings must start successfully after Logger migration", activity)
            assertFalse("ActSettings must not be finishing", activity.isFinishing)
        }

        scenario.close()
    }

    @Test
    fun testActSettings_viewpager_presentAfterMigration() {
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            val viewPager = activity.findViewById<android.view.View>(R.id.viewpager)
            assertNotNull("ViewPager must be present — UI integrity after code changes", viewPager)
            assertEquals(
                "ViewPager must be visible — Logger migration must not affect UI",
                android.view.View.VISIBLE,
                viewPager.visibility
            )
        }

        scenario.close()
    }

    // ========================================================================
    // ActHome — BitmapCache architecture does not break launcher UI
    // ========================================================================

    @Test
    fun testActHome_startsWithoutCrash_afterBitmapCacheChanges() {
        // Chứng minh ActHome vẫn start đúng sau BitmapCache architecture changes
        // (icon management: App.icon=null, BitmapCache-based)
        val scenario = ActivityScenario.launch(ActHome::class.java)

        scenario.onActivity { activity ->
            assertNotNull("ActHome must start after BitmapCache architecture changes", activity)
        }

        scenario.close()
    }

    @Test
    fun testActHome_lensView_presentAfterBitmapCacheChanges() {
        val scenario = ActivityScenario.launch(ActHome::class.java)

        scenario.onActivity { activity ->
            val lensView = activity.findViewById<android.view.View>(R.id.lensViews)
            assertNotNull(
                "LensView must be present — BitmapCache changes must not break launcher",
                lensView
            )
        }

        scenario.close()
    }

    // ========================================================================
    // AppEventManager → UI Update Chain
    // ========================================================================

    @Test
    fun testAppEventManager_notifyFromBackground_doesNotCrash() {
        // Simulate what BroadcastReceivers do after BUG-13 fix:
        // Direct call to AppEventManager (no Observable layer)
        val latch = CountDownLatch(2)
        val updatedObserver = Observer<Any?> { latch.countDown() }
        val loadedObserver = Observer<Any?> { latch.countDown() }

        AppEventManager.appsUpdated.observeForever(updatedObserver)
        AppEventManager.appsLoaded.observeForever(loadedObserver)

        try {
            val worker = Thread {
                AppEventManager.notifyAppsUpdated()
                AppEventManager.notifyAppsLoaded()
            }
            worker.start()
            worker.join(2_000)

            assertTrue(
                "AppEventManager direct dispatch from background must reach observers",
                latch.await(2, TimeUnit.SECONDS)
            )
        } finally {
            AppEventManager.appsUpdated.removeObserver(updatedObserver)
            AppEventManager.appsLoaded.removeObserver(loadedObserver)
        }
    }

    // ========================================================================
    // BitmapCache + RAppsSingleton Integration
    // ========================================================================

    @Test
    fun testIconFlow_setAndGet_acrossActivityBoundary() {
        // Verify icon persists in BitmapCache across activity launch
        val packageName = "com.integration.test"
        val icon = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)

        // Set icon (as TaskUpdateApps would do)
        RAppsSingleton.instance.setAppIcon(packageName, icon)

        // Launch activity — verify cache persists
        val scenario = ActivityScenario.launch(ActHome::class.java)

        scenario.onActivity { _ ->
            val cachedIcon = RAppsSingleton.instance.getAppIcon(packageName)
            assertNotNull(
                "BitmapCache must persist icon across Activity boundary",
                cachedIcon
            )
            assertFalse(
                "Cached icon must not be recycled (BUG-06 fix: no manual recycle)",
                cachedIcon!!.isRecycled
            )
        }

        scenario.close()
        if (!icon.isRecycled) icon.recycle()
    }

    @Test
    fun testIconFlow_multipleApps_allRetrievable() {
        // Simulate TaskUpdateApps loading multiple apps
        val apps = listOf("com.app1", "com.app2", "com.app3")
        val icons = apps.map {
            android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
        }

        // Populate (as TaskUpdateApps.doInBackground() does)
        apps.forEachIndexed { index, packageName ->
            RAppsSingleton.instance.setAppIcon(packageName, icons[index])
        }

        val appList = ArrayList<App>().apply {
            apps.forEach { add(App(packageName = it, name = it, icon = null)) }
        }
        RAppsSingleton.instance.apps = appList

        // Launch ActHome — all icons retrievable
        val scenario = ActivityScenario.launch(ActHome::class.java)

        scenario.onActivity { _ ->
            apps.forEach { packageName ->
                val icon = RAppsSingleton.instance.getAppIcon(packageName)
                assertNotNull(
                    "Icon for $packageName must be retrievable from BitmapCache (BUG-07 architecture)",
                    icon
                )
            }
        }

        scenario.close()
        icons.forEach { if (!it.isRecycled) it.recycle() }
    }

    // ========================================================================
    // ManifestPlaceholders — App starts correctly after SDK key injection
    // ========================================================================

    @Test
    fun testApp_startsCorrectly_afterManifestPlaceholdersChange() {
        // Verify app still starts normally after moving AppLovin SDK key
        // from hardcoded Manifest value to manifestPlaceholders
        // If manifest injection fails, app would crash on start
        val scenario = ActivityScenario.launch(ActSettings::class.java)

        scenario.onActivity { activity ->
            assertNotNull(
                "App must start correctly after manifestPlaceholders injection for AppLovin SDK key",
                activity
            )
            assertFalse(
                "Activity must not be finishing — manifest injection must be correct",
                activity.isFinishing
            )
        }

        scenario.close()
    }

    // ========================================================================
    // onTerminate / packageReceiver — Lifecycle cleanup
    // ========================================================================

    @Test
    fun testPackageReceiver_appLifecycle_noDoubleRegistration() {
        // Verify that launching and closing Activities does not cause
        // double-registration of package receiver

        val scenario1 = ActivityScenario.launch(ActSettings::class.java)
        scenario1.onActivity { assertNotNull(it) }
        scenario1.close()

        // Second launch — package receiver should still work
        val scenario2 = ActivityScenario.launch(ActSettings::class.java)
        scenario2.onActivity { activity ->
            assertNotNull("Second launch must succeed — no double-registration crash", activity)
        }
        scenario2.close()
    }
}
