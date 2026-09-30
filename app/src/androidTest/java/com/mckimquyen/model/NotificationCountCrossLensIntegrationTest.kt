package com.mckimquyen.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationCountCrossLensIntegrationTest {

    private val dao get() = AppDatabase.getInstance().appPersistentDao()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() = runBlocking {
        AppDatabase.init(context)
        dao.getAll().filter { it.identifier == IDENTIFIER }.forEach { dao.delete(it) }
    }

    @After
    fun tearDown() = runBlocking {
        dao.getAllForLens("second").filter { it.identifier == IDENTIFIER }.forEach { dao.delete(it) }
        dao.getAll().filter { it.identifier == IDENTIFIER }.forEach { dao.delete(it) }
    }

    @Test
    fun countSetWhileOneLensIsActiveReadsIdenticallyFromAnotherLens() = runBlocking {
        val onDefaultLens = AppPersistent(
            packageName = "pkg.camera",
            name = "CameraActivity",
            identifier = IDENTIFIER
        )
        val onSecondLens = onDefaultLens.copy(lensId = "second")
        dao.insertIfAbsent(onDefaultLens)
        dao.insertIfAbsent(onSecondLens)

        dao.setNotificationCount(onDefaultLens, 6)

        assertEquals(6, dao.findByIdentifier("second", IDENTIFIER)?.notificationCount)
        assertEquals(6, dao.findByIdentifier(IDENTIFIER)?.notificationCount) // default lens
    }

    private companion object {
        const val IDENTIFIER = "pkg.camera-CameraActivity"
    }
}
