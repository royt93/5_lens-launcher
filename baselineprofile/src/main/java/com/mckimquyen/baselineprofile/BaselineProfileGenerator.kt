package com.mckimquyen.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PERF-004 / BUILD-004: records the cold-start path (process start -> ActHome -> LensView with icons)
 * into app/src/main/generated/baselineProfiles/. Two separate collections, because the two files serve
 * different jobs:
 *  - baseline-prof.txt: every class/method worth AOT-compiling (keeps the ad SDK, whose init is hot).
 *  - startup-prof.txt: classes needed before the first frame, used for DEX layout. The ad SDKs start on a
 *    background thread after first draw, so they are filtered out to keep this profile narrow.
 * Run via `adb -s <serial> shell am instrument -e class ...BaselineProfileGenerator#<method>`, not the
 * Gradle task (see doc/task/done/p2-perf-perf-004-baseline-profile-cold-start.md).
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    private companion object {
        // JVM class descriptors (not dotted names): the predicate receives whole rule lines such as
        // "HSPLcom/applovin/sdk/Foo;->bar()V", so the class part is matched as a substring after the flags.
        val STARTUP_EXCLUDED_DESCRIPTORS = listOf("Lcom/applovin/", "Lcom/google/android/gms/ads/", "Lcom/roy/sdkadbmob/")
    }

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateBaseline() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = false,
    ) {
        startHomeAndWaitForGrid()
    }

    @Test
    fun generateStartup() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
        filterPredicate = { rule -> STARTUP_EXCLUDED_DESCRIPTORS.none { rule.contains(it) } },
    ) {
        startHomeAndWaitForGrid()
    }
}
