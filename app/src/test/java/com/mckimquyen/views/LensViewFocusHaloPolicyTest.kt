package com.mckimquyen.views

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LensViewFocusHaloPolicyTest {

    @Test
    fun shouldDrawFocusHalo_trueWhenEnabledAndCleanModeOff() {
        assertTrue(LensView.shouldDrawFocusHalo(enabled = true, cleanMode = false))
    }

    @Test
    fun shouldDrawFocusHalo_falseWhenDisabled() {
        assertFalse(LensView.shouldDrawFocusHalo(enabled = false, cleanMode = false))
        assertFalse(LensView.shouldDrawFocusHalo(enabled = false, cleanMode = true))
    }

    @Test
    fun shouldDrawFocusHalo_falseWhenCleanModeOn() {
        assertFalse(LensView.shouldDrawFocusHalo(enabled = true, cleanMode = true))
    }
}
