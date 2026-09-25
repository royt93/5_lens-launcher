package com.mckimquyen.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PERF-004: records the cold-start path (process start -> ActHome -> LensView with icons) into
 * app/src/main/generated/baselineProfiles/. Run with
 * `./gradlew :app:generateProductionReleaseBaselineProfile` while only the approved device is attached.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        startHomeAndWaitForGrid()
    }
}
