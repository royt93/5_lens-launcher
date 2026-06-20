package com.mckimquyen.feature.vip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VipKeysTest {

    @Test
    fun testValidVip30DaysKey() {
        val key = "9fA0q7eN!27cLx04@21993Y2u0I7#Q0"
        val days = VipKeys.lookupDays(key)
        assertEquals(30, days)
    }

    @Test
    fun testValidVip3DaysKey() {
        val key = "eQ7@93L0f!2Y2707xN04021993u0I#2aK"
        val days = VipKeys.lookupDays(key)
        assertEquals(3, days)
    }

    @Test
    fun testInvalidKey() {
        val key = "invalid_key_123"
        val days = VipKeys.lookupDays(key)
        assertNull(days)
    }

    @Test
    fun testEmptyKey() {
        val days = VipKeys.lookupDays("")
        assertNull(days)
    }

    @Test
    fun testKeyWithWhitespaces() {
        val key = "  9fA0q7eN!27cLx04@21993Y2u0I7#Q0  "
        val days = VipKeys.lookupDays(key)
        assertEquals(30, days)
    }

    @Test
    fun testCaseInsensitiveKey() {
        val uppercaseKey = "9FA0Q7EN!27CLX04@21993Y2U0I7#Q0"
        assertEquals(30, VipKeys.lookupDays(uppercaseKey))

        val lowercaseKey = "9fa0q7en!27clx04@21993y2u0i7#q0"
        assertEquals(30, VipKeys.lookupDays(lowercaseKey))
    }

    // BUG-4: verify that .uppercase() at call site produces a key that lookupDays accepts
    @Test
    fun `BUG4 - lowercase key normalized at call site returns correct days`() {
        val rawInput = "9fa0q7en!27clx04@21993y2u0i7#q0" // lowercase as user might type
        val normalizedKey = rawInput.trim().uppercase()   // mirrors fix in btnActivateVipKey
        val days = VipKeys.lookupDays(normalizedKey)
        assertEquals("Normalized lowercase 30d key must return 30", 30, days)
    }

    @Test
    fun `BUG4 - lowercase 3-day key normalized at call site returns 3 days`() {
        val rawInput = "eq7@93l0f!2y2707xn04021993u0i#2ak" // lowercase 3d key
        val normalizedKey = rawInput.trim().uppercase()
        val days = VipKeys.lookupDays(normalizedKey)
        assertEquals("Normalized lowercase 3d key must return 3", 3, days)
    }

    @Test
    fun `BUG4 - mixed case key normalizes correctly`() {
        val rawInput = "9fA0Q7en!27cLx04@21993Y2u0I7#q0" // mixed case
        val normalizedKey = rawInput.trim().uppercase()
        val days = VipKeys.lookupDays(normalizedKey)
        assertEquals("Mixed case key after normalization must return 30", 30, days)
    }

    @Test
    fun `BUG4 - normalized key equals VIP_30D_KEY uppercase`() {
        val rawInput = "9fa0q7en!27clx04@21993y2u0i7#q0"
        val normalizedKey = rawInput.uppercase()
        assertEquals(
            "Normalized key must equal VIP_30D_KEY.uppercase()",
            VipKeys.VIP_30D_KEY.uppercase(),
            normalizedKey
        )
    }

    @Test
    fun `BUG4 - key with leading and trailing spaces normalizes correctly`() {
        val rawInput = "  9fA0q7eN!27cLx04@21993Y2u0I7#Q0  "
        val normalizedKey = rawInput.trim().uppercase()
        val days = VipKeys.lookupDays(normalizedKey)
        assertEquals(30, days)
    }
}

