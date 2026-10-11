package com.mckimquyen.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/**
 * FISH-008: Data access object for managing Lens workspaces.
 */
@Dao
interface LensWorkspaceDao {

    @Query("SELECT * FROM LENS_WORKSPACE ORDER BY ORDER_INDEX ASC, CREATED_AT ASC")
    suspend fun getAll(): List<LensWorkspace>

    @Query("SELECT * FROM LENS_WORKSPACE WHERE ID = :id LIMIT 1")
    suspend fun findById(id: String): LensWorkspace?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(workspace: LensWorkspace): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(workspace: LensWorkspace): Long

    @Update
    suspend fun update(workspace: LensWorkspace)

    @Delete
    suspend fun delete(workspace: LensWorkspace)

    @Query("DELETE FROM LENS_WORKSPACE WHERE ID = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM LENS_WORKSPACE")
    suspend fun deleteAll(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(workspaces: List<LensWorkspace>)

    @Transaction
    suspend fun replaceWorkspace(workspaces: List<LensWorkspace>, importedRows: List<AppPersistent>) {
        require(workspaces.isNotEmpty()) { "Workspace must contain at least the default lens" }
        require(workspaces.count { it.id == LensWorkspace.DEFAULT_LENS_ID } == 1) {
            "Workspace must contain exactly one default lens"
        }
        require(workspaces.map { it.id }.toSet().size == workspaces.size) { "Workspace contains duplicate lens IDs" }
        val lensIds = workspaces.map { it.id }.toSet()
        require(importedRows.all { it.lensId in lensIds })
        require(importedRows.map { it.lensId to it.identifier }.toSet().size == importedRows.size)

        val appDao = AppDatabase.getInstance().appPersistentDao()
        val oldRows = appDao.getAllRows()
        val preservedByIdentifier = oldRows.groupBy { it.identifier }.mapValues { (_, rows) -> rows.first() }
        val importedIds = importedRows.mapTo(mutableSetOf()) { it.identifier }
        val restoredRows = importedRows.map { imported ->
            val preserved = preservedByIdentifier[imported.identifier]
            imported.copy(
                id = null,
                openCount = preserved?.openCount ?: imported.openCount,
                paletteColor = preserved?.paletteColor ?: imported.paletteColor,
                notificationCount = preserved?.notificationCount ?: imported.notificationCount
            )
        } + preservedByIdentifier.values
            .filter { it.identifier !in importedIds }
            .map { old ->
                old.copy(
                    id = null,
                    orderNumber = -1,
                    appVisible = true,
                    appOpened = true,
                    isFavorite = false,
                    folderName = null,
                    pinnedZone = PinnedZone.NONE.name,
                    lensId = LensWorkspace.DEFAULT_LENS_ID
                )
            }

        appDao.replaceWorkspaceLayout(restoredRows)
        deleteAll()
        insertAll(workspaces)
    }
}
