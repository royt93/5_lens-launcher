package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UI-026: a color pref that is empty or malformed must degrade to a default, never crash. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class SafeColorTest {
    private val fallback = 0xFFF50057.toInt()

    @Test
    fun `valid ARGB hex parses`() = assertEquals(0xFF112233.toInt(), SafeColor.parse("#FF112233", fallback))

    @Test
    fun `valid RGB hex parses as opaque`() = assertEquals(0xFF112233.toInt(), SafeColor.parse("#112233", fallback))

    @Test
    fun `null falls back`() = assertEquals(fallback, SafeColor.parse(null, fallback))

    @Test
    fun `empty and blank fall back`() {
        assertEquals(fallback, SafeColor.parse("", fallback))
        assertEquals(fallback, SafeColor.parse("   ", fallback))
    }

    @Test
    fun `garbage and truncated values fall back`() {
        for (bad in listOf("zzz", "#12", "#GGGGGG", "FF112233", "#FF11223", "#FF1122334", "red-ish", "#")) {
            assertEquals("value '$bad' must fall back", fallback, SafeColor.parse(bad, fallback))
        }
    }

    @Test
    fun `isValid agrees with parse`() {
        assertTrue(SafeColor.isValid("#FFF50057"))
        assertFalse(SafeColor.isValid(""))
        assertFalse(SafeColor.isValid(null))
        assertFalse(SafeColor.isValid("#12"))
    }

    @Test
    fun `display hex drops the alpha pair and keeps the stored format for RGB`() {
        assertEquals("#F50057", SafeColor.displayHex("#FFF50057", "#F50057"))
        assertEquals("#112233", SafeColor.displayHex("#112233", "#F50057"))
    }

    @Test
    fun `display hex of a bad value is the fallback text not a crash`() {
        assertEquals("#F50057", SafeColor.displayHex("", "#F50057"))
        assertEquals("#F50057", SafeColor.displayHex("#12", "#F50057"))
        assertEquals("#F50057", SafeColor.displayHex(null, "#F50057"))
    }
}
