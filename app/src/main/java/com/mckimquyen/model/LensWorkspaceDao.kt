package com.mckimquyen.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mckimquyen.util.LensGroupPolicy

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

    /**
     * Refuses to delete the final lens or the default lens (DB-003: backup export/import require
     * exactly one `default` lens, and startup would resurrect it empty anyway); deleting a
     * workspace and its layout rows is atomic.
     */
    @Transaction
    suspend fun deleteLensIfMoreThanOne(id: String): Boolean {
        if (id == LensWorkspace.DEFAULT_LENS_ID) return false
        val lenses = getAll()
        if (lenses.size <= 1) return false
        val lens = findById(id) ?: return false
        delete(lens)
        AppDatabase.getInstance().appPersistentDao().deleteForLens(id)
        return true
    }

    @Query("DELETE FROM LENS_WORKSPACE")
    suspend fun deleteAll(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(workspaces: List<LensWorkspace>)

    @Transaction
    suspend fun assignGroup(lensIds: Set<String>, groupName: String?) {
        require(lensIds.isNotEmpty()) { "At least one lens is required" }
        require(groupName == null || groupName.isNotBlank()) { "Group name must not be blank" }
        require(groupName == null || LensGroupPolicy.normalizeName(groupName) == groupName) {
            "Group name must already be normalized"
        }
        val lenses = lensIds.map { id ->
            findById(id) ?: throw IllegalArgumentException("Unknown lens ID: $id")
        }
        lenses.forEach { update(it.copy(groupName = groupName)) }
    }

    /** v2-only destructive replacement; v1 imports use the default-lens layout applier instead. */
    @Transaction
    suspend fun replaceWorkspace(workspaces: List<LensWorkspace>, importedRows: List<AppPersistent>) {
        require(workspaces.isNotEmpty()) { "Workspace must contain at least the default lens" }
        require(workspaces.count { it.id == LensWorkspace.DEFAULT_LENS_ID } == 1) {
            "Workspace must contain exactly one default lens"
        }
        require(workspaces.map { it.id }.toSet().size == workspaces.size) { "Workspace contains duplicate lens IDs" }
        val lensIds = workspaces.map { it.id }.toSet()
        require(importedRows.all { it.lensId in lensIds })

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
                    appVisible = false,
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
