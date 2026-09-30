package com.mckimquyen.services

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class BadgeCountReceiverParserTest {

    @Test
    fun `valid count passes through unchanged`() {
        val extras = Bundle().apply { putInt(BadgeCountReceiver.EXTRA_COUNT, 3) }
        assertEquals(3, BadgeCountReceiver.parseBadgeCount(extras))
    }

    @Test
    fun `negative count clamps to zero`() {
        val extras = Bundle().apply { putInt(BadgeCountReceiver.EXTRA_COUNT, -5) }
        assertEquals(0, BadgeCountReceiver.parseBadgeCount(extras))
    }

    @Test
    fun `count above the spam guard clamps to the ceiling`() {
        val extras = Bundle().apply { putInt(BadgeCountReceiver.EXTRA_COUNT, 50000) }
        assertEquals(9999, BadgeCountReceiver.parseBadgeCount(extras))
    }

    @Test
    fun `missing extra defaults to zero`() {
        assertEquals(0, BadgeCountReceiver.parseBadgeCount(Bundle()))
    }

    @Test
    fun `null extras defaults to zero`() {
        assertEquals(0, BadgeCountReceiver.parseBadgeCount(null))
    }
}
