package com.mckimquyen.model

import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppPersistentMainThreadTest {
    @Test
    fun publicPersistenceApiDoesNotPerformDiskIoOnMainThread() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val previousPolicy = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder(previousPolicy)
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyDeath()
                    .build()
            )
            try {
                val packageName = "com.example.strictmode"
                val componentName = "MainActivity"
                RAppsSingleton.instance.apps = arrayListOf(
                    App(label = "Strict", packageName = packageName, name = componentName)
                )
                AppPersistent.getAppOpened(packageName, componentName)
                AppPersistent.getAppVisibility(packageName, componentName)
                AppPersistent.getAppOpenCount(packageName, componentName)
                AppPersistent.getAppPaletteColor(packageName, componentName)
                AppPersistent.setAppOpened(packageName, componentName, false)
                AppPersistent.setAppVisibility(packageName, componentName, false)
                AppPersistent.setAppPaletteColor(packageName, componentName, 123)
                AppPersistent.incrementAppCount(packageName, componentName)
                AppPersistent.setAppOrderBatch(RAppsSingleton.instance.apps.orEmpty())
                AppPersistent.setOrganization(
                    packageName,
                    componentName,
                    favorite = true,
                    folderName = "Work",
                    pinnedZone = PinnedZone.START
                )
            } finally {
                RAppsSingleton.instance.clearAllData()
                StrictMode.setThreadPolicy(previousPolicy)
            }
        }
    }

    @Test
    fun rapidOrganizationWritesPersistOnlyTheNewestState() = runBlocking {
        val packageName = "com.example.latestorganization"
        val componentName = "MainActivity"
        val identifier = AppPersistent.generateIdentifier(packageName, componentName)
        val dao = AppDatabase.getInstance().appPersistentDao()

        AppPersistent.setOrganization(packageName, componentName, true, "Old", PinnedZone.START)
        AppPersistent.setOrganization(packageName, componentName, false, "Latest", PinnedZone.END)

        val stored = withTimeout(5_000) {
            var current: AppPersistent?
            do {
                current = dao.findByIdentifier(identifier)
                if (current?.folderName != "Latest") delay(10)
            } while (current?.folderName != "Latest")
            current
        }!!
        assertFalse(stored.isFavorite)
        assertEquals("Latest", stored.folderName)
        assertEquals(PinnedZone.END.name, stored.pinnedZone)
        dao.delete(stored)
    }
}
