package com.mckimquyen.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationBadgeFormatterTest {

    @Test
    fun `counts one through ninety-nine format as the exact number`() {
        assertEquals("1", NotificationBadgeFormatter.format(1))
        assertEquals("42", NotificationBadgeFormatter.format(42))
        assertEquals("99", NotificationBadgeFormatter.format(99))
    }

    @Test
    fun `counts above ninety-nine cap to the display ceiling`() {
        assertEquals("99+", NotificationBadgeFormatter.format(100))
        assertEquals("99+", NotificationBadgeFormatter.format(9999))
    }
}
