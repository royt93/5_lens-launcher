package com.mckimquyen.ui

import android.content.Context
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LensWorkspace
import kotlinx.coroutines.runBlocking

/** Full Room snapshot for LensManagementActivity instrumentation fixtures. */
internal data class LensManagerRoomSnapshot(
    private val lenses: List<LensWorkspace>,
    private val appRows: List<AppPersistent>,
    private val appPersistentSequence: Long?
) {
    fun restore(context: Context) = runBlocking {
        AppDatabase.init(context)
        val database = AppDatabase.getInstance()
        val lensDao = database.lensWorkspaceDao()
        val appDao = database.appPersistentDao()
        appDao.deleteAllRows()
        lensDao.deleteAll()
        if (lenses.isNotEmpty()) lensDao.insertAll(lenses)
        if (appRows.isNotEmpty()) appDao.insertAll(appRows)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE sqlite_sequence SET seq = ? WHERE name = 'APP_PERSISTENT'",
            arrayOf(appPersistentSequence)
        )
        if (appPersistentSequence == null) {
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM sqlite_sequence WHERE name = 'APP_PERSISTENT'"
            )
        }
        check(lensDao.getAll() == lenses) { "Lens fixture failed to restore LENS_WORKSPACE rows" }
        check(appDao.getAllRows() == appRows) { "Lens fixture failed to restore APP_PERSISTENT rows" }
        check(readAppPersistentSequence(database) == appPersistentSequence) {
            "Lens fixture failed to restore APP_PERSISTENT sequence"
        }
    }

    companion object {
        fun capture(context: Context): LensManagerRoomSnapshot = runBlocking {
            AppDatabase.init(context)
            val database = AppDatabase.getInstance()
            LensManagerRoomSnapshot(
                database.lensWorkspaceDao().getAll(),
                database.appPersistentDao().getAllRows(),
                readAppPersistentSequence(database)
            )
        }

        private fun readAppPersistentSequence(database: AppDatabase): Long? =
            database.openHelper.readableDatabase
                .query("SELECT seq FROM sqlite_sequence WHERE name = 'APP_PERSISTENT'")
                .use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
    }
}
