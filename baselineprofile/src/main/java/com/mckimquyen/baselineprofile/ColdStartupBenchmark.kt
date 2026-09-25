package com.mckimquyen.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PERF-004 before/after proof: same cold start, once with no AOT compilation (what a user gets
 * on first launch without a profile) and once with the shipped baseline profile required.
 * CompilationMode resets ART state per run, so no manual reinstall is needed between them.
 *
 * Regression check is an audit-round step (compare timeToFullDisplayMs medians in the
 * Macrobenchmark JSON), not an automated gate - CI gating was declined by the owner (TEST-001).
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ColdStartupBenchmark {

    private companion object {
        const val ITERATIONS = 10
    }

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartupNoCompilation() = measure(CompilationMode.None())

    @Test
    fun coldStartupBaselineProfile() =
        measure(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun measure(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
    ) {
        startHomeAndWaitForGrid()
    }
}
