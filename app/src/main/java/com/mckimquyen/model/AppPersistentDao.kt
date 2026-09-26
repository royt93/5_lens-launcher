package com.mckimquyen.model

import androidx.room.*

@Dao
interface AppPersistentDao {
    @Query("SELECT * FROM APP_PERSISTENT WHERE LENS_ID = 'default'")
    suspend fun getAll(): List<AppPersistent>

    @Query("SELECT * FROM APP_PERSISTENT WHERE LENS_ID = :lensId")
    suspend fun getAllForLens(lensId: String): List<AppPersistent>

    @Query("SELECT * FROM APP_PERSISTENT WHERE LENS_ID = :lensId AND IDENTIFIER = :identifier LIMIT 1")
    suspend fun findByIdentifier(lensId: String, identifier: String): AppPersistent?

    @Query("SELECT * FROM APP_PERSISTENT WHERE LENS_ID = 'default' AND IDENTIFIER = :identifier LIMIT 1")
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

    @Query("SELECT OPEN_COUNT FROM APP_PERSISTENT WHERE LENS_ID = 'default' AND IDENTIFIER = :identifier LIMIT 1")
    suspend fun getOpenCount(identifier: String): Long?

    @Query("UPDATE APP_PERSISTENT SET ORDER_NUMBER = :orderNumber WHERE LENS_ID = :lensId AND IDENTIFIER = :identifier")
    suspend fun updateOrder(lensId: String, identifier: String, orderNumber: Int)

    @Query("UPDATE APP_PERSISTENT SET APP_OPENED = :opened WHERE LENS_ID = :lensId AND IDENTIFIER = :identifier")
    suspend fun updateOpened(lensId: String, identifier: String, opened: Boolean)

    @Query("UPDATE APP_PERSISTENT SET APP_VISIBLE = :visible WHERE LENS_ID = :lensId AND IDENTIFIER = :identifier")
    suspend fun updateVisibility(lensId: String, identifier: String, visible: Boolean)

    @Query("UPDATE APP_PERSISTENT SET PALETTE_COLOR = :color WHERE IDENTIFIER = :identifier")
    suspend fun updatePaletteColor(identifier: String, color: Int)

    @Query(
        "UPDATE APP_PERSISTENT SET IS_FAVORITE = :favorite, " +
            "FOLDER_NAME = :folderName, PINNED_ZONE = :pinnedZone " +
            "WHERE LENS_ID = :lensId AND IDENTIFIER = :identifier"
    )
    suspend fun updateOrganization(
        lensId: String,
        identifier: String,
        favorite: Boolean,
        folderName: String?,
        pinnedZone: String
    )

    @Query("DELETE FROM APP_PERSISTENT WHERE LENS_ID = :lensId")
    suspend fun deleteForLens(lensId: String): Int

    @Transaction
    suspend fun duplicateLensLayout(sourceLensId: String, targetLensId: String) {
        val sourceApps = getAllForLens(sourceLensId)
        sourceApps.forEach { app ->
            val copy = app.copy(id = null, lensId = targetLensId)
            insert(copy)
        }
    }

    @Transaction
    suspend fun incrementAtomic(defaults: AppPersistent): Long {
        insertIfAbsent(defaults.copy(openCount = 0L))
        check(incrementExisting(defaults.identifier) >= 1) {
            "Unable to increment ${defaults.identifier}"
        }
        return getOpenCount(defaults.identifier) ?: 0L
    }

    @Transaction
    suspend fun setOrders(apps: List<AppPersistent>) {
        apps.forEach { app ->
            insertIfAbsent(app)
            updateOrder(app.lensId, app.identifier, app.orderNumber)
        }
    }

    @Transaction
    suspend fun setOpened(defaults: AppPersistent, opened: Boolean) {
        insertIfAbsent(defaults.copy(appOpened = opened))
        updateOpened(defaults.lensId, defaults.identifier, opened)
    }

    @Transaction
    suspend fun setVisibility(defaults: AppPersistent, visible: Boolean) {
        insertIfAbsent(defaults.copy(appVisible = visible))
        updateVisibility(defaults.lensId, defaults.identifier, visible)
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
        updateOrganization(defaults.lensId, defaults.identifier, favorite, folderName, pinnedZone)
    }
}
