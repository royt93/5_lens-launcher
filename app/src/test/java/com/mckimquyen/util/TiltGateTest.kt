package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TiltGateTest {
    @Test fun runsOnlyWhenEveryConditionHolds() {
        for (mask in 0 until ALL_COMBINATIONS) {
            val enabled = mask and ENABLED != 0
            val reduceMotion = mask and REDUCE_MOTION != 0
            val resumed = mask and RESUMED != 0
            val searchShowing = mask and SEARCH != 0
            val listMode = mask and LIST != 0
            val sensor = mask and SENSOR != 0
            assertEquals(
                "mask=$mask",
                enabled && !reduceMotion && resumed && !searchShowing && !listMode && sensor,
                TiltGate.shouldRun(enabled, reduceMotion, resumed, searchShowing, listMode, sensor)
            )
        }
    }

    @Test fun theOnlyRunningCombinationIsTheExpectedOne() {
        val running = (0 until ALL_COMBINATIONS).filter {
            TiltGate.shouldRun(it and ENABLED != 0, it and REDUCE_MOTION != 0, it and RESUMED != 0,
                it and SEARCH != 0, it and LIST != 0, it and SENSOR != 0)
        }
        assertEquals(listOf(ENABLED or RESUMED or SENSOR), running)
    }

    private companion object {
        const val ENABLED = 1
        const val REDUCE_MOTION = 2
        const val RESUMED = 4
        const val SEARCH = 8
        const val LIST = 16
        const val SENSOR = 32
        const val ALL_COMBINATIONS = 64
    }
}
