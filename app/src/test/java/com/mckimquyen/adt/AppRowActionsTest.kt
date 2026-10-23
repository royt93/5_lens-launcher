package com.mckimquyen.adt

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.room.Room
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.services.AppEventManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.mockito.Mockito.mock
import androidx.appcompat.app.AppCompatActivity

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AppRowActionsTest {
    @get:Rule val liveData = InstantTaskExecutorRule()
    private var database: AppDatabase? = null
    private val app = App(packageName = "com.test.row", name = "Main", lensId = "work")
    private var success: (() -> Unit)? = null
    private val actions = AppRowActions { _, _, callback -> success = callback }
    private val observers = mutableListOf<Pair<androidx.lifecycle.LiveData<Any?>, Observer<Any?>>>()

    @Before
    fun setup() {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        database = db
        setDatabase(db)
        RAppsSingleton.instance.apps = arrayListOf(app)
    }

    @After
    fun cleanup() {
        observers.forEach { (source, observer) -> source.removeObserver(observer) }
        database?.close()
        setDatabase(null)
        RAppsSingleton.instance.clearAllData()
    }

    private fun observe(source: androidx.lifecycle.LiveData<Any?>, callback: () -> Unit) {
        val observer = Observer<Any?> { callback() }
        source.observeForever(observer)
        observers.add(source to observer)
    }

    @Test
    fun visibilityNotifiesOnlyAfterSnapshotChangesAndPersistsToOriginalLens() {
        var atEvent: Boolean? = null
        observe(AppEventManager.visibilityChanged) {
            atEvent = RAppsSingleton.instance.findApp(app.packageName.toString(), app.name.toString())?.isVisible
        }
        atEvent = null
        val updated = actions.toggleVisibility(app)
        assertFalse(updated.isVisible)
        assertEquals(false, atEvent)
        awaitRow { it.appVisible == false }
    }

    @Test
    fun cancelledAuthenticationDoesNotWriteOrNotify() {
        var events = 0
        observe(AppEventManager.lockChanged) { events++ }
        events = 0
        actions.toggleLock(mock(AppCompatActivity::class.java), app) { fail("Không được đổi UI trước xác thực") }
        assertNotNull(success)
        assertEquals(0, events)
        assertTrue(RAppsSingleton.instance.findApp(app.packageName.toString(), app.name.toString())?.isOpened == true)
        assertNull(runBlocking { database?.appPersistentDao()?.findByIdentifier(
            app.lensId, AppPersistent.generateIdentifier(app.packageName.toString(), app.name.toString())) })
    }

    @Test
    fun successfulAuthenticationUpdatesSnapshotUiAndEventThenPersistsCapturedLens() {
        val order = mutableListOf<String>()
        observe(AppEventManager.lockChanged) {
            if (RAppsSingleton.instance.findApp(app.packageName.toString(), app.name.toString())?.isOpened == false) {
                order.add("event")
            }
        }
        actions.toggleLock(mock(AppCompatActivity::class.java), app) { updated ->
            assertFalse(updated.isOpened)
            assertEquals(app.lensId, updated.lensId)
            order.add("ui")
        }
        // Mô phỏng holder chuyển sang app/lens khác khi prompt còn mở.
        RAppsSingleton.instance.apps = arrayListOf(app, app.copy(name = "Other", lensId = "personal"))
        requireNotNull(success).invoke()
        assertEquals(listOf("ui", "event"), order)
        awaitRow { !it.appOpened }
        assertTrue(RAppsSingleton.instance.findApp(app.packageName.toString(), "Other")?.isOpened == true)
    }

    @Test
    fun organizationNormalizesFolderAndNotifiesAfterSnapshotChanges() {
        var favoriteAtEvent = false
        observe(AppEventManager.appsEdited) {
            favoriteAtEvent = RAppsSingleton.instance.findApp(app.packageName.toString(), app.name.toString())?.isFavorite == true
        }
        val updated = actions.setOrganization(app, true, "  Work  ", PinnedZone.START)
        assertEquals("Work", updated.folderName)
        assertTrue(favoriteAtEvent)
        awaitRow { it.isFavorite && it.folderName == "Work" && it.pinnedZone == PinnedZone.START.name }
    }

    private fun awaitRow(condition: (AppPersistent) -> Boolean) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (System.nanoTime() < deadline) {
            val row = runBlocking { database?.appPersistentDao()?.findByIdentifier(
                app.lensId, AppPersistent.generateIdentifier(app.packageName.toString(), app.name.toString())) }
            if (row != null && condition(row)) return
            Thread.sleep(POLL_MS)
        }
        fail("Room phải lưu đúng app và lens ban đầu")
    }

    private fun setDatabase(db: AppDatabase?) {
        AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, db)
    }

    private companion object {
        const val WAIT_SECONDS = 5L
        const val POLL_MS = 10L
    }
}
