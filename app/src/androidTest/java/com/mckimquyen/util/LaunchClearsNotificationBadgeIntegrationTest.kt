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
            }
            // AppPersistent.setNotificationCount(..., 0) persists on a background coroutine, not
            // synchronously on this thread - same wait shape as BadgeCountReceiverWidgetTest.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            android.os.SystemClock.sleep(250)

            assertEquals(0, RAppsSingleton.instance.findApp(packageName, name)?.notificationCount)
            val stored = AppDatabase.getInstance().appPersistentDao()
                .findByIdentifier(AppPersistent.generateIdentifier(packageName, name))
            assertEquals(0, stored?.notificationCount)
        }
    }
}
