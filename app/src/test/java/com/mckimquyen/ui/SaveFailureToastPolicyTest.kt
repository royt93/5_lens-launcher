package com.mckimquyen.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DB-006: a screen toasts only for failures newer than what it has already shown or started with. */
class SaveFailureToastPolicyTest {
    @Test
    fun `a newer failure is shown`() = assertTrue(ActBase.shouldShowSaveFailure(lastSeen = 3, current = 4))

    @Test
    fun `the replayed value at subscription time is not shown`() = assertFalse(ActBase.shouldShowSaveFailure(lastSeen = 3, current = 3))

    @Test
    fun `an older or reset value is not shown`() = assertFalse(ActBase.shouldShowSaveFailure(lastSeen = 3, current = 1))

    @Test
    fun `a burst that jumps several counts is shown once because lastSeen advances to the latest`() {
        var lastSeen = 0L
        var shown = 0
        for (count in longArrayOf(5L, 5L, 7L)) {
            if (ActBase.shouldShowSaveFailure(lastSeen, count)) { lastSeen = count; shown++ }
        }
        org.junit.Assert.assertEquals(2, shown)
    }
}
