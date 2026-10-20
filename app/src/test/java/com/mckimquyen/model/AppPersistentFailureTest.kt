package com.mckimquyen.model

import androidx.room.Room
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.services.AppEventManager
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * DB-006: a failed Room write must not leave memory and disk diverged, and a cancelled write must
 * never be swallowed (that would break structured cancellation of ApplicationScope).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AppPersistentFailureTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        setDatabaseInstance(db)
        RAppsSingleton.instance.clearAllData()
        RAppsSingleton.instance.apps = arrayListOf(
            App(packageName = PKG, name = NAME, isVisible = true, isOpened = true, isFavorite = false, folderName = null)
        )
    }

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) db.close()
        setDatabaseInstance(null)
        RAppsSingleton.instance.clearAllData()
    }

    @Test
    fun `runGuarded rethrows cancellation instead of swallowing it`() {
        var failureCalled = false
        try {
            runBlocking {
                AppPersistent.runGuarded(onFailure = { failureCalled = true }) { throw CancellationException("cancelled") }
            }
            fail("CancellationException must propagate")
        } catch (expected: CancellationException) {
            assertEquals("cancelled", expected.message)
        }
        assertFalse("a cancellation is not a database failure", failureCalled)
    }

    @Test
    fun `runGuarded reports a real failure once and does not rethrow it`() {
        val failures = mutableListOf<Throwable>()
        val boom = IllegalStateException("disk full")

        runBlocking { AppPersistent.runGuarded(onFailure = { failures += it }) { throw boom } }

        assertEquals(1, failures.size)
        assertSame(boom, failures.single())
    }

    @Test
    fun `runGuarded does nothing extra when the operation succeeds`() {
        var failureCalled = false
        var ran = false

        runBlocking { AppPersistent.runGuarded(onFailure = { failureCalled = true }) { ran = true } }

        assertTrue(ran)
        assertFalse(failureCalled)
    }

    @Test
    fun `failed visibility write rolls memory back to the previous value`() {
        val before = failureCount()
        breakDatabase()

        AppPersistent.setAppVisibility(PKG, NAME, false)

        awaitUntil("visibility must roll back to true") { RAppsSingleton.instance.findApp(PKG, NAME)?.isVisible == true }
        awaitFailureEvent(before)
    }

    @Test
    fun `failed organization write restores favorite folder and zone`() {
        val before = failureCount()
        breakDatabase()

        AppPersistent.setOrganization(PKG, NAME, true, "Work", PinnedZone.START)

        awaitUntil("organization must roll back") {
            val app = RAppsSingleton.instance.findApp(PKG, NAME)
            app?.isFavorite == false && app.folderName == null && app.pinnedZone == PinnedZone.NONE
        }
        awaitFailureEvent(before)
    }

    @Test
    fun `failed write publishes a failure event for the UI`() {
        val before = failureCount()
        breakDatabase()

        AppPersistent.setAppOpened(PKG, NAME, false)

        awaitFailureEvent(before)
    }

    @Test
    fun `failed order write restores every app's previous order`() {
        val second = App(packageName = PKG, name = "$NAME.Second", orderNumber = 7)
        RAppsSingleton.instance.apps = arrayListOf(
            App(packageName = PKG, name = NAME, orderNumber = 3),
            second
        )
        val before = failureCount()
        breakDatabase()

        AppPersistent.setAppOrderBatch(RAppsSingleton.instance.apps!!, "default")

        awaitUntil("orders must roll back to 3 and 7") {
            RAppsSingleton.instance.findApp(PKG, NAME)?.orderNumber == 3 &&
                RAppsSingleton.instance.findApp(PKG, "$NAME.Second")?.orderNumber == 7
        }
        awaitFailureEvent(before)
    }

    @Test
    fun `successful write keeps the new value and publishes no failure`() = runBlocking {
        val before = failureCount()

        AppPersistent.setAppVisibility(PKG, NAME, false)
        awaitUntil("row must reach Room") {
            runBlocking { db.appPersistentDao().findByIdentifier(AppPersistent.generateIdentifier(PKG, NAME))?.appVisible == false }
        }

        assertEquals(false, RAppsSingleton.instance.findApp(PKG, NAME)?.isVisible)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(before, AppEventManager.persistFailureCount.value ?: 0L)
    }

    /** Every write now fails with a real SQLite error, like a corrupted or full database. */
    private fun breakDatabase() {
        db.openHelper.writableDatabase.execSQL("DROP TABLE APP_PERSISTENT")
    }

    private fun awaitUntil(message: String, timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        fail("Timed out: $message")
    }

    /**
     * The failure counter is a process-wide LiveData and Robolectric resets the main looper between
     * tests. A delivery runnable still queued when a test ends is dropped while LiveData keeps its
     * pending flag set, so later postValue calls are never delivered. Every test that triggers a
     * failure therefore waits for the event itself, draining the main looper while it does.
     */
    private fun failureCount(): Long {
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return AppEventManager.persistFailureCount.value ?: 0L
    }

    private fun awaitFailureEvent(before: Long) {
        awaitUntil("a failure event must be published") {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            (AppEventManager.persistFailureCount.value ?: 0L) > before
        }
    }

    private fun setDatabaseInstance(database: AppDatabase?) {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, database)
    }

    private companion object {
        const val PKG = "com.example.db006"
        const val NAME = "com.example.db006.Main"
    }
}
