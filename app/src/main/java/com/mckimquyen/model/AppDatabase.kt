package com.mckimquyen.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AppPersistent::class], version = 8)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appPersistentDao(): AppPersistentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "app_persistent.db"
                        )
                        .allowMainThreadQueries() // Maintain compatibility for direct calls from Java UI thread
                        .addMigrations(MIGRATION_7_8)
                        .fallbackToDestructiveMigrationOnDowngrade()
                        .build()
                    }
                }
            }
        }

        fun getInstance(): AppDatabase {
            return INSTANCE ?: throw IllegalStateException("Database not initialized. Call init(context) in Application.")
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val columns = db.query("PRAGMA table_info(APP_PERSISTENT)").use { cursor ->
                    val names = mutableSetOf<String>()
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        names.add(cursor.getString(nameIndex))
                    }
                    names
                }

                if (columns.contains("M_IDENTIFIER")) {
                    migrateSugarTableToRoom(db)
                } else if (!columns.contains("PALETTE_COLOR")) {
                    db.execSQL("ALTER TABLE APP_PERSISTENT ADD COLUMN PALETTE_COLOR INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private fun migrateSugarTableToRoom(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS APP_PERSISTENT_ROOM_NEW (
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    PACKAGE_NAME TEXT,
                    NAME TEXT,
                    IDENTIFIER TEXT NOT NULL,
                    OPEN_COUNT INTEGER NOT NULL,
                    ORDER_NUMBER INTEGER NOT NULL,
                    APP_VISIBLE INTEGER NOT NULL,
                    APP_OPENED INTEGER NOT NULL,
                    PALETTE_COLOR INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO APP_PERSISTENT_ROOM_NEW (
                    ID,
                    PACKAGE_NAME,
                    NAME,
                    IDENTIFIER,
                    OPEN_COUNT,
                    ORDER_NUMBER,
                    APP_VISIBLE,
                    APP_OPENED,
                    PALETTE_COLOR
                )
                SELECT
                    ID,
                    M_PACKAGE_NAME,
                    M_NAME,
                    COALESCE(M_IDENTIFIER, ''),
                    COALESCE(M_OPEN_COUNT, 0),
                    COALESCE(M_ORDER_NUMBER, -1),
                    COALESCE(M_APP_VISIBLE, 1),
                    COALESCE(M_APP_OPENED, 1),
                    0
                FROM APP_PERSISTENT
                """.trimIndent()
            )
            db.execSQL("DROP TABLE APP_PERSISTENT")
            db.execSQL("ALTER TABLE APP_PERSISTENT_ROOM_NEW RENAME TO APP_PERSISTENT")
        }
    }
}
