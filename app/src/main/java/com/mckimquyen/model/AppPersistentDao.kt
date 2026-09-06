package com.mckimquyen.model

import androidx.room.*

@Dao
interface AppPersistentDao {
    @Query("SELECT * FROM APP_PERSISTENT")
    suspend fun getAll(): List<AppPersistent>

    @Query("SELECT * FROM APP_PERSISTENT WHERE IDENTIFIER = :identifier LIMIT 1")
    suspend fun findByIdentifier(identifier: String): AppPersistent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: AppPersistent): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(app: AppPersistent): Long

    @Update
    suspend fun update(app: AppPersistent)

    @Delete
    suspend fun delete(app: AppPersistent)

    @Query("UPDATE APP_PERSISTENT SET OPEN_COUNT = OPEN_COUNT + 1 WHERE IDENTIFIER = :identifier")
    suspend fun incrementExisting(identifier: String): Int

    @Query("SELECT OPEN_COUNT FROM APP_PERSISTENT WHERE IDENTIFIER = :identifier LIMIT 1")
    suspend fun getOpenCount(identifier: String): Long?

    @Query("UPDATE APP_PERSISTENT SET ORDER_NUMBER = :orderNumber WHERE IDENTIFIER = :identifier")
    suspend fun updateOrder(identifier: String, orderNumber: Int)

    @Query("UPDATE APP_PERSISTENT SET APP_OPENED = :opened WHERE IDENTIFIER = :identifier")
    suspend fun updateOpened(identifier: String, opened: Boolean)

    @Query("UPDATE APP_PERSISTENT SET APP_VISIBLE = :visible WHERE IDENTIFIER = :identifier")
    suspend fun updateVisibility(identifier: String, visible: Boolean)

    @Query("UPDATE APP_PERSISTENT SET PALETTE_COLOR = :color WHERE IDENTIFIER = :identifier")
    suspend fun updatePaletteColor(identifier: String, color: Int)

    @Query(
        "UPDATE APP_PERSISTENT SET IS_FAVORITE = :favorite, " +
            "FOLDER_NAME = :folderName, PINNED_ZONE = :pinnedZone " +
            "WHERE IDENTIFIER = :identifier"
    )
    suspend fun updateOrganization(
        identifier: String,
        favorite: Boolean,
        folderName: String?,
        pinnedZone: String
    )

    @Transaction
    suspend fun incrementAtomic(defaults: AppPersistent): Long {
        insertIfAbsent(defaults.copy(openCount = 0L))
        check(incrementExisting(defaults.identifier) == 1) {
            "Unable to increment ${defaults.identifier}"
        }
        return getOpenCount(defaults.identifier) ?: 0L
    }

    @Transaction
    suspend fun setOrders(apps: List<AppPersistent>) {
        apps.forEach { app ->
            insertIfAbsent(app)
            updateOrder(app.identifier, app.orderNumber)
        }
    }

    @Transaction
    suspend fun setOpened(defaults: AppPersistent, opened: Boolean) {
        insertIfAbsent(defaults.copy(appOpened = opened))
        updateOpened(defaults.identifier, opened)
    }

    @Transaction
    suspend fun setVisibility(defaults: AppPersistent, visible: Boolean) {
        insertIfAbsent(defaults.copy(appVisible = visible))
        updateVisibility(defaults.identifier, visible)
    }

    @Transaction
    suspend fun setPaletteColor(defaults: AppPersistent, color: Int) {
        insertIfAbsent(defaults.copy(paletteColor = color))
        updatePaletteColor(defaults.identifier, color)
    }

    @Transaction
    suspend fun setOrganization(
        defaults: AppPersistent,
        favorite: Boolean,
        folderName: String?,
        pinnedZone: String
    ) {
        insertIfAbsent(
            defaults.copy(
                isFavorite = favorite,
                folderName = folderName,
                pinnedZone = pinnedZone
            )
        )
        updateOrganization(defaults.identifier, favorite, folderName, pinnedZone)
    }
}
