package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * FISH-015: the user-saved "Custom" lens physics preset. Distortion follows the exact same
 * per-lens split [UtilSettingsPerLensTest] already proves for [UtilSettings.KEY_DISTORTION_FACTOR]
 * - a Custom distortion override is per-lens, while Custom scale/animation-time stay global,
 * matching how the fixed Gentle/Standard/Snappy presets already treat these three fields.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsCustomPresetTest {

    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    private val work = "work-lens-id"
    private val personal = "personal-lens-id"

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    // ---- hasCustomPreset() ----

    @Test
    fun `hasCustomPreset is false before any save`() {
        assertFalse(settings.hasCustomPreset())
    }

    @Test
    fun `hasCustomPreset is true once the custom scale is saved`() {
        settings.saveCustomScaleFactor(1.4f)
        assertTrue(settings.hasCustomPreset())
    }

    // ---- Distortion: default before any save ----

    @Test
    fun `custom distortion before any save matches the live effective distortion for that lens`() {
        assertEquals(
            settings.getDistortionFactor(work),
            settings.getCustomDistortionFactor(work),
            0.001f
        )
    }

    // ---- Distortion: per-lens round trip and fallback ----

    @Test
    fun `saving custom distortion for one lens does not affect another`() {
        settings.saveCustomDistortionFactor(work, 3.2f)

        assertEquals(3.2f, settings.getCustomDistortionFactor(work), 0.001f)
        assertEquals(
            "An untouched lens must fall back, not read the other lens's override",
            settings.getDistortionFactor(personal),
            settings.getCustomDistortionFactor(personal),
            0.001f
        )
    }

    @Test
    fun `a lens with no override inherits the base custom distortion once one exists`() {
        settings.saveCustomDistortionFactor(LensWorkspace.DEFAULT_LENS_ID, 4.0f)

        assertEquals(
            "The base custom value must be the fallback once it exists",
            4.0f,
            settings.getCustomDistortionFactor(work),
            0.001f
        )
    }

    @Test
    fun `custom distortion is clamped to the same range as the regular distortion key`() {
        settings.saveCustomDistortionFactor(work, 99f)
        val max = UtilSettings.MAX_DISTORTION_FACTOR / 2f + UtilSettings.MIN_DISTORTION_FACTOR
        assertEquals(max, settings.getCustomDistortionFactor(work), 0.001f)

        settings.saveCustomDistortionFactor(work, -5f)
        assertEquals(UtilSettings.MIN_DISTORTION_FACTOR, settings.getCustomDistortionFactor(work), 0.001f)
    }

    // ---- Scale / animation-time: global round trip ----

    @Test
    fun `custom scale factor round trips and is global`() {
        settings.saveCustomScaleFactor(1.7f)
        assertEquals(1.7f, settings.getCustomScaleFactor(), 0.001f)
        assertTrue(rawPrefs().contains(UtilSettings.KEY_CUSTOM_SCALE_FACTOR))
    }

    @Test
    fun `custom animation time round trips and is global`() {
        settings.saveCustomAnimationTime(180L)
        assertEquals(180L, settings.getCustomAnimationTime())
        assertTrue(rawPrefs().contains(UtilSettings.KEY_CUSTOM_ANIMATION_TIME))
    }

    @Test
    fun `custom animation time before any save falls back to the documented default`() {
        assertEquals(UtilSettings.DEFAULT_ANIMATION_TIME, settings.getCustomAnimationTime())
    }

    // ---- Lifecycle: delete / duplicate ----

    @Test
    fun `deleteLensSettings removes that lens's custom distortion override`() {
        settings.saveCustomDistortionFactor(work, 4.5f)
        settings.saveCustomDistortionFactor(personal, 1.5f)

        settings.deleteLensSettings(work)

        assertFalse(rawPrefs().contains("${UtilSettings.KEY_CUSTOM_DISTORTION_FACTOR}_$work"))
        assertEquals(1.5f, settings.getCustomDistortionFactor(personal), 0.001f)
    }

    @Test
    fun `duplicateLensSettings copies the effective custom distortion onto the new lens`() {
        settings.saveCustomDistortionFactor(work, 4.5f)

        settings.duplicateLensSettings(work, personal)

        assertEquals(4.5f, settings.getCustomDistortionFactor(personal), 0.001f)
    }

    @Test
    fun `duplicating from a lens that only inherits materializes the inherited custom value`() {
        settings.saveCustomDistortionFactor(LensWorkspace.DEFAULT_LENS_ID, 3.0f)

        settings.duplicateLensSettings(work, personal)

        assertEquals(
            "work never saved its own override, so it inherits the base custom value - " +
                "duplicating it must materialize that inherited value onto personal",
            3.0f,
            settings.getCustomDistortionFactor(personal),
            0.001f
        )
    }
}
