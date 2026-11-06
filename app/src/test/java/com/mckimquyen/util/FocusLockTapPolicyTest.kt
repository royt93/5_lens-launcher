package com.mckimquyen.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusLockTapPolicyTest {
    @Test fun acceptsOnlyCompletedSinglePointerEmptyTapInsideBounds() {
        assertTrue(FocusLockTapPolicy.acceptsEmptyTap(true, false, false, 1, -1, 12f, 34f, 100, 100))
        assertFalse(FocusLockTapPolicy.acceptsEmptyTap(false, false, false, 1, -1, 12f, 34f, 100, 100))
        assertFalse(FocusLockTapPolicy.acceptsEmptyTap(true, true, false, 1, -1, 12f, 34f, 100, 100))
        assertFalse(FocusLockTapPolicy.acceptsEmptyTap(true, false, true, 1, -1, 12f, 34f, 100, 100))
        assertFalse(FocusLockTapPolicy.acceptsEmptyTap(true, false, false, 2, -1, 12f, 34f, 100, 100))
        assertFalse(FocusLockTapPolicy.acceptsEmptyTap(true, false, false, 1, 0, 12f, 34f, 100, 100))
    }

    @Test fun rejectsInvalidCoordinatesAndUnusableBounds() {
        val valid = Triple(true, false, false)
        assertFalse(FocusLockTapPolicy.acceptsEmptyTap(valid.first, valid.second, valid.third, 1, -1, Float.NaN, 1f, 10, 10))
        assertFalse(FocusLockTapPolicy.acceptsEmptyTap(true, false, false, 1, -1, Float.POSITIVE_INFINITY, 1f, 10, 10))
        assertFalse(FocusLockTapPolicy.acceptsEmptyTap(true, false, false, 1, -1, 1f, Float.NEGATIVE_INFINITY, 10, 10))
        assertFalse(FocusLockTapPolicy.acceptsEmptyTap(true, false, false, 1, -1, -0.1f, 1f, 10, 10))
        assertFalse(FocusLockTapPolicy.acceptsEmptyTap(true, false, false, 1, -1, 1f, -0.1f, 10, 10))
        assertFalse(FocusLockTapPolicy.acceptsEmptyTap(true, false, false, 1, -1, 10f, 1f, 10, 10))
        assertFalse(FocusLockTapPolicy.acceptsEmptyTap(true, false, false, 1, -1, 1f, 10f, 10, 10))
        assertFalse(FocusLockTapPolicy.acceptsEmptyTap(true, false, false, 1, -1, 1f, 1f, 0, 10))
        assertFalse(FocusLockTapPolicy.acceptsEmptyTap(true, false, false, 1, -1, 1f, 1f, 10, 0))
    }

    @Test fun acceptsPairAtExactTimeoutAndSlop() {
        assertTrue(
            FocusLockTapPolicy.isDoubleTap(
                EmptyTapSample(0f, 0f, 100), EmptyTapSample(3f, 4f, 400), timeoutMs = 300, slopPx = 5f
            )
        )
    }

    @Test fun rejectsMissingLateEarlyFarAndInvalidPairSamples() {
        val first = EmptyTapSample(0f, 0f, 100)
        assertFalse(FocusLockTapPolicy.isDoubleTap(null, EmptyTapSample(0f, 0f, 200), 300, 5f))
        assertFalse(FocusLockTapPolicy.isDoubleTap(first, EmptyTapSample(0f, 0f, 401), 300, 5f))
        assertFalse(FocusLockTapPolicy.isDoubleTap(first, EmptyTapSample(0f, 0f, 99), 300, 5f))
        assertFalse(FocusLockTapPolicy.isDoubleTap(first, EmptyTapSample(5.01f, 0f, 200), 300, 5f))
        assertFalse(FocusLockTapPolicy.isDoubleTap(EmptyTapSample(Float.NaN, 0f, 100), EmptyTapSample(0f, 0f, 200), 300, 5f))
        assertFalse(FocusLockTapPolicy.isDoubleTap(first, EmptyTapSample(0f, Float.POSITIVE_INFINITY, 200), 300, 5f))
        assertFalse(FocusLockTapPolicy.isDoubleTap(first, EmptyTapSample(0f, 0f, 200), 0, 5f))
        assertFalse(FocusLockTapPolicy.isDoubleTap(first, EmptyTapSample(0f, 0f, 200), 300, 0f))
        assertFalse(FocusLockTapPolicy.isDoubleTap(first, EmptyTapSample(0f, 0f, 200), 300, Float.NaN))
    }
}
