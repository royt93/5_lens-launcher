package com.mckimquyen.util

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PERF-007: đo chi phí đọc và ghi palette Room trên thiết bị thật; so sánh baseline đọc từng app
 * và ghi từng app với phương án đọc một lần và ghi một batch. Sử dụng database in-memory riêng
 * trong test, không đọc hay ghi vào database người dùng đang chạy của launcher.
 */
@RunWith(AndroidJUnit4::class)
class PaletteScanBenchmarkTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private var benchmarkDb: AppDatabase? = null
    private val db get() = requireNotNull(benchmarkDb)

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        benchmarkDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun teardown() {
        benchmarkDb?.close()
        benchmarkDb = null
    }

    @Test
    fun benchmarkWarmPaletteLookupOnInMemoryFixture() = runBlocking {
        val dao = db.appPersistentDao()
        val identifiers = (1..BENCH_APP_COUNT).map { index -> "com.example.app$index-MainActivity" }
        // Seed 100 app có palette sẵn ở lens default và một nửa ở lens work
        identifiers.indices.forEach { index ->
            val pkg = "com.example.app${index + 1}"
            dao.insert(AppPersistent.defaults(pkg, "MainActivity").copy(paletteColor = 0xFF102030.toInt() + index))
            if (index % 2 == 0) {
                dao.insert(AppPersistent.defaults(pkg, "MainActivity", SECOND_LENS).copy(paletteColor = 0xFF102030.toInt() + index))
            }
        }

        // Warm-up JIT
        repeat(WARMUP_COUNT) {
            dao.getAllRows()
            identifiers.take(10).forEach { dao.findAnyByIdentifier(it) }
        }

        // Đo baseline N lần findAnyByIdentifier
        val legacyStart = System.nanoTime()
        val legacyColors = HashMap<String, Int>()
        identifiers.forEach { id ->
            val row = dao.findAnyByIdentifier(id)
            legacyColors[id] = row?.paletteColor ?: 0
        }
        val legacyNanos = System.nanoTime() - legacyStart

        // Đo candidate 1 lần getAllRows
        val candidateStart = System.nanoTime()
        val candidateColors = HashMap<String, Int>()
        dao.getAllRows().forEach { row ->
            if (!candidateColors.containsKey(row.identifier)) candidateColors[row.identifier] = row.paletteColor
        }
        val candidateNanos = System.nanoTime() - candidateStart

        assertEquals(legacyColors, candidateColors)
        Log.i(TAG, "warmLookup apps=$BENCH_APP_COUNT legacyMs=${legacyNanos / NANOS_PER_MS} candidateMs=${candidateNanos / NANOS_PER_MS}")
        // Dữ liệu đo timing; assertion chỉ xác nhận kết quả khớp nhau
        assertTrue("candidate phải ra cùng kết quả", candidateColors.isNotEmpty())
    }

    @Test
    fun benchmarkColdPalettePersistOnInMemoryFixture() = runBlocking {
        val dao = db.appPersistentDao()
        val appsToInsert = (1..BENCH_APP_COUNT).map { index ->
            AppPersistent.defaults("com.example.cold$index", "MainActivity")
                .copy(paletteColor = 0xFF204060.toInt() + index)
        }

        // Baseline: N lần setPaletteColor (mỗi app một transaction riêng)
        val legacyDb = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        val legacyDao = legacyDb.appPersistentDao()

        val legacyStart = System.nanoTime()
        appsToInsert.forEach { app ->
            legacyDao.setPaletteColor(app, app.paletteColor)
        }
        val legacyNanos = System.nanoTime() - legacyStart

        // Candidate: một batch trong một transaction
        val candidateStart = System.nanoTime()
        dao.setPaletteColors(appsToInsert)
        val candidateNanos = System.nanoTime() - candidateStart

        assertEquals(legacyDao.getAllRows().size, dao.getAllRows().size)
        legacyDb.close()

        Log.i(TAG, "coldPersist apps=$BENCH_APP_COUNT legacyMs=${legacyNanos / NANOS_PER_MS} candidateMs=${candidateNanos / NANOS_PER_MS}")
        assertTrue("candidate batch ghi đủ số app", dao.getAllRows().size == BENCH_APP_COUNT)
    }

    private companion object {
        const val TAG = "PERF007"
        const val SECOND_LENS = "work"
        const val BENCH_APP_COUNT = 100
        const val WARMUP_COUNT = 3
        const val NANOS_PER_MS = 1_000_000L
    }
}
