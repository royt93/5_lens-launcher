package com.mckimquyen.util

import androidx.preference.PreferenceManager
import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * FISH-012: the pending-distortion-factor key is what survives a process kill between a pinch
 * gesture ending and the user answering the confirmation Snackbar (see LensView.reportPinchFinished
 * and ActHome.bindLensView's resurrect check). It follows the exact same per-lens `lensKey`
 * suffixing convention as the real KEY_DISTORTION_FACTOR (UtilSettingsPerLensTest), on purpose -
 * these tests only prove the pending key's own contract, not that convention again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsPendingDistortionTest {

    private val work = "work-lens-id"
    private val personal = "personal-lens-id"

    private fun freshSettings(): UtilSettings {
        val context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        return UtilSettings(context)
    }

    @Test
    fun `pending distortion is null when never set`() {
        assertNull(freshSettings().getPendingDistortionFactor(work))
    }

    @Test
    fun `saved pending distortion reads back for that lens`() {
        val settings = freshSettings()
        settings.savePendingDistortionFactor(work, 3.7f)
        assertEquals(3.7f, settings.getPendingDistortionFactor(work)!!, 0.001f)
    }

    @Test
    fun `pending distortion is independent per lens`() {
        val settings = freshSettings()
        settings.savePendingDistortionFactor(work, 3.7f)
        settings.savePendingDistortionFactor(personal, 1.2f)

        assertEquals(3.7f, settings.getPendingDistortionFactor(work)!!, 0.001f)
        assertEquals(1.2f, settings.getPendingDistortionFactor(personal)!!, 0.001f)
    }

    @Test
    fun `clearing pending distortion removes only that lens's key`() {
        val settings = freshSettings()
        settings.savePendingDistortionFactor(work, 3.7f)
        settings.savePendingDistortionFactor(personal, 1.2f)

        settings.clearPendingDistortionFactor(work)

        assertNull(settings.getPendingDistortionFactor(work))
        assertEquals(1.2f, settings.getPendingDistortionFactor(personal)!!, 0.001f)
    }

    @Test
    fun `the default lens uses the unsuffixed key, same convention as the real distortion key`() {
        val settings = freshSettings()
        settings.savePendingDistortionFactor(LensWorkspace.DEFAULT_LENS_ID, 2.2f)

        val rawPrefs = PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication())
        assertEquals(2.2f, rawPrefs.getFloat(UtilSettings.KEY_PENDING_DISTORTION_FACTOR, -1f), 0.001f)
    }

    @Test
    fun `deleteLensSettings also clears a lens's pending key`() {
        val settings = freshSettings()
        settings.savePendingDistortionFactor(work, 3.7f)

        settings.deleteLensSettings(work)

        assertNull(settings.getPendingDistortionFactor(work))
    }
}
