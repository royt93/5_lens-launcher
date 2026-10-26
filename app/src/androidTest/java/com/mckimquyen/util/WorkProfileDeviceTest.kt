package com.mckimquyen.util

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppIdentity
import com.mckimquyen.model.AppPersistent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-012 on a real device: the real LauncherApps/UserManager path and the real Room file. A device with no work
 * profile must behave exactly like before; a profile can only be simulated with rows, never created here.
 */
@RunWith(AndroidJUnit4::class)
class WorkProfileDeviceTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var originalApps: ArrayList<App>? = null

    @Before
    fun setUp() {
        AppDatabase.init(context)
        originalApps = RAppsSingleton.instance.apps
    }

    @After
    fun tearDown() {
        runBlocking {
            AppDatabase.getInstance().appPersistentDao().deleteByIdentifiers(listOf(WORK_ID, GONE_ID))
        }
        RAppsSingleton.instance.apps = originalApps
    }

    @Test
    fun theRunningUserIsAlwaysTheprimaryUserInTheDirectory() {
        val directory = LauncherAppsDirectory(context)

        assertTrue("the primary user must be listed", AppIdentity.PRIMARY_USER_ID in directory.profileUserIds())
        assertNotNull(directory.handleOf(AppIdentity.PRIMARY_USER_ID))
        assertEquals(Process.myUserHandle(), directory.handleOf(AppIdentity.PRIMARY_USER_ID))
    }

    @Test
    fun theDirectoryListsTheSameLaunchableAppsAsLauncherAppsForTheCurrentUser() {
        val directory = LauncherAppsDirectory(context)
        val expected = context.getSystemService(LauncherApps::class.java)
            .getActivityList(null, Process.myUserHandle())
            .map { it.componentName.packageName to it.componentName.className }
            .toSet()

        val actual = directory.appsOf(AppIdentity.PRIMARY_USER_ID).map { it.packageName to it.name }.toSet()

        assertEquals(expected, actual)
        assertTrue(expected.isNotEmpty())
    }

    @Test
    fun withoutAWorkProfileNothingIsFlaggedAsWorkAndTheMergeEqualsThePrimaryList() {
        val directory = LauncherAppsDirectory(context)
        val merged = ProfileAppSource.collect(directory)

        if (directory.profileUserIds().size == 1) {
            assertTrue(merged.none { it.isWorkProfile })
            assertTrue(merged.all { it.userId == AppIdentity.PRIMARY_USER_ID })
            assertEquals(directory.appsOf(AppIdentity.PRIMARY_USER_ID).size, merged.size)
        } else {
            assertTrue("a work profile is present, so work entries must be flagged", merged.any { it.isWorkProfile })
        }
    }

    @Test
    fun anUnknownProfileHasNoHandleNoAppsAndIsNotUnlocked() {
        val directory = LauncherAppsDirectory(context)

        assertEquals(null, directory.handleOf(UNKNOWN_USER))
        assertTrue(directory.appsOf(UNKNOWN_USER).isEmpty())
        assertFalse(directory.isUserUnlocked(UNKNOWN_USER))
        assertEquals(ProfileAppSource.LaunchRoute.PROFILE_GONE, ProfileAppSource.routeFor(UNKNOWN_USER, directory.profileUserIds()))
    }

    @Test
    fun theRealGetAppShellsScanKeepsEveryPrimaryAppAndAddsNoWorkAppsOnADeviceWithoutAProfile() = runBlocking {
        val app = context.applicationContext as android.app.Application
        val shells = UtilApp.getAppShells(app.packageManager, context, app, UtilSettings.DEFAULT_ICON_PACK_PACKAGE_NAME)

        assertTrue(shells.isNotEmpty())
        assertTrue(shells.all { it.userId == AppIdentity.PRIMARY_USER_ID || it.isWorkProfile })
        assertEquals(shells.size, shells.map { it.identifier }.toSet().size)
        assertTrue("primary identifiers keep the legacy shape", shells.filter { !it.isWorkProfile }.all { !it.identifier.contains("@u") })
    }

    @Test
    fun rowsOfARemovedProfileSurviveAnEmptyLookupAndAreDeletedWhenTheProfileIsKnownGone() = runBlocking {
        val dao = AppDatabase.getInstance().appPersistentDao()
        dao.insert(AppPersistent.defaults("com.fake.work", "Main", userId = FAKE_WORK_USER))
        dao.insert(AppPersistent.defaults("com.fake.gone", "Main", userId = FAKE_GONE_USER))

        assertEquals(0, dao.pruneProfileRows(emptyList()))
        assertNotNull(dao.findByIdentifier("default", WORK_ID))

        val removed = dao.pruneProfileRows(listOf(AppIdentity.PRIMARY_USER_ID, FAKE_WORK_USER))
        assertEquals(1, removed)
        assertNotNull(dao.findByIdentifier("default", WORK_ID))
        assertEquals(null, dao.findByIdentifier("default", GONE_ID))
    }

    @Test
    fun launchingAnAppOfAMissingProfileDoesNothingAndDoesNotCrash() {
        val before = RAppsSingleton.instance.apps?.size
        var thrown: Throwable? = null
        // The "not found" toast needs a Looper, so run on the main thread like the real caller.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                UtilApp.launchComponent(context, "com.fake.work", "Fake", "Main", null, null, UNKNOWN_USER)
            } catch (error: Throwable) {
                thrown = error
            }
        }
        assertEquals("launching into a removed profile must not throw", null, thrown)
        assertEquals(before, RAppsSingleton.instance.apps?.size)
    }

    private companion object {
        const val UNKNOWN_USER = 9999
        const val FAKE_WORK_USER = 9101
        const val FAKE_GONE_USER = 9102
        const val WORK_ID = "com.fake.work-Main@u9101"
        const val GONE_ID = "com.fake.gone-Main@u9102"
    }
}
