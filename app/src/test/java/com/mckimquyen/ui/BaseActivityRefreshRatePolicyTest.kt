package com.mckimquyen.ui

import android.os.PowerManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests cho DISPLAY-001's mode-selection policy
 * ([BaseActivity.Companion.shouldRequestHighRefreshRate]).
 *
 * Đây là hàm pure (không đụng Display/Window thật), tách riêng để test được
 * đầy đủ mọi tổ hợp battery-saver × thermal-status mà không cần thiết bị thật.
 */
class BaseActivityRefreshRatePolicyTest {

    @Test
    fun `requests high refresh rate when battery saver off and no thermal throttling`() {
        assertTrue(
            BaseActivity.shouldRequestHighRefreshRate(
                batterySaverOn = false,
                thermalStatus = PowerManager.THERMAL_STATUS_NONE
            )
        )
    }

    @Test
    fun `requests high refresh rate under light thermal status`() {
        assertTrue(
            BaseActivity.shouldRequestHighRefreshRate(
                batterySaverOn = false,
                thermalStatus = PowerManager.THERMAL_STATUS_LIGHT
            )
        )
    }

    @Test
    fun `backs off when battery saver is on, regardless of thermal status`() {
        assertFalse(
            BaseActivity.shouldRequestHighRefreshRate(
                batterySaverOn = true,
                thermalStatus = PowerManager.THERMAL_STATUS_NONE
            )
        )
    }

    @Test
    fun `backs off at moderate thermal status`() {
        assertFalse(
            BaseActivity.shouldRequestHighRefreshRate(
                batterySaverOn = false,
                thermalStatus = PowerManager.THERMAL_STATUS_MODERATE
            )
        )
    }

    @Test
    fun `backs off above moderate thermal status`() {
        assertFalse(
            BaseActivity.shouldRequestHighRefreshRate(
                batterySaverOn = false,
                thermalStatus = PowerManager.THERMAL_STATUS_SEVERE
            )
        )
        assertFalse(
            BaseActivity.shouldRequestHighRefreshRate(
                batterySaverOn = false,
                thermalStatus = PowerManager.THERMAL_STATUS_CRITICAL
            )
        )
        assertFalse(
            BaseActivity.shouldRequestHighRefreshRate(
                batterySaverOn = false,
                thermalStatus = PowerManager.THERMAL_STATUS_EMERGENCY
            )
        )
        assertFalse(
            BaseActivity.shouldRequestHighRefreshRate(
                batterySaverOn = false,
                thermalStatus = PowerManager.THERMAL_STATUS_SHUTDOWN
            )
        )
    }

    @Test
    fun `backs off when both battery saver and thermal throttling are active`() {
        assertFalse(
            BaseActivity.shouldRequestHighRefreshRate(
                batterySaverOn = true,
                thermalStatus = PowerManager.THERMAL_STATUS_SEVERE
            )
        )
    }
}
