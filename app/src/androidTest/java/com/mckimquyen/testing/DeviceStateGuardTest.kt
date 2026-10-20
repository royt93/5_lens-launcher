package com.mckimquyen.testing

import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** TEST-005: the guard restores added, changed and removed keys, the app list, and it does so after a failing test. */
@RunWith(AndroidJUnit4::class)
class DeviceStateGuardTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = PreferenceManager.getDefaultSharedPreferences(context)

    init { com.mckimquyen.model.AppDatabase.init(InstrumentationRegistry.getInstrumentation().targetContext) }

    private val sentinelAdded = "t005_added_key"
    private val sentinelChanged = "t005_changed_key"
    private val sentinelRemoved = "t005_removed_key"

    /** Runs [body] under a fresh guard, the way a test class would, and always runs the guard's cleanup. */
    private fun underGuard(body: () -> Unit) {
        val guard = DeviceStateGuard()
        guard.apply(object : org.junit.runners.model.Statement() {
            override fun evaluate() = body()
        }, org.junit.runner.Description.createTestDescription(javaClass, "x")).evaluate()
    }

    @Test
    fun addedChangedAndRemovedKeysAreAllRestored_andAbsentStaysAbsent() {
        check(prefs.edit().putString(sentinelChanged, "before").putBoolean(sentinelRemoved, true).commit())
        try {
            underGuard {
                prefs.edit().putString(sentinelAdded, "x").putString(sentinelChanged, "after").remove(sentinelRemoved).commit()
            }
            assertFalse("a key the test added must be removed again", prefs.contains(sentinelAdded))
            assertEquals("a changed key must get its old value back", "before", prefs.getString(sentinelChanged, null))
            assertEquals("a removed key must come back", true, prefs.getBoolean(sentinelRemoved, false))
        } finally {
            prefs.edit().remove(sentinelAdded).remove(sentinelChanged).remove(sentinelRemoved).commit()
        }
    }

    @Test
    fun theAppListIsRestored() {
        val original = RAppsSingleton.instance.apps
        try {
            underGuard { RAppsSingleton.instance.apps = arrayListOf(App(label = "t005", packageName = "t005.pkg", name = "T005")) }
            assertEquals(original?.map { it.packageName }, RAppsSingleton.instance.apps?.map { it.packageName })
        } finally {
            RAppsSingleton.instance.apps = original
        }
    }

    @Test
    fun stateIsRestoredEvenWhenTheTestBodyFails() {
        try {
            val expected = AssertionError("simulated test failure")
            try {
                underGuard {
                    prefs.edit().putString(sentinelAdded, "leak").commit()
                    throw expected
                }
                org.junit.Assert.fail("the test failure must propagate")
            } catch (actual: AssertionError) {
                org.junit.Assert.assertSame(expected, actual)
            }
            assertFalse("cleanup must run after a failing test", prefs.contains(sentinelAdded))
        } finally {
            prefs.edit().remove(sentinelAdded).commit()
        }
    }

    @Test
    fun animationScaleInTheGlobalNamespaceIsRestored() {
        val key = android.provider.Settings.Global.ANIMATOR_DURATION_SCALE
        val resolver = context.contentResolver
        val original = android.provider.Settings.Global.getString(resolver, key)
        try {
            underGuard { shell("settings put global $key 0.37") }
            assertEquals(original, android.provider.Settings.Global.getString(resolver, key))
        } finally {
            shell(if (original == null) "settings delete global $key" else "settings put global $key $original")
        }
    }

    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ).use { it.readBytes() }
    }

    /**
     * 21 device classes delete lenses with `dao.delete(lens)`, which (no ON DELETE CASCADE) leaves that
     * lens's app rows behind. Orphans then break the next class's `replaceWorkspace` require().
     */
    @Test
    fun appRowsOfALensDeletedByTheTestAreRemovedToo() {
        val db = com.mckimquyen.model.AppDatabase.getInstance()
        val lensId = "t005-orphan-lens"
        try {
            underGuard {
                kotlinx.coroutines.runBlocking {
                    db.lensWorkspaceDao().insertOrUpdate(com.mckimquyen.model.LensWorkspace(lensId, "T005", 9))
                    db.appPersistentDao().insert(com.mckimquyen.model.AppPersistent(
                        packageName = "t005.pkg", name = "T005", identifier = "t005.pkg-T005", lensId = lensId))
                    // exactly what the leaking classes do: drop the lens, forget its rows
                    db.lensWorkspaceDao().getAll().filter { it.id == lensId }.forEach { db.lensWorkspaceDao().delete(it) }
                }
            }
            val orphans = kotlinx.coroutines.runBlocking {
                val ids = db.lensWorkspaceDao().getAll().map { it.id }.toSet()
                db.appPersistentDao().getAllRows().filter { it.lensId !in ids }
            }
            assertEquals("no app row may point at a lens that no longer exists", emptyList<String>(), orphans.map { it.identifier })
        } finally {
            kotlinx.coroutines.runBlocking { db.appPersistentDao().deleteForLens(lensId) }
        }
    }

    @Test
    fun aSecondGuardAfterTheFirstSeesTheRestoredState() {
        check(prefs.edit().putString(sentinelChanged, "v1").commit())
        try {
            underGuard { prefs.edit().putString(sentinelChanged, "v2").commit() }
            underGuard { assertEquals("v1", prefs.getString(sentinelChanged, null)); prefs.edit().putString(sentinelChanged, "v3").commit() }
            assertEquals("v1", prefs.getString(sentinelChanged, null))
            assertTrue(prefs.contains(sentinelChanged))
        } finally {
            prefs.edit().remove(sentinelChanged).commit()
        }
    }
}
