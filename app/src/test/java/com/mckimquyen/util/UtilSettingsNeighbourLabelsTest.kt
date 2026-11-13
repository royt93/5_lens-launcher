package com.mckimquyen.util

import androidx.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsNeighbourLabelsTest {
    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test fun neighbourLabelsAreOffByDefault() {
        assertFalse(freshSettings().getBoolean(UtilSettings.KEY_NEIGHBOUR_LABELS))
        assertFalse(UtilSettings.DEFAULT_NEIGHBOUR_LABELS)
    }

    @Test fun enablingAndDisablingPersists() {
        val settings = freshSettings()
        settings.save(UtilSettings.KEY_NEIGHBOUR_LABELS, true)
        assertTrue(settings.getBoolean(UtilSettings.KEY_NEIGHBOUR_LABELS))
        settings.save(UtilSettings.KEY_NEIGHBOUR_LABELS, false)
        assertFalse(settings.getBoolean(UtilSettings.KEY_NEIGHBOUR_LABELS))
    }

    @Test fun theKeyIsStable() = assertEquals("neighbour_labels", UtilSettings.KEY_NEIGHBOUR_LABELS)
}
