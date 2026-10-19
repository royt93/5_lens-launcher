package com.mckimquyen.model

import androidx.room.*

@Dao
interface AppPersistentDao {
    @Query("SELECT * FROM APP_PERSISTENT")
    suspend fun getAllRows(): List<AppPersistent>

    @Query("SELECT * FROM APP_PERSISTENT WHERE LENS_ID = 'default'")
    suspend fun getAll(): List<AppPersistent>

    @Query("SELECT * FROM APP_PERSISTENT WHERE LENS_ID = :lensId")
    suspend fun getAllForLens(lensId: String): List<AppPersistent>

    @Query("SELECT * FROM APP_PERSISTENT WHERE LENS_ID = :lensId AND IDENTIFIER = :identifier LIMIT 1")
    suspend fun findByIdentifier(lensId: String, identifier: String): AppPersistent?

    @Query("SELECT * FROM APP_PERSISTENT WHERE LENS_ID = 'default' AND IDENTIFIER = :identifier LIMIT 1")
    suspend fun findByIdentifier(identifier: String): AppPersistent?

    // FISH-008 Phase 2: paletteColor is global by design (see updatePaletteColor below), but a
    // row for a given identifier may only exist under a non-default lens (e.g. an app first
    // seen while a "Work" lens was active). This looks it up regardless of which lens wrote it,
    // for the palette-color-only read in UtilApp.getAppShells.
    @Query("SELECT * FROM APP_PERSISTENT WHERE IDENTIFIER = :identifier LIMIT 1")
    suspend fun findAnyByIdentifier(identifier: String): AppPersistent?

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

    /** DB-005: open count is global, so the truth is the highest value any lens row holds. */
    @Query("SELECT MAX(OPEN_COUNT) FROM APP_PERSISTENT WHERE IDENTIFIER = :identifier")
    suspend fun getMaxOpenCount(identifier: String): Long?

    @Query("UPDATE APP_PERSISTENT SET OPEN_COUNT = :count WHERE IDENTIFIER = :identifier")
    suspend fun setOpenCountForAllLenses(identifier: String, count: Long): Int

    /** One global open count per identifier, for overlaying onto any lens's list. */
    @Query("SELECT IDENTIFIER AS identifier, MAX(OPEN_COUNT) AS openCount FROM APP_PERSISTENT GROUP BY IDENTIFIER")
    suspend fun getGlobalOpenCounts(): List<GlobalOpenCount>

    @Query("UPDATE APP_PERSISTENT SET ORDER_NUMBER = :orderNumber WHERE LENS_ID = :lensId AND IDENTIFIER = :identifier")
    suspend fun updateOrder(lensId: String, identifier: String, orderNumber: Int)

    /** FISH-021: unfreeze - every app of the lens goes back to "no manual position". */
    @Query("UPDATE APP_PERSISTENT SET ORDER_NUMBER = -1 WHERE LENS_ID = :lensId")
    suspend fun clearOrderForLens(lensId: String): Int

    @Query("UPDATE APP_PERSISTENT SET APP_OPENED = :opened WHERE LENS_ID = :lensId AND IDENTIFIER = :identifier")
    suspend fun updateOpened(lensId: String, identifier: String, opened: Boolean)

    @Query("UPDATE APP_PERSISTENT SET APP_VISIBLE = :visible WHERE LENS_ID = :lensId AND IDENTIFIER = :identifier")
    suspend fun updateVisibility(lensId: String, identifier: String, visible: Boolean)

    @Query("UPDATE APP_PERSISTENT SET PALETTE_COLOR = :color WHERE IDENTIFIER = :identifier")
    suspend fun updatePaletteColor(identifier: String, color: Int)

    @Query("UPDATE APP_PERSISTENT SET NOTIFICATION_COUNT = :count WHERE IDENTIFIER = :identifier")
    suspend fun updateNotificationCount(identifier: String, count: Int)

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

    @Query("DELETE FROM APP_PERSISTENT")
    suspend fun deleteAllRows(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<AppPersistent>)

    @Transaction
    suspend fun replaceWorkspaceLayout(rows: List<AppPersistent>) {
        deleteAllRows()
        insertAll(rows)
    }

    @Query("DELETE FROM APP_PERSISTENT WHERE LENS_ID = 'default'")
    suspend fun deleteDefaultLensRows(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDefaultLensRows(rows: List<AppPersistent>)

    @Transaction
    suspend fun replaceDefaultLensLayout(rows: List<AppPersistent>) {
        require(rows.all { it.lensId == LensWorkspace.DEFAULT_LENS_ID })
        deleteDefaultLensRows()
        insertDefaultLensRows(rows)
    }

    @Transaction
    suspend fun duplicateLensLayout(sourceLensId: String, targetLensId: String) {
        val sourceApps = getAllForLens(sourceLensId)
        sourceApps.forEach { app ->
            val copy = app.copy(id = null, lensId = targetLensId)
            insert(copy)
        }
    }

    /**
     * DB-005: open count is global across lenses. Rows are per (lens, identifier), so they can start
     * out different (a lens row created later, or older builds that bumped each row by one). Bump
     * from the highest value and write it to every row, so the count never moves backwards or
     * differs by which lens is read.
     */
    @Transaction
    suspend fun incrementAtomic(defaults: AppPersistent): Long {
        insertIfAbsent(defaults.copy(openCount = 0L))
        val next = (getMaxOpenCount(defaults.identifier) ?: 0L) + 1L
        check(setOpenCountForAllLenses(defaults.identifier, next) >= 1) {
            "Unable to increment ${defaults.identifier}"
        }
        return next
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
    suspend fun setNotificationCount(defaults: AppPersistent, count: Int) {
        insertIfAbsent(defaults.copy(notificationCount = count))
        updateNotificationCount(defaults.identifier, count)
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

/** Projection for [AppPersistentDao.getGlobalOpenCounts]. */
data class GlobalOpenCount(val identifier: String, val openCount: Long)
