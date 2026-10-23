package com.mckimquyen.model

import androidx.room.Room
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AppPersistentPaletteBatchTest {
    private var database: AppDatabase? = null
    private val db get() = requireNotNull(database)
    private val dao get() = db.appPersistentDao()

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        database?.close()
        database = null
    }

    @Test
    fun batchOnlyChangesPaletteAndSeedsMissingDefaults() = runBlocking {
        val first = AppPersistent.defaults(FIRST_PACKAGE, COMPONENT).copy(
            appVisible = false,
            appOpened = false,
            isFavorite = true,
            folderName = FOLDER,
            pinnedZone = PinnedZone.START.name,
            orderNumber = CUSTOM_ORDER,
            openCount = OPEN_COUNT
        )
        dao.insert(first)
        dao.insert(first.copy(id = null, lensId = SECOND_LENS))
        val before = dao.getAllRows()

        val fresh = AppPersistent.defaults(SECOND_PACKAGE, COMPONENT).copy(paletteColor = SECOND_COLOR)
        dao.setPaletteColors(listOf(first.copy(paletteColor = FIRST_COLOR), fresh))

        val after = dao.getAllRows()
        before.forEach { old ->
            val updated = after.single { it.id == old.id }
            assertEquals("mọi field ngoài paletteColor phải giữ nguyên", old.copy(paletteColor = FIRST_COLOR), updated)
        }
        val insertedFresh = dao.findByIdentifier(fresh.identifier)
        assertNotNull("app chưa có row phải được tạo row default", insertedFresh)
        assertEquals(SECOND_COLOR, insertedFresh?.paletteColor)
        assertEquals(LensWorkspace.DEFAULT_LENS_ID, insertedFresh?.lensId)
    }

    @Test
    fun batchDoesNotOverwriteRowExclusivelyOnSecondaryLens() = runBlocking {
        val secondaryOnly = AppPersistent.defaults(FIRST_PACKAGE, COMPONENT, SECOND_LENS).copy(
            appVisible = false,
            orderNumber = CUSTOM_ORDER
        )
        dao.insert(secondaryOnly)

        dao.setPaletteColors(listOf(AppPersistent.defaults(FIRST_PACKAGE, COMPONENT).copy(paletteColor = FIRST_COLOR)))

        val secondaryStored = dao.getAllForLens(SECOND_LENS).single()
        assertEquals(FIRST_COLOR, secondaryStored.paletteColor)
        assertEquals(false, secondaryStored.appVisible)
        assertEquals(CUSTOM_ORDER, secondaryStored.orderNumber)
        val defaultStored = dao.findByIdentifier(LensWorkspace.DEFAULT_LENS_ID, secondaryOnly.identifier)
        assertNotNull("vẫn tạo row default giống helper cũ", defaultStored)
        assertEquals(FIRST_COLOR, defaultStored?.paletteColor)
    }

    @Test
    fun batchFailureRollsBackAllRowsInTransaction() = runBlocking {
        val first = AppPersistent.defaults(FIRST_PACKAGE, COMPONENT).copy(paletteColor = INITIAL_COLOR)
        val second = AppPersistent.defaults(SECOND_PACKAGE, COMPONENT).copy(paletteColor = INITIAL_COLOR)
        dao.insert(first)
        dao.insert(second)
        val before = dao.getAllRows()

        // Trigger ABORT ở row thứ hai để mô phỏng lỗi giữa transaction
        val secondIdentifier = second.identifier
        db.openHelper.writableDatabase.execSQL(
            """
            CREATE TEMP TRIGGER fail_second_palette
            BEFORE UPDATE OF PALETTE_COLOR ON APP_PERSISTENT
            WHEN NEW.IDENTIFIER = '$secondIdentifier'
            BEGIN SELECT RAISE(ABORT, 'palette batch fixture failure'); END
            """.trimIndent()
        )

        val failure = runCatching {
            dao.setPaletteColors(
                listOf(
                    first.copy(paletteColor = FIRST_COLOR),
                    second.copy(paletteColor = SECOND_COLOR)
                )
            )
        }.exceptionOrNull()

        assertNotNull("phải ném exception khi trigger fail", failure)
        val after = dao.getAllRows()
        assertEquals("toàn bộ transaction phải rollback về trạng thái trước đó", before, after)
    }

    private companion object {
        const val FIRST_PACKAGE = "com.example.first"
        const val SECOND_PACKAGE = "com.example.second"
        const val COMPONENT = "MainActivity"
        const val FOLDER = "Tools"
        const val SECOND_LENS = "work"
        const val CUSTOM_ORDER = 42
        const val OPEN_COUNT = 7L
        const val INITIAL_COLOR = 0xFF112233.toInt()
        const val FIRST_COLOR = 0xFF224466.toInt()
        const val SECOND_COLOR = 0xFF4488AA.toInt()
    }
}
