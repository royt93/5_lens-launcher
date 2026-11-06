package com.mckimquyen.model

import androidx.room.Room
import com.mckimquyen.app.RAppsSingleton
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** FISH-035: the confirmed-commit order API reports exactly one of COMMITTED, FAILED or SUPERSEDED, on main. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AppPersistentOrderOutcomeTest {
    private lateinit var db: AppDatabase
    private val apps = listOf("a", "b", "c").map { App(packageName = "com.fish035.$it", name = "Main", orderNumber = -1) }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        setDatabaseInstance(db)
        RAppsSingleton.instance.clearAllData()
        RAppsSingleton.instance.apps = ArrayList(apps)
        runBlocking { db.lensWorkspaceDao().insertOrUpdate(LensWorkspace(LENS, "Work")) }
    }

    @After
    fun tearDown() {
        AppPersistent.beforeOrderCommitForTest = null
        if (db.isOpen) db.close()
        setDatabaseInstance(null)
        RAppsSingleton.instance.clearAllData()
    }

    private fun commit(list: List<App>, lens: String = LENS): List<OrderCommitOutcome> {
        val outcomes = mutableListOf<OrderCommitOutcome>()
        AppPersistent.commitAppOrderBatch(list, lens) { outcomes += it }
        return outcomes
    }

    private fun await(outcomes: List<*>, count: Int = 1) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (outcomes.size >= count) return
            Thread.sleep(POLL_MS)
        }
        fail("timed out waiting for $count outcome(s), got ${outcomes.size}")
    }

    private fun storedOrder() = runBlocking { db.appPersistentDao().getAllForLens(LENS) }
        .sortedBy { it.orderNumber }.map { it.name + "@" + it.packageName?.substringAfterLast('.') }

    @Test fun aSuccessfulWriteReportsCommittedStoresTheOrderAndUpdatesMemory() {
        val reversed = apps.reversed()
        val outcomes = commit(reversed)
        await(outcomes)
        assertEquals(listOf(OrderCommitOutcome.COMMITTED), outcomes)
        assertEquals(listOf("Main@c", "Main@b", "Main@a"), storedOrder())
        assertEquals(0, RAppsSingleton.instance.findApp("com.fish035.c", "Main")?.orderNumber)
    }

    @Test fun aMissingLensReportsFailedAndWritesNothing() {
        val outcomes = commit(apps, lens = "deleted-lens")
        await(outcomes)
        assertEquals(listOf(OrderCommitOutcome.FAILED), outcomes)
        assertTrue(runBlocking { db.appPersistentDao().getAllForLens("deleted-lens") }.isEmpty())
        assertEquals(-1, RAppsSingleton.instance.findApp("com.fish035.a", "Main")?.orderNumber)
    }

    @Test fun aBrokenDatabaseReportsFailedAndLeavesMemoryUntouched() {
        db.openHelper.writableDatabase.execSQL("DROP TABLE APP_PERSISTENT")
        val outcomes = commit(apps.reversed())
        await(outcomes)
        assertEquals(listOf(OrderCommitOutcome.FAILED), outcomes)
        assertEquals(-1, RAppsSingleton.instance.findApp("com.fish035.c", "Main")?.orderNumber)
    }

    @Test fun anEmptyOrUnusableListReportsFailed() {
        val empty = commit(emptyList())
        val blank = commit(listOf(App(packageName = "", name = "x")))
        await(empty)
        await(blank)
        assertEquals(listOf(OrderCommitOutcome.FAILED), empty)
        assertEquals(listOf(OrderCommitOutcome.FAILED), blank)
    }

    @Test fun anOlderWriteThatLosesTheRaceReportsSupersededAndOnlyTheNewestIsStored() {
        val firstReached = CountDownLatch(1)
        val release = CountDownLatch(1)
        var firstGate = true
        AppPersistent.beforeOrderCommitForTest = {
            if (firstGate) {
                firstGate = false
                firstReached.countDown()
                assertTrue(release.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
            }
        }
        val first = commit(apps.reversed())
        assertTrue(firstReached.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        val second = commit(apps)            // newer revision while the first is parked before its check
        release.countDown()
        await(first)
        await(second)
        assertEquals(listOf(OrderCommitOutcome.SUPERSEDED), first)
        assertEquals(listOf(OrderCommitOutcome.COMMITTED), second)
        assertEquals(listOf("Main@a", "Main@b", "Main@c"), storedOrder())
    }

    @Test fun theCallbackRunsExactlyOnceOnTheMainThread() {
        var threads = mutableListOf<Boolean>()
        AppPersistent.commitAppOrderBatch(apps, LENS) {
            threads += (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        }
        await(threads)
        Thread.sleep(POLL_MS * 4)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(listOf(true), threads)
    }

    @Test fun theLegacyFireAndForgetApiStillWorksUnchanged() {
        AppPersistent.setAppOrderBatch(apps.reversed(), LENS)
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline && storedOrder() != listOf("Main@c", "Main@b", "Main@a")) Thread.sleep(POLL_MS)
        assertEquals(listOf("Main@c", "Main@b", "Main@a"), storedOrder())
    }

    private fun setDatabaseInstance(database: AppDatabase?) {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, database)
    }

    private companion object {
        const val LENS = "fish035-lens"
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 25L
    }
}
