package com.mckimquyen.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LensSettingsSliderValueTest {
    @Test fun valuesAreSnappedClampedAndFiniteForSlider() {
        assertEquals(10.0f, LensSettingsActivity.snap(Float.NaN, 10f, 55f, 1f), 0f)
        assertEquals(55.0f, LensSettingsActivity.snap(99f, 10f, 55f, 1f), 0f)
        assertEquals(20.0f, LensSettingsActivity.snap(20.4f, 10f, 55f, 1f), 0f)
        assertEquals(1.2f, LensSettingsActivity.snap(1.23f, 0.5f, 5f, 0.1f), 0.0001f)
    }
}
