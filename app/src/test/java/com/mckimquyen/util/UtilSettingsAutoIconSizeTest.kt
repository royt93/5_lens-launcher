package com.mckimquyen.util

import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Icon-size default should auto-scale to the device instead of always being the
 * phone-tuned 18dp constant, but only until the user has an explicit saved value.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsAutoIconSizeTest {

    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test
    fun `baseline 360dp width keeps the original phone default`() {
        assertEquals(
            UtilSettings.DEFAULT_ICON_SIZE,
            UtilSettings.calculateAutoDefaultIconSize(360),
            0.001f
        )
    }

    @Test
    fun `wider device scales the default up proportionally`() {
        // 720dp smallest-width (tablet) -> double the baseline, clamped to MAX.
        val expected = (UtilSettings.DEFAULT_ICON_SIZE * 2f)
            .coerceAtMost(UtilSettings.MAX_ICON_SIZE + UtilSettings.MIN_ICON_SIZE)
        assertEquals(expected, UtilSettings.calculateAutoDefaultIconSize(720), 0.001f)
    }

    @Test
    fun `tiny screen width never goes below MIN_ICON_SIZE`() {
        assertEquals(
            UtilSettings.MIN_ICON_SIZE,
            UtilSettings.calculateAutoDefaultIconSize(1),
            0.001f
        )
    }

    @Test
    fun `huge screen width never exceeds the max`() {
        val max = UtilSettings.MAX_ICON_SIZE + UtilSettings.MIN_ICON_SIZE
        assertEquals(max, UtilSettings.calculateAutoDefaultIconSize(100_000), 0.001f)
    }

    @Test
    fun `first run with no saved value reads back the auto default`() {
        val settings = freshSettings()
        assertEquals(
            settings.autoDefaultIconSize,
            settings.getFloat(UtilSettings.KEY_ICON_SIZE),
            0.001f
        )
    }

    @Test
    fun `once the user saves an explicit value it wins regardless of device size`() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        assertEquals(30f, settings.getFloat(UtilSettings.KEY_ICON_SIZE), 0.001f)
    }
}
