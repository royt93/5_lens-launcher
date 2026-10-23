package com.mckimquyen.adt

import androidx.appcompat.app.AppCompatActivity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.Observer
import androidx.room.Room
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.services.AppEventManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Dùng Biometric.toggleLockApp thật, chỉ thay prompt hệ thống để không cần vân tay thật. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AppAdapterAuthenticationTest {
    @get:Rule val liveData = InstantTaskExecutorRule()
    private var database: AppDatabase? = null
    private val app = App(label = "Mail", packageName = "com.test.auth", name = "Main", lensId = "work")

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        setDatabase(database)
        RAppsSingleton.instance.apps = arrayListOf(app)
    }

    @After
    fun cleanup() {
        database?.close()
        setDatabase(null)
        RAppsSingleton.instance.clearAllData()
    }

    @Test
    fun productionAuthenticationCallbackWritesOnlyOnSuccess() {
        var callback: BiometricPrompt.AuthenticationCallback? = null
        Mockito.mockConstruction(BiometricPrompt::class.java) { _, construction ->
            callback = construction.arguments()[2] as BiometricPrompt.AuthenticationCallback
        }.use { prompts ->
            val activity = Mockito.mock(AppCompatActivity::class.java)
            Mockito.`when`(activity.getString(R.string.verify_your_identity)).thenReturn("Xác thực")
            Mockito.`when`(activity.getString(R.string.cancel)).thenReturn("Hủy")
            var events = 0
            var uiUpdated = false
            val observer = Observer<Any?> { events++ }
            AppEventManager.lockChanged.observeForever(observer)
            events = 0
            try {
                val actions = AppRowActions()
                actions.toggleLock(activity, app) { uiUpdated = true }
                assertNotNull("Phải đăng ký callback vào BiometricPrompt", callback)
                Mockito.verify(prompts.constructed().single()).authenticate(Mockito.any(BiometricPrompt.PromptInfo::class.java))
                assertEquals(0, events)
                assertTrue(currentApp().isOpened)

                requireNotNull(callback).onAuthenticationError(BiometricPrompt.ERROR_NEGATIVE_BUTTON, "Hủy")
                assertEquals(0, events)
                assertFalse(uiUpdated)
                assertTrue(currentApp().isOpened)
                assertNull(row())

                actions.toggleLock(activity, app) { updated -> uiUpdated = !updated.isOpened }
                requireNotNull(callback).onAuthenticationSucceeded(Mockito.mock(BiometricPrompt.AuthenticationResult::class.java))
                assertEquals(1, events)
                assertTrue(uiUpdated)
                assertFalse(currentApp().isOpened)
                awaitStoredLock()
            } finally {
                AppEventManager.lockChanged.removeObserver(observer)
            }
        }
    }

    private fun currentApp() = requireNotNull(RAppsSingleton.instance.findApp(app.packageName.toString(), app.name.toString()))

    private fun row() = runBlocking { database?.appPersistentDao()?.findByIdentifier(
        app.lensId, AppPersistent.generateIdentifier(app.packageName.toString(), app.name.toString())) }

    private fun awaitStoredLock() {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (row()?.appOpened == false) return
            Thread.sleep(POLL_MS)
        }
        fail("Xác thực thành công phải lưu lock vào đúng lens")
    }

    private fun setDatabase(db: AppDatabase?) {
        AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, db)
    }

    private companion object {
        const val WAIT_SECONDS = 5L
        const val POLL_MS = 10L
    }
}
