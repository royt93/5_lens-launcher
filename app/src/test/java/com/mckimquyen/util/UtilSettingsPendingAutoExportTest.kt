package com.mckimquyen.util

import androidx.preference.PreferenceManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsPendingAutoExportTest {

    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test
    fun `pending auto-export defaults to false when unset`() {
        val settings = freshSettings()
        assertFalse(settings.hasPendingAutoExportLens())
    }

    @Test
    fun `saving pending auto-export reads back true`() {
        val settings = freshSettings()
        settings.setPendingAutoExportLens(true)
        assertTrue(settings.hasPendingAutoExportLens())
    }

    @Test
    fun `clearing pending auto-export reverts to false`() {
        val settings = freshSettings()
        settings.setPendingAutoExportLens(true)
        assertTrue(settings.hasPendingAutoExportLens())

        settings.clearPendingAutoExportLens()
        assertFalse(settings.hasPendingAutoExportLens())
    }
}
