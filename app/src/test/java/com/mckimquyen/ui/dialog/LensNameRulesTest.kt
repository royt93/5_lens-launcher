package com.mckimquyen.ui.dialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The one rule every lens name field uses (group name, lens name). */
class LensNameRulesTest {
    @Test
    fun `null and blank names are rejected`() {
        assertNull(LensNameRules.normalize(null))
        assertNull(LensNameRules.normalize(""))
        assertNull(LensNameRules.normalize("   "))
        assertNull(LensNameRules.normalize("\t\n"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("Daily", LensNameRules.normalize("  Daily  "))
    }

    @Test
    fun `interior spaces are kept`() {
        assertEquals("My  Group", LensNameRules.normalize("My  Group"))
    }

    @Test
    fun `the maximum length is accepted and one more is rejected`() {
        val max = "a".repeat(LensNameRules.MAX_LENGTH)
        assertEquals(max, LensNameRules.normalize(max))
        assertNull(LensNameRules.normalize(max + "a"))
    }

    @Test
    fun `length is measured after trimming`() {
        val max = "a".repeat(LensNameRules.MAX_LENGTH)
        assertEquals(max, LensNameRules.normalize("   $max   "))
    }

    @Test
    fun `a zero width character is kept because the rule stays simple`() {
        assertEquals("​", LensNameRules.normalize("​"))
    }

    @Test
    fun `max length is the documented 64`() {
        assertEquals(64, LensNameRules.MAX_LENGTH)
    }
}
