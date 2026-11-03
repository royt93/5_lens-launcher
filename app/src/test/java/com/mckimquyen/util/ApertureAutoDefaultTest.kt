package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ApertureAutoDefaultTest {
    @Test
    fun `zero and negative counts use the small-lens value`() {
        assertEquals(3.5f, ApertureAutoDefault.forAppCount(0), 0f)
        assertEquals(3.5f, ApertureAutoDefault.forAppCount(-1), 0f)
    }

    @Test
    fun `24 apps is small and 25 apps is medium`() {
        assertEquals(3.5f, ApertureAutoDefault.forAppCount(24), 0f)
        assertEquals(2.5f, ApertureAutoDefault.forAppCount(25), 0f)
    }

    @Test
    fun `99 apps is medium and 100 apps is large`() {
        assertEquals(2.5f, ApertureAutoDefault.forAppCount(99), 0f)
        assertEquals(1.8f, ApertureAutoDefault.forAppCount(100), 0f)
    }

    @Test
    fun `very large counts stay at the large-lens value`() {
        assertEquals(1.8f, ApertureAutoDefault.forAppCount(Int.MAX_VALUE), 0f)
    }
}
