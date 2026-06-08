package com.mckimquyen.model

import androidx.room.*

@Dao
interface AppPersistentDao {
    @Query("SELECT * FROM APP_PERSISTENT")
    fun getAll(): List<AppPersistent>

    @Query("SELECT * FROM APP_PERSISTENT WHERE IDENTIFIER = :identifier LIMIT 1")
    fun findByIdentifier(identifier: String): AppPersistent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(app: AppPersistent): Long

    @Update
    fun update(app: AppPersistent)

    @Delete
    fun delete(app: AppPersistent)
}
