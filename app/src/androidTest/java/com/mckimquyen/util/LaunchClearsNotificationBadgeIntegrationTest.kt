package com.mckimquyen.util

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.ui.ActHome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchClearsNotificationBadgeIntegrationTest {

    @After
    fun tearDown() {
        // This test genuinely launches the real Settings app (a separate task) - leave the
        // device clean for whatever runs next.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("am force-stop com.android.settings").close()
    }

    @Test
    fun launchingAnAppWithAnExistingBadgeClearsItToZero() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppDatabase.init(context)
        // Real device's own Settings app as the launch target - always installed, always
        // resolvable, and (unlike targeting this app's own singleTask ActHome) a genuinely
        // separate task, so ActivityScenario<ActHome>'s own lifecycle isn't disturbed by the
        // launch this test triggers.
        val packageName = "com.android.settings"
        val name = "com.android.settings.Settings"
        AppPersistent.setNotificationCount(packageName, name, 7)
        RAppsSingleton.instance.apps = arrayListOf(
            App(label = "Test", packageName = packageName, name = name, notificationCount = 7)
        )

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                UtilApp.launchComponent(activity, packageName, "Test", name, null, null)
                // Checked inside the same main-thread callback, right after launchComponent's own
                // synchronous optimistic RAppsSingleton.updateAppState() call - not after the
                // waitForIdleSync()/sleep below, which gives a real, unrelated background
                // PackageManager rescan (RApplication's own startup scan, or one the real
                // Settings-launch/force-stop below can trigger via our package-change receiver)
                // enough time to land and wholesale-replace RAppsSingleton's app list first. Room
                // is unaffected by that replacement, so its check can safely wait for real persistence.
                assertEquals(0, RAppsSingleton.instance.findApp(packageName, name)?.notificationCount)
            }
            // AppPersistent.setNotificationCount(..., 0) persists on a background coroutine, not
            // synchronously on this thread - same wait shape as BadgeCountReceiverWidgetTest.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            android.os.SystemClock.sleep(250)

            val stored = AppDatabase.getInstance().appPersistentDao()
                .findByIdentifier(AppPersistent.generateIdentifier(packageName, name))
            assertEquals(0, stored?.notificationCount)
        }
    }

    /**
     * UI-024 wrap-up: a package can expose more than one launcher activity - `BadgeCountReceiver`
     * sets the same count on every `App` entry sharing a packageName (one badge per installed
     * app, not per activity), so opening only one entry point must clear every sibling entry too,
     * or the other one would keep showing a stale badge for an app the user just opened.
     */
    @Test
    fun launchingOneActivityOfAMultiActivityPackageClearsEverySiblingEntryToo() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppDatabase.init(context)
        val packageName = "com.android.settings"
        val mainName = "com.android.settings.Settings"
        val siblingName = "com.android.settings.SubSettings"
        AppPersistent.setNotificationCount(packageName, mainName, 7)
        AppPersistent.setNotificationCount(packageName, siblingName, 7)
        RAppsSingleton.instance.apps = arrayListOf(
            App(label = "Test", packageName = packageName, name = mainName, notificationCount = 7),
            App(label = "Test (alt)", packageName = packageName, name = siblingName, notificationCount = 7)
        )

        ActivityScenario.launch(ActHome::class.java).use { scenario ->
            scenario.onActivity { activity ->
                UtilApp.launchComponent(activity, packageName, "Test", mainName, null, null)
                // Same reasoning as the test above: assert the in-memory side effect inside this
                // same main-thread callback, before any real background rescan gets a chance to
                // land and replace the whole list (see that test's comment for the full story).
                assertEquals(0, RAppsSingleton.instance.findApp(packageName, mainName)?.notificationCount)
                assertEquals(
                    "the sibling launcher activity's badge must also clear, not just the launched one",
                    0,
                    RAppsSingleton.instance.findApp(packageName, siblingName)?.notificationCount
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            android.os.SystemClock.sleep(250)

            val storedSibling = AppDatabase.getInstance().appPersistentDao()
                .findByIdentifier(AppPersistent.generateIdentifier(packageName, siblingName))
            assertEquals(0, storedSibling?.notificationCount)
        }
    }
}
