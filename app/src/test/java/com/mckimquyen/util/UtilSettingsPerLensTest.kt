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
 * FISH-008 Phase 3: per-lens distortion factor and Smart Focus bias.
 *
 * The contract these prove, in the order it matters:
 * 1. The default lens resolves to the *legacy global keys*, so a single-lens install (everyone
 *    before this feature) keeps reading and writing exactly what it always did.
 * 2. A lens with no override of its own inherits the global value - it does not silently fall
 *    back to the hardcoded constant and lose the user's chosen setting.
 * 3. Once a lens has its own value, that value wins and no other lens is affected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsPerLensTest {

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

    // ---- Default lens maps onto the legacy global keys ----

    @Test
    fun `default lens distortion writes the legacy global key`() {
        settings.saveDistortionFactor(LensWorkspace.DEFAULT_LENS_ID, 3.5f)

        assertEquals(
            "The default lens must keep using the pre-Phase-3 key so existing installs are untouched",
            3.5f,
            rawPrefs().getFloat(UtilSettings.KEY_DISTORTION_FACTOR, -1f),
            0.001f
        )
        assertFalse(
            "No suffixed key may be created for the default lens",
            rawPrefs().contains("${UtilSettings.KEY_DISTORTION_FACTOR}_${LensWorkspace.DEFAULT_LENS_ID}")
        )
    }

    @Test
    fun `null and empty lens ids behave exactly like the default lens`() {
        settings.saveDistortionFactor(null, 3.1f)
        assertEquals(3.1f, settings.getDistortionFactor(LensWorkspace.DEFAULT_LENS_ID), 0.001f)

        settings.saveDistortionFactor("", 4.2f)
        assertEquals(4.2f, settings.getDistortionFactor(null), 0.001f)
        assertEquals(4.2f, rawPrefs().getFloat(UtilSettings.KEY_DISTORTION_FACTOR, -1f), 0.001f)
    }

    @Test
    fun `default lens smart focus writes the legacy global key`() {
        settings.saveSmartFocusBias(LensWorkspace.DEFAULT_LENS_ID, true)

        assertTrue(rawPrefs().getBoolean(UtilSettings.KEY_SMART_FOCUS_BIAS, false))
        assertFalse(
            rawPrefs().contains("${UtilSettings.KEY_SMART_FOCUS_BIAS}_${LensWorkspace.DEFAULT_LENS_ID}")
        )
    }

    // ---- Inheritance while a lens has no override ----

    @Test
    fun `a lens with no override inherits the global distortion, not the constant`() {
        settings.save(UtilSettings.KEY_DISTORTION_FACTOR, 4.0f)

        assertEquals(
            "An untouched lens must show what the user already configured globally",
            4.0f,
            settings.getDistortionFactor(work),
            0.001f
        )
    }

    @Test
    fun `a lens with no override inherits the global smart focus flag`() {
        settings.save(UtilSettings.KEY_SMART_FOCUS_BIAS, true)

        assertTrue(settings.isSmartFocusBias(work))
    }

    @Test
    fun `a lens with no override falls back to the documented defaults on a fresh install`() {
        assertEquals(
            UtilSettings.DEFAULT_DISTORTION_FACTOR,
            settings.getDistortionFactor(work),
            0.001f
        )
        assertEquals(UtilSettings.DEFAULT_SMART_FOCUS_BIAS, settings.isSmartFocusBias(work))
    }

    // ---- Isolation once an override exists ----

    @Test
    fun `a lens override wins over the global value and leaves other lenses alone`() {
        settings.save(UtilSettings.KEY_DISTORTION_FACTOR, 2.0f)
        settings.saveDistortionFactor(work, 4.5f)

        assertEquals(4.5f, settings.getDistortionFactor(work), 0.001f)
        assertEquals("The untouched lens must still inherit", 2.0f, settings.getDistortionFactor(personal), 0.001f)
        assertEquals(
            "The global/default-lens value must be unchanged",
            2.0f,
            settings.getDistortionFactor(LensWorkspace.DEFAULT_LENS_ID),
            0.001f
        )
    }

    @Test
    fun `smart focus can differ per lens`() {
        settings.saveSmartFocusBias(work, true)
        settings.saveSmartFocusBias(personal, false)

        assertTrue(settings.isSmartFocusBias(work))
        assertFalse(settings.isSmartFocusBias(personal))
        assertEquals(
            "A per-lens write must not leak into the shared default",
            UtilSettings.DEFAULT_SMART_FOCUS_BIAS,
            settings.isSmartFocusBias(LensWorkspace.DEFAULT_LENS_ID)
        )
    }

    @Test
    fun `a per-lens distortion override is clamped to the same range as the global one`() {
        settings.saveDistortionFactor(work, 99f)
        val max = UtilSettings.MAX_DISTORTION_FACTOR / 2f + UtilSettings.MIN_DISTORTION_FACTOR
        assertEquals(max, settings.getDistortionFactor(work), 0.001f)

        settings.saveDistortionFactor(work, -5f)
        assertEquals(UtilSettings.MIN_DISTORTION_FACTOR, settings.getDistortionFactor(work), 0.001f)
    }

    // ---- Create / delete lifecycle ----

    @Test
    fun `duplicate copies both values onto the new lens`() {
        settings.saveDistortionFactor(work, 4.5f)
        settings.saveSmartFocusBias(work, true)

        settings.duplicateLensSettings(work, personal)

        assertEquals(4.5f, settings.getDistortionFactor(personal), 0.001f)
        assertTrue(settings.isSmartFocusBias(personal))
    }

    @Test
    fun `duplicating from a lens that only inherits materializes the inherited values`() {
        settings.save(UtilSettings.KEY_DISTORTION_FACTOR, 3.0f)
        settings.save(UtilSettings.KEY_SMART_FOCUS_BIAS, true)

        settings.duplicateLensSettings(LensWorkspace.DEFAULT_LENS_ID, work)

        assertEquals(3.0f, settings.getDistortionFactor(work), 0.001f)
        assertTrue(settings.isSmartFocusBias(work))
        // And it really is this lens's own value now, not a live link to the global one.
        settings.save(UtilSettings.KEY_DISTORTION_FACTOR, 1.0f)
        assertEquals(3.0f, settings.getDistortionFactor(work), 0.001f)
    }

    @Test
    fun `delete removes only that lens's keys`() {
        settings.saveDistortionFactor(work, 4.5f)
        settings.saveSmartFocusBias(work, true)
        settings.saveDistortionFactor(personal, 1.5f)
        settings.save(UtilSettings.KEY_DISTORTION_FACTOR, 2.0f)

        settings.deleteLensSettings(work)

        assertFalse(rawPrefs().contains("${UtilSettings.KEY_DISTORTION_FACTOR}_$work"))
        assertFalse(rawPrefs().contains("${UtilSettings.KEY_SMART_FOCUS_BIAS}_$work"))
        assertEquals("Deleting one lens must not touch another", 1.5f, settings.getDistortionFactor(personal), 0.001f)
        assertEquals(2.0f, settings.getDistortionFactor(LensWorkspace.DEFAULT_LENS_ID), 0.001f)
    }

    @Test
    fun `delete refuses to wipe the default lens's shared settings`() {
        settings.save(UtilSettings.KEY_DISTORTION_FACTOR, 2.0f)
        settings.save(UtilSettings.KEY_SMART_FOCUS_BIAS, true)

        settings.deleteLensSettings(LensWorkspace.DEFAULT_LENS_ID)
        settings.deleteLensSettings("")

        assertEquals(
            "The default lens shares the global keys - deleting it would wipe everyone's settings",
            2.0f,
            settings.getDistortionFactor(LensWorkspace.DEFAULT_LENS_ID),
            0.001f
        )
        assertTrue(settings.isSmartFocusBias(LensWorkspace.DEFAULT_LENS_ID))
    }
}
