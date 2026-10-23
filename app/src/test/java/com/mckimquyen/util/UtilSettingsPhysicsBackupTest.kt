package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.model.LensPhysicsBackup
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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsPhysicsBackupTest {

    private lateinit var context: Context
    private lateinit var settings: UtilSettings
    private val defaultLens = LensWorkspace.DEFAULT_LENS_ID
    private val workLens = "work"

    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        rawPrefs().edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun importingPhysicsWritesSpecifiedValuesAndClearsSecondaryOverrides() {
        settings.saveDistortionFactor(workLens, 3.0f)
        settings.saveIconSize(workLens, 40.0f)
        settings.saveCustomDistortionFactor(workLens, 4.0f)
        settings.saveSmartFocusBias(workLens, true)

        val defaultPhysics = LensPhysicsBackup(distortionFactor = 2.0f, iconSize = 25.0f)
        val workPhysics = LensPhysicsBackup(distortionFactor = 1.5f)
        assertTrue(settings.replaceWorkspacePreferences(
            listOf(
                LensAppSettingsBackup(defaultLens, LensAppScope.ALL, emptySet(), false, physics = defaultPhysics),
                LensAppSettingsBackup(workLens, LensAppScope.ALL, emptySet(), false, physics = workPhysics)
            ),
            defaultLens
        ))

        assertEquals(1.5f, settings.getDistortionFactor(workLens), 0f)
        assertFalse(rawPrefs().contains("${UtilSettings.KEY_ICON_SIZE}_$workLens"))
        assertFalse(rawPrefs().contains("${UtilSettings.KEY_CUSTOM_DISTORTION_FACTOR}_$workLens"))
        assertFalse(rawPrefs().contains("${UtilSettings.KEY_SMART_FOCUS_BIAS}_$workLens"))
        assertEquals(2.0f, settings.getDistortionFactor(defaultLens), 0f)
        assertEquals(25.0f, settings.getIconSize(defaultLens), 0f)
    }

    @Test
    fun defaultLensKeepsSharedKeysWhenPhysicsFieldsAreMissing() {
        settings.saveIconSize(defaultLens, 30.0f)
        assertTrue(settings.replaceWorkspacePreferences(
            listOf(LensAppSettingsBackup(
                defaultLens, LensAppScope.ALL, emptySet(), false,
                physics = LensPhysicsBackup(distortionFactor = 1.8f)
            )),
            defaultLens
        ))
        assertTrue(rawPrefs().contains(UtilSettings.KEY_ICON_SIZE))
        assertEquals(30.0f, settings.getIconSize(defaultLens), 0f)
    }

    @Test
    fun legacyBackupWithoutPhysicsLeavesPhysicsPreferencesUntouched() {
        settings.saveDistortionFactor(defaultLens, 2.2f)
        settings.saveDistortionFactor(workLens, 4.4f)
        assertTrue(settings.replaceWorkspacePreferences(
            listOf(
                LensAppSettingsBackup(defaultLens, LensAppScope.ALL, emptySet(), false),
                LensAppSettingsBackup(workLens, LensAppScope.ALL, emptySet(), false)
            ),
            defaultLens
        ))
        assertEquals(2.2f, settings.getDistortionFactor(defaultLens), 0f)
        assertEquals(4.4f, settings.getDistortionFactor(workLens), 0f)
    }

    @Test
    fun workspaceSnapshotCapturesPhysicsAndRestoreRemovesPreviouslyAbsentKeys() {
        settings.saveDistortionFactor(defaultLens, 2.2f)
        settings.saveIconSize(workLens, 33.0f)
        val snapshot = settings.snapshotWorkspacePreferences()
        assertTrue("snapshot must capture base physics key", snapshot.values.containsKey(UtilSettings.KEY_DISTORTION_FACTOR))
        assertTrue("snapshot must capture suffixed physics key", snapshot.values.containsKey("${UtilSettings.KEY_ICON_SIZE}_$workLens"))

        settings.saveDistortionFactor(defaultLens, 4.0f)
        settings.saveSmartFocusBias(workLens, true)
        assertTrue(settings.restoreWorkspacePreferences(snapshot))

        assertEquals(2.2f, settings.getDistortionFactor(defaultLens), 0f)
        assertFalse(rawPrefs().contains("${UtilSettings.KEY_SMART_FOCUS_BIAS}_$workLens"))
        assertFalse(snapshot.values.keys.any { it.startsWith("${UtilSettings.KEY_CUSTOM_DISTORTION_FACTOR}_") })
    }

    @Test
    fun emptyPhysicsObjectClearsSecondaryOverridesButNotDefaultKeys() {
        settings.saveIconSize(defaultLens, 27.0f)
        settings.saveIconSize(workLens, 40.0f)
        assertTrue(settings.replaceWorkspacePreferences(
            listOf(
                LensAppSettingsBackup(defaultLens, LensAppScope.ALL, emptySet(), false, physics = LensPhysicsBackup()),
                LensAppSettingsBackup(workLens, LensAppScope.ALL, emptySet(), false, physics = LensPhysicsBackup())
            ),
            defaultLens
        ))
        assertEquals(27.0f, settings.getIconSize(defaultLens), 0f)
        assertFalse(rawPrefs().contains("${UtilSettings.KEY_ICON_SIZE}_$workLens"))
    }
}
