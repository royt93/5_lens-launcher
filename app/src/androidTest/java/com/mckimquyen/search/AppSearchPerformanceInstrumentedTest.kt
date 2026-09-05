package com.mckimquyen.search

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.model.App
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSearchPerformanceInstrumentedTest {

    @Test
    fun searchOf400AppsStaysWithinDeviceBudget() {
        val apps = (0 until 400).map { index ->
            App(
                id = index,
                label = "Application $index Camera Notes",
                packageName = "com.example.catalog.application$index",
                name = "com.example.catalog.application$index.MainActivity",
                openCount = index.toLong()
            )
        }

        repeat(WARM_UP_RUNS) {
            AppSearchEngine.search(apps, "camera $it")
        }

        val samplesMs = LongArray(MEASURED_RUNS) { index ->
            val startedAt = SystemClock.elapsedRealtimeNanos()
            AppSearchEngine.search(apps, "camera ${index % 10}")
            (SystemClock.elapsedRealtimeNanos() - startedAt) / NANOS_PER_MILLISECOND
        }.sorted()

        val averageMs = samplesMs.average()
        val p95Ms = samplesMs[((samplesMs.size - 1) * 95) / 100]
        val maxMs = samplesMs.last()
        Log.i(
            TAG,
            "deviceSearch400 averageMs=$averageMs p95Ms=$p95Ms maxMs=$maxMs runs=$MEASURED_RUNS"
        )

        assertTrue(
            "400-app search exceeded ${SEARCH_BUDGET_MS}ms: p95=${p95Ms}ms, max=${maxMs}ms",
            p95Ms < SEARCH_BUDGET_MS
        )
    }

    private companion object {
        const val TAG = "FEAT001_PERF"
        const val WARM_UP_RUNS = 10
        const val MEASURED_RUNS = 50
        const val SEARCH_BUDGET_MS = 150L
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
