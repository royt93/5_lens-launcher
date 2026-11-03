package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.model.LensPhysicsBackup
import com.mckimquyen.model.LensWorkspace
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** FISH-028: the hood is stored per lens and never inherited, so every old lens keeps the full reach. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LensHoodSettingsTest {
    private lateinit var context: Context
    private lateinit var settings: UtilSettings
    private val work = "work-lens-id"
    private val personal = "personal-lens-id"

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    private fun raw() = PreferenceManager.getDefaultSharedPreferences(context)

    @Test fun anUntouchedLensIsFullAndWritesNothing() {
        assertEquals(LensHood.FULL, settings.getLensHood(work), 0f)
        assertFalse(raw().contains("lens_hood_$work"))
    }

    @Test fun theDefaultLensUsesTheUnsuffixedKeyAndOthersAreSuffixed() {
        settings.saveLensHood(LensWorkspace.DEFAULT_LENS_ID, 0.4f)
        settings.saveLensHood(work, 0.6f)
        assertEquals(0.4f, raw().getFloat("lens_hood", -1f), 0f)
        assertEquals(0.6f, raw().getFloat("lens_hood_$work", -1f), 0f)
    }

    @Test fun aLensDoesNotInheritAnotherLensHood() {
        settings.saveLensHood(LensWorkspace.DEFAULT_LENS_ID, 0.4f)
        assertEquals(LensHood.FULL, settings.getLensHood(work), 0f)
        settings.saveLensHood(work, 0.6f)
        assertEquals(LensHood.FULL, settings.getLensHood(personal), 0f)
    }

    @Test fun badStoredValuesFallBackSafely() {
        raw().edit().putString("lens_hood_$work", "wide").commit()
        assertEquals("a wrong-typed value must read as FULL", LensHood.FULL, settings.getLensHood(work), 0f)
        raw().edit().remove("lens_hood_$work").putFloat("lens_hood_$work", Float.NaN).commit()
        assertEquals(LensHood.FULL, settings.getLensHood(work), 0f)
        raw().edit().putFloat("lens_hood_$work", 9f).commit()
        assertEquals(LensHood.FULL, settings.getLensHood(work), 0f)
        raw().edit().putFloat("lens_hood_$work", -3f).commit()
        assertEquals(LensHood.MIN, settings.getLensHood(work), 0f)
    }

    @Test fun savingClampsInsteadOfStoringAnOutOfRangeValue() {
        settings.saveLensHood(work, 7f)
        assertEquals(LensHood.FULL, raw().getFloat("lens_hood_$work", -1f), 0f)
        settings.saveLensHood(work, -1f)
        assertEquals(LensHood.MIN, raw().getFloat("lens_hood_$work", -1f), 0f)
    }

    @Test fun duplicatingALensCopiesItsHoodOnlyWhenOneIsStored() {
        settings.duplicateLensSettings(work, personal, copiedAppCount = 0)
        assertFalse("an unset hood must stay unset on the copy", raw().contains("lens_hood_$personal"))
        settings.saveLensHood(work, 0.45f)
        settings.duplicateLensSettings(work, personal, copiedAppCount = 0)
        assertEquals(0.45f, settings.getLensHood(personal), 0f)
    }

    @Test fun deletingALensRemovesItsHoodKey() {
        settings.saveLensHood(work, 0.5f)
        settings.deleteLensSettings(work)
        assertFalse(raw().contains("lens_hood_$work"))
    }

    @Test fun backupExportsOnlyAStoredHood() {
        assertNull(settings.getLensPhysicsBackup(work).lensHood)
        settings.saveLensHood(work, 0.5f)
        assertEquals(0.5f, settings.getLensPhysicsBackup(work).lensHood!!, 0f)
    }

    @Test fun backupJsonRoundTripsAndRejectsBadValues() {
        val physics = LensPhysicsBackup(lensHood = 0.55f)
        assertEquals(physics, LensPhysicsBackup.fromJson(physics.toJson()))
        // JSONObject itself refuses NaN/Infinity, so those can never reach fromJson through real JSON.
        for (bad in listOf(JSONObject().put("lensHood", 1.5), JSONObject().put("lensHood", 0.0), JSONObject().put("lensHood", "x"))) {
            assertNull("a bad hood must reject the physics block: $bad", LensPhysicsBackup.fromJson(bad))
        }
    }

    private fun importLens(vararg physics: Pair<String, LensPhysicsBackup?>) {
        val lenses = listOf(LensWorkspace.DEFAULT_LENS_ID to null as LensPhysicsBackup?) + physics
        val ok = settings.replaceWorkspacePreferences(
            lenses.map { (id, p) -> LensAppSettingsBackup(id, LensAppScope.ALL, emptySet(), false, emptySet(), p) },
            LensWorkspace.DEFAULT_LENS_ID
        )
        assertEquals(true, ok)
    }

    @Test fun importingABackupWritesTheHood() {
        settings.saveLensHood(work, 0.9f)
        importLens(work to LensPhysicsBackup(lensHood = 0.3f))
        assertEquals(0.3f, settings.getLensHood(work), 0f)
    }

    @Test fun importingABackupWithoutTheFieldClearsASecondaryLensHood() {
        settings.saveLensHood(work, 0.9f)
        importLens(work to LensPhysicsBackup())
        assertFalse(raw().contains("lens_hood_$work"))
    }

    @Test fun importingALegacyBackupWithoutPhysicsKeepsTheHood() {
        settings.saveLensHood(work, 0.9f)
        importLens(work to null)
        assertEquals(0.9f, settings.getLensHood(work), 0f)
    }
}
