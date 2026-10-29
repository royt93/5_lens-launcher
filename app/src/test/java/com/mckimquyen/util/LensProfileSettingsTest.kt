package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.model.LensPhysicsBackup
import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** FISH-027: the lens profile is stored per lens and never inherited, so old installs stay on FISHEYE. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LensProfileSettingsTest {
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

    @Test fun anUntouchedLensIsFisheyeAndWritesNothing() {
        assertEquals(LensProfile.FISHEYE, settings.getLensProfile(work))
        assertFalse(raw().contains("lens_profile_$work"))
    }

    @Test fun theDefaultLensUsesTheUnsuffixedKeyAndOthersAreSuffixed() {
        settings.saveLensProfile(LensWorkspace.DEFAULT_LENS_ID, LensProfile.ANAMORPHIC)
        settings.saveLensProfile(work, LensProfile.CYLINDRICAL)
        assertEquals("ANAMORPHIC", raw().getString("lens_profile", null))
        assertEquals("CYLINDRICAL", raw().getString("lens_profile_$work", null))
    }

    @Test fun aLensDoesNotInheritAnotherLensProfile() {
        settings.saveLensProfile(LensWorkspace.DEFAULT_LENS_ID, LensProfile.ANAMORPHIC)
        assertEquals(LensProfile.FISHEYE, settings.getLensProfile(work))
        settings.saveLensProfile(work, LensProfile.CYLINDRICAL)
        assertEquals(LensProfile.FISHEYE, settings.getLensProfile(personal))
    }

    @Test fun aWrongTypedStoredValueFallsBackToFisheye() {
        raw().edit().putInt("lens_profile_$work", 7).commit()
        assertEquals(LensProfile.FISHEYE, settings.getLensProfile(work))
    }

    @Test fun duplicatingALensCopiesItsProfile() {
        settings.saveLensProfile(work, LensProfile.CYLINDRICAL)
        settings.duplicateLensSettings(work, personal)
        assertEquals(LensProfile.CYLINDRICAL, settings.getLensProfile(personal))
    }

    @Test fun deletingALensRemovesItsProfileKey() {
        settings.saveLensProfile(work, LensProfile.ANAMORPHIC)
        settings.deleteLensSettings(work)
        assertFalse(raw().contains("lens_profile_$work"))
    }

    @Test fun backupExportsOnlyAStoredProfile() {
        assertNull(settings.getLensPhysicsBackup(work).lensProfile)
        settings.saveLensProfile(work, LensProfile.ANAMORPHIC)
        assertEquals("ANAMORPHIC", settings.getLensPhysicsBackup(work).lensProfile)
    }

    @Test fun backupJsonRoundTripsAndRejectsUnknownNames() {
        val physics = LensPhysicsBackup(lensProfile = "CYLINDRICAL")
        assertEquals(physics, LensPhysicsBackup.fromJson(physics.toJson()))
        val bad = org.json.JSONObject().put("lensProfile", "WIDE")
        assertNull("an unknown profile name must reject the physics block", LensPhysicsBackup.fromJson(bad))
        val wrongType = org.json.JSONObject().put("lensProfile", 3)
        assertNull(LensPhysicsBackup.fromJson(wrongType))
    }

    private fun importLens(vararg physics: Pair<String, LensPhysicsBackup?>) {
        val lenses = listOf(LensWorkspace.DEFAULT_LENS_ID to null as LensPhysicsBackup?) + physics
        val ok = settings.replaceWorkspacePreferences(
            lenses.map { (id, p) -> LensAppSettingsBackup(id, LensAppScope.ALL, emptySet(), false, emptySet(), p) },
            LensWorkspace.DEFAULT_LENS_ID
        )
        assertEquals(true, ok)
    }

    @Test fun importingABackupWritesTheProfile() {
        settings.saveLensProfile(work, LensProfile.CYLINDRICAL)
        importLens(work to LensPhysicsBackup(lensProfile = "ANAMORPHIC"))
        assertEquals(LensProfile.ANAMORPHIC, settings.getLensProfile(work))
    }

    @Test fun importingABackupWithoutTheFieldClearsASecondaryLensProfile() {
        settings.saveLensProfile(work, LensProfile.CYLINDRICAL)
        importLens(work to LensPhysicsBackup())
        assertFalse(raw().contains("lens_profile_$work"))
    }

    @Test fun importingALegacyBackupWithoutPhysicsKeepsTheProfile() {
        settings.saveLensProfile(work, LensProfile.CYLINDRICAL)
        importLens(work to null)
        assertEquals(LensProfile.CYLINDRICAL, settings.getLensProfile(work))
    }
}
