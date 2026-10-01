package com.mckimquyen.services

import android.content.Intent
import androidx.lifecycle.Observer
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-024: no Espresso needed - a BroadcastReceiver's onReceive() is called directly (the exact
 * same real production method a real broadcast dispatch would invoke), matching this project's
 * established convention of calling production handler methods directly where the test device
 * cannot build Espresso's event injector (API 37 removed InputManager.getInstance).
 */
@RunWith(AndroidJUnit4::class)
class BadgeCountReceiverWidgetTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val receiver = BadgeCountReceiver()

    @Before
    fun setUp() = runBlocking {
        AppDatabase.init(context)
        RAppsSingleton.instance.apps = ArrayList()
    }

    @After
    fun tearDown() = runBlocking {
        RAppsSingleton.instance.apps = ArrayList()
    }

    @Test
    fun broadcastForAnInstalledAppWritesTheClampedCountAndFiresAppsEdited() = runBlocking {
        val app = App(label = "Camera", packageName = "pkg.camera", name = "CameraActivity")
        RAppsSingleton.instance.apps = arrayListOf(app)
        var eventFired = false
        val observer = Observer<Any?> { eventFired = true }
        // observeForever/removeObserver are main-thread-only LiveData APIs; this test body runs
        // on the instrumentation test thread under runBlocking, not the main thread.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppEventManager.appsEdited.observeForever(observer)
        }

        val intent = Intent(BadgeCountReceiver.ACTION_BADGE_COUNT_UPDATE).apply {
            putExtra(BadgeCountReceiver.EXTRA_PACKAGE_NAME, "pkg.camera")
            putExtra(BadgeCountReceiver.EXTRA_COUNT, 5)
        }
        receiver.onReceive(context, intent)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(250)

        assertEquals(5, RAppsSingleton.instance.findApp("pkg.camera", "CameraActivity")?.notificationCount)
        val stored = AppDatabase.getInstance().appPersistentDao()
            .findByIdentifier(AppPersistent.generateIdentifier("pkg.camera", "CameraActivity"))
        assertEquals(5, stored?.notificationCount)
        assertEquals(true, eventFired)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppEventManager.appsEdited.removeObserver(observer)
        }
    }

    @Test
    fun broadcastForAPackageWithTwoLauncherActivitiesUpdatesBothEntries() = runBlocking {
        val main = App(label = "Suite", packageName = "pkg.suite", name = "MainActivity")
        val secondary = App(label = "Suite (alt)", packageName = "pkg.suite", name = "SecondActivity")
        RAppsSingleton.instance.apps = arrayListOf(main, secondary)

        val intent = Intent(BadgeCountReceiver.ACTION_BADGE_COUNT_UPDATE).apply {
            putExtra(BadgeCountReceiver.EXTRA_PACKAGE_NAME, "pkg.suite")
            putExtra(BadgeCountReceiver.EXTRA_COUNT, 7)
        }
        receiver.onReceive(context, intent)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(250)

        assertEquals(7, RAppsSingleton.instance.findApp("pkg.suite", "MainActivity")?.notificationCount)
        assertEquals(7, RAppsSingleton.instance.findApp("pkg.suite", "SecondActivity")?.notificationCount)
    }

    @Test
    fun broadcastForAPackageNotInTheSnapshotIsDropped() = runBlocking {
        RAppsSingleton.instance.apps = ArrayList() // nothing installed per the snapshot

        val intent = Intent(BadgeCountReceiver.ACTION_BADGE_COUNT_UPDATE).apply {
            putExtra(BadgeCountReceiver.EXTRA_PACKAGE_NAME, "pkg.unknown")
            putExtra(BadgeCountReceiver.EXTRA_COUNT, 5)
        }
        receiver.onReceive(context, intent)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(250)

        val anyRowForUnknownPackage = AppDatabase.getInstance().appPersistentDao().getAll()
            .any { it.packageName == "pkg.unknown" }
        assertTrue("no row should ever be created for a package outside the snapshot", !anyRowForUnknownPackage)
    }
}
