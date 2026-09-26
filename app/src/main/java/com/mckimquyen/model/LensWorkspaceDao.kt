package com.mckimquyen.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
}
