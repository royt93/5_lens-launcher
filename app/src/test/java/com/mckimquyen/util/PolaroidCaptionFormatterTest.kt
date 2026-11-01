package com.mckimquyen.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PolaroidCaptionFormatterTest {
    @Test fun formatsProfileAndThreeSettings() {
        assertEquals(
            "Fisheye · d=2.5 · s=1.0 · hood=100%",
            PolaroidCaptionFormatter.opticalLine("Fisheye", 2.5f, 1.0f, 1.0f, Locale.US)
        )
    }

    @Test fun roundsDistortionAndScaleToOneDecimalAndHoodToWholePercent() {
        assertEquals(
            "Pincushion · d=2.7 · s=1.5 · hood=35%",
            PolaroidCaptionFormatter.opticalLine("Pincushion", 2.66f, 1.54f, 0.349f, Locale.US)
        )
    }

    @Test fun usesTheLocalesDecimalSeparator() {
        assertEquals(
            "Mắt cá · d=2,5 · s=1,0 · hood=100%",
            PolaroidCaptionFormatter.opticalLine("Mắt cá", 2.5f, 1.0f, 1.0f, Locale.forLanguageTag("vi-VN"))
        )
    }

    @Test fun nonFiniteNumbersShowADashNotNaN() {
        assertEquals(
            "Fisheye · d=– · s=– · hood=–",
            PolaroidCaptionFormatter.opticalLine("Fisheye", Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, Locale.US)
        )
    }

    @Test fun blankProfileFallsBackToADash() {
        assertEquals(
            "– · d=2.5 · s=1.0 · hood=100%",
            PolaroidCaptionFormatter.opticalLine("  ", 2.5f, 1.0f, 1.0f, Locale.US)
        )
    }
}
