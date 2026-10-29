package com.mckimquyen.util

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LensWorkspace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LensFreezeEditorTest {
    @Test fun targetOrderDoesNotChangeOtherLensAndUnfreezeClearsOnlyTarget() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val settings = UtilSettings(context)
            db.lensWorkspaceDao().insertOrUpdate(LensWorkspace(TARGET, "Work"))
            val app = App(packageName = "test.pkg", name = "Main")
            val original = AppPersistent.defaults("test.pkg", "Main").copy(orderNumber = 7)
            db.appPersistentDao().insert(original)
            val editor = LensFreezeEditor(db, settings)
            editor.setFrozen(TARGET, true, listOf(app))
            assertTrue(settings.isLensFrozen(TARGET))
            assertEquals(0, db.appPersistentDao().findByIdentifier(TARGET, app.identifier)?.orderNumber)
            assertEquals(7, db.appPersistentDao().findByIdentifier(app.identifier)?.orderNumber)
            editor.setFrozen(TARGET, false)
            assertFalse(settings.isLensFrozen(TARGET))
            assertEquals(-1, db.appPersistentDao().findByIdentifier(TARGET, app.identifier)?.orderNumber)
            assertEquals(7, db.appPersistentDao().findByIdentifier(app.identifier)?.orderNumber)
        } finally { db.close() }
    }

    @Test fun deletedLensCannotWriteRowsOrFrozenFlag() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val settings = UtilSettings(context)
            settings.saveLensFrozen(TARGET, false)
            try {
                LensFreezeEditor(db, settings).setFrozen(TARGET, true, listOf(App(packageName = "test", name = "Main")))
                fail("Missing lens must be rejected")
            } catch (_: IllegalArgumentException) { }
            assertFalse(settings.isLensFrozen(TARGET))
            assertTrue(db.appPersistentDao().getAllForLens(TARGET).isEmpty())
        } finally { db.close() }
    }

    private companion object { const val TARGET = "ui033-freeze" }
}
