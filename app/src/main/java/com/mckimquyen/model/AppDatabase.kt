package com.mckimquyen.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AppPersistent::class, LensWorkspace::class], version = 13, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appPersistentDao(): AppPersistentDao
    abstract fun lensWorkspaceDao(): LensWorkspaceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = builder(context.applicationContext, DATABASE_NAME).build()
                    }
                }
            }
        }

        private const val DATABASE_NAME = "app_persistent.db"

        /** The production builder, shared with device tests so they exercise the real migration chain. */
        @androidx.annotation.VisibleForTesting
        internal fun builder(context: Context, name: String): RoomDatabase.Builder<AppDatabase> =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*LEGACY_TO_FIRST_ROOM_MIGRATIONS)
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)

        fun getInstance(): AppDatabase {
            return INSTANCE ?: throw IllegalStateException("Database not initialized. Call init(context) in Application.")
        }

        private const val FIRST_LEGACY_VERSION = 1
        private const val FIRST_ROOM_VERSION = 7

        /**
         * DB-008: SugarORM-era databases (user_version 1..6) used to crash-loop on open because Room had
         * no path out of them. Their data is converted by MIGRATION_7_8 (it detects the Sugar table by
         * its M_* columns), so reaching version 7 is a no-op.
         */
        private val LEGACY_TO_FIRST_ROOM_MIGRATIONS: Array<Migration> =
            (FIRST_LEGACY_VERSION until FIRST_ROOM_VERSION).map { legacyVersion ->
                object : Migration(legacyVersion, FIRST_ROOM_VERSION) {
                    override fun migrate(db: SupportSQLiteDatabase) = Unit
                }
            }.toTypedArray()

        private const val UNRECOGNISED_BACKUP_TABLE = "APP_PERSISTENT_UNRECOGNISED_BACKUP"

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

                when {
                    columns.isEmpty() -> createRoomV8Table(db)
                    columns.contains("M_IDENTIFIER") -> migrateSugarTableToRoom(db, columns)
                    columns.contains("IDENTIFIER") ->
                        if (!columns.contains("PALETTE_COLOR")) {
                            db.execSQL("ALTER TABLE APP_PERSISTENT ADD COLUMN PALETTE_COLOR INTEGER NOT NULL DEFAULT 0")
                        }
                    else -> {
                        // Shape we cannot read: keep the rows for manual recovery rather than crash or drop them.
                        db.execSQL("DROP TABLE IF EXISTS $UNRECOGNISED_BACKUP_TABLE")
                        db.execSQL("ALTER TABLE APP_PERSISTENT RENAME TO $UNRECOGNISED_BACKUP_TABLE")
                        createRoomV8Table(db)
                    }
                }
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rows with an empty identifier would all collapse into one under the dedup below, losing every
                // such app's data; rebuild the identifier from package + name wherever both are known.
                db.execSQL(
                    """
                    UPDATE APP_PERSISTENT SET IDENTIFIER = PACKAGE_NAME || '-' || NAME
                    WHERE IDENTIFIER = ''
                      AND PACKAGE_NAME IS NOT NULL AND PACKAGE_NAME != ''
                      AND NAME IS NOT NULL AND NAME != ''
                    """.trimIndent()
                )
                // Keep the newest legacy row before enforcing stable component identity.
                db.execSQL(
                    """
                    DELETE FROM APP_PERSISTENT
                    WHERE ID NOT IN (
                        SELECT MAX(ID) FROM APP_PERSISTENT GROUP BY IDENTIFIER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_APP_PERSISTENT_IDENTIFIER " +
                        "ON APP_PERSISTENT (IDENTIFIER)"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE APP_PERSISTENT ADD COLUMN IS_FAVORITE " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("ALTER TABLE APP_PERSISTENT ADD COLUMN FOLDER_NAME TEXT")
                db.execSQL(
                    "ALTER TABLE APP_PERSISTENT ADD COLUMN PINNED_ZONE " +
                        "TEXT NOT NULL DEFAULT 'NONE'"
                )
            }
        }

        @androidx.annotation.VisibleForTesting
        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS LENS_WORKSPACE (
                        ID TEXT PRIMARY KEY NOT NULL,
                        NAME TEXT NOT NULL,
                        ORDER_INDEX INTEGER NOT NULL DEFAULT 0,
                        CREATED_AT INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO LENS_WORKSPACE (ID, NAME, ORDER_INDEX, CREATED_AT) " +
                        "VALUES ('default', 'Lens 1', 0, 0)"
                )
                db.execSQL(
                    "ALTER TABLE APP_PERSISTENT ADD COLUMN LENS_ID TEXT NOT NULL DEFAULT 'default'"
                )
                db.execSQL("DROP INDEX IF EXISTS index_APP_PERSISTENT_IDENTIFIER")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_APP_PERSISTENT_LENS_ID_IDENTIFIER " +
                        "ON APP_PERSISTENT (LENS_ID, IDENTIFIER)"
                )
            }
        }

        @androidx.annotation.VisibleForTesting
        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // UI-024: notification-count badges, global across lenses (no LENS_ID scoping),
                // same shape PALETTE_COLOR already uses.
                db.execSQL(
                    "ALTER TABLE APP_PERSISTENT ADD COLUMN NOTIFICATION_COUNT " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @androidx.annotation.VisibleForTesting
        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE LENS_WORKSPACE ADD COLUMN GROUP_NAME TEXT")
            }
        }

        private fun createRoomV8Table(db: SupportSQLiteDatabase, tableName: String = "APP_PERSISTENT") {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $tableName (
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
        }

        /** Old Sugar versions may lack a column; use [default] for a missing one instead of failing the query. */
        private fun sugarColumn(columns: Set<String>, name: String, default: String): String =
            if (columns.contains(name)) "COALESCE($name, $default)" else default

        private fun migrateSugarTableToRoom(db: SupportSQLiteDatabase, columns: Set<String>) {
            val newTable = "APP_PERSISTENT_ROOM_NEW"
            createRoomV8Table(db, newTable)
            db.execSQL(
                """
                INSERT INTO $newTable (
                    ID, PACKAGE_NAME, NAME, IDENTIFIER, OPEN_COUNT,
                    ORDER_NUMBER, APP_VISIBLE, APP_OPENED, PALETTE_COLOR
                )
                SELECT
                    ID,
                    ${if (columns.contains("M_PACKAGE_NAME")) "M_PACKAGE_NAME" else "NULL"},
                    ${if (columns.contains("M_NAME")) "M_NAME" else "NULL"},
                    ${sugarColumn(columns, "M_IDENTIFIER", "''")},
                    ${sugarColumn(columns, "M_OPEN_COUNT", "0")},
                    ${sugarColumn(columns, "M_ORDER_NUMBER", "-1")},
                    ${sugarColumn(columns, "M_APP_VISIBLE", "1")},
                    ${sugarColumn(columns, "M_APP_OPENED", "1")},
                    0
                FROM APP_PERSISTENT
                """.trimIndent()
            )
            db.execSQL("DROP TABLE APP_PERSISTENT")
            db.execSQL("ALTER TABLE $newTable RENAME TO APP_PERSISTENT")
        }
    }
}
