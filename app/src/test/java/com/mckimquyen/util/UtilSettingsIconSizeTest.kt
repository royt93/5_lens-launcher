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
 * FISH-018: per-lens icon size. Same contract as the per-lens distortion factor
 * ([UtilSettingsPerLensTest]): the default lens keeps the legacy unsuffixed key, a lens with no
 * override inherits the shared value, and once a lens has its own value no other lens moves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsIconSizeTest {

    // Set in @Before before every test; JUnit never calls a @Test without running setup.
    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    private val work = "work-lens-id"
    private val personal = "personal-lens-id"

    private val maxAllowed = UtilSettings.MAX_ICON_SIZE.toFloat() + UtilSettings.MIN_ICON_SIZE

    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        rawPrefs().edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun `the key is stable`() {
        assertEquals("min_icon_size", UtilSettings.KEY_ICON_SIZE)
    }

    @Test
    fun `a lens with no override inherits the shared value`() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        assertEquals(30f, settings.getIconSize(work), 0.001f)
        assertEquals(30f, settings.getIconSize(personal), 0.001f)
    }

    @Test
    fun `saving for one lens does not change another lens or the shared value`() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 30f)
        settings.saveIconSize(work, 50f)

        assertEquals(50f, settings.getIconSize(work), 0.001f)
        assertEquals(30f, settings.getIconSize(personal), 0.001f)
        assertEquals(30f, settings.getFloat(UtilSettings.KEY_ICON_SIZE), 0.001f)
    }

    @Test
    fun `the default lens reads and writes the legacy unsuffixed key`() {
        settings.saveIconSize(LensWorkspace.DEFAULT_LENS_ID, 45f)

        assertEquals(45f, rawPrefs().getFloat(UtilSettings.KEY_ICON_SIZE, -1f), 0.001f)
        assertEquals(45f, settings.getFloat(UtilSettings.KEY_ICON_SIZE), 0.001f)
        assertFalse(rawPrefs().contains("${UtilSettings.KEY_ICON_SIZE}_${LensWorkspace.DEFAULT_LENS_ID}"))
    }

    @Test
    fun `null and empty lens ids use the shared key`() {
        settings.saveIconSize(null, 33f)
        assertEquals(33f, settings.getFloat(UtilSettings.KEY_ICON_SIZE), 0.001f)
        settings.saveIconSize("", 34f)
        assertEquals(34f, settings.getFloat(UtilSettings.KEY_ICON_SIZE), 0.001f)
        assertEquals(34f, settings.getIconSize(null), 0.001f)
        assertEquals(34f, settings.getIconSize(""), 0.001f)
    }

    @Test
    fun `a non default lens stores its value under the suffixed key`() {
        settings.saveIconSize(work, 51f)
        assertEquals(51f, rawPrefs().getFloat("${UtilSettings.KEY_ICON_SIZE}_$work", -1f), 0.001f)
    }

    @Test
    fun `a value below the minimum is clamped up to the minimum`() {
        settings.saveIconSize(work, UtilSettings.MIN_ICON_SIZE - 5f)
        assertEquals(UtilSettings.MIN_ICON_SIZE, settings.getIconSize(work), 0.001f)
    }

    @Test
    fun `a value above the maximum is clamped down to the maximum`() {
        settings.saveIconSize(work, maxAllowed + 50f)
        assertEquals(maxAllowed, settings.getIconSize(work), 0.001f)
    }

    @Test
    fun `a wrong typed per lens value falls back instead of throwing`() {
        rawPrefs().edit().putString("${UtilSettings.KEY_ICON_SIZE}_$work", "huge").commit()
        val result = settings.getIconSize(work)
        assertTrue("fell back to a value inside the allowed range", result in UtilSettings.MIN_ICON_SIZE..maxAllowed)
    }

    @Test
    fun `a wrong typed inherited shared value falls back instead of throwing`() {
        rawPrefs().edit().putString(UtilSettings.KEY_ICON_SIZE, "huge").commit()
        val result = settings.getIconSize(work)
        assertTrue("fell back to a value inside the allowed range", result in UtilSettings.MIN_ICON_SIZE..maxAllowed)
    }

    @Test
    fun `a stored NaN falls back to a finite value`() {
        settings.saveIconSize(work, Float.NaN)
        val result = settings.getIconSize(work)
        assertTrue("NaN must not reach LensGridCache", result.isFinite())
        assertTrue("fallback stays inside the allowed range", result in UtilSettings.MIN_ICON_SIZE..maxAllowed)
    }

    @Test
    fun `duplicate copies the effective value of an inherited source`() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 44f)
        settings.duplicateLensSettings(work, personal)

        assertEquals(44f, rawPrefs().getFloat("${UtilSettings.KEY_ICON_SIZE}_$personal", -1f), 0.001f)
        // Materialized: a later change of the shared value no longer moves the copy.
        settings.save(UtilSettings.KEY_ICON_SIZE, 20f)
        assertEquals(44f, settings.getIconSize(personal), 0.001f)
    }

    @Test
    fun `duplicate copies an explicit source override`() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 44f)
        settings.saveIconSize(work, 52f)
        settings.duplicateLensSettings(work, personal)
        assertEquals(52f, settings.getIconSize(personal), 0.001f)
    }

    @Test
    fun `delete removes the override so the lens inherits again`() {
        settings.save(UtilSettings.KEY_ICON_SIZE, 44f)
        settings.saveIconSize(work, 52f)
        settings.deleteLensSettings(work)

        assertFalse(rawPrefs().contains("${UtilSettings.KEY_ICON_SIZE}_$work"))
        assertEquals(44f, settings.getIconSize(work), 0.001f)
    }

    @Test
    fun `deleting the default lens never removes the shared key`() {
        settings.saveIconSize(LensWorkspace.DEFAULT_LENS_ID, 46f)
        settings.deleteLensSettings(LensWorkspace.DEFAULT_LENS_ID)
        assertEquals(46f, settings.getFloat(UtilSettings.KEY_ICON_SIZE), 0.001f)
    }
}
