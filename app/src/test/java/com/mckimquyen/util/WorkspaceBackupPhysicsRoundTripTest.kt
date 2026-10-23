package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupLens
import com.mckimquyen.model.LensPhysicsBackup
import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** DB-007: pipeline wiring through SnapshotFactory, ImportApplier, and raw export reads. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class WorkspaceBackupPhysicsRoundTripTest {

    private lateinit var context: Context
    private lateinit var settings: UtilSettings
    private val defaultLens = LensWorkspace.DEFAULT_LENS_ID
    private val workLens = "work"

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun snapshotFactoryTransfersPhysicsFromSettingsToBackupLenses() {
        val lenses = listOf(
            LensWorkspace(defaultLens, "Home", 0, 1L),
            LensWorkspace(workLens, "Work", 1, 2L)
        )
        val defaultPhysics = LensPhysicsBackup(distortionFactor = 1.2f)
        val workPhysics = LensPhysicsBackup(distortionFactor = 3.4f, smartFocusBias = true)

        val appSettings = listOf(
            LensAppSettingsBackup(defaultLens, LensAppScope.ALL, emptySet(), false, physics = defaultPhysics),
            LensAppSettingsBackup(workLens, LensAppScope.ALL, emptySet(), false, physics = workPhysics)
        )

        val backup = LayoutBackupSnapshotFactory.create(
            lenses = lenses,
            rows = emptyList(),
            appSettings = appSettings,
            exportedAt = 1000L
        )

        assertEquals(defaultPhysics, backup.lenses.single { it.id == defaultLens }.physics)
        assertEquals(workPhysics, backup.lenses.single { it.id == workLens }.physics)
    }

    @Test
    fun importApplierPlanV2PassesPhysicsThroughToAppSettingsBackup() {
        val physics = LensPhysicsBackup(iconSize = 28.0f)
        val lens = LayoutBackupLens(
            id = workLens,
            name = "Work",
            orderIndex = 1,
            scope = LensAppScope.ALL,
            selectedAppIds = emptySet(),
            frozen = false,
            entries = emptyList(),
            physics = physics
        )

        val plan = LayoutImportApplier.planV2(
            LayoutBackup(
                schemaVersion = 2,
                exportedAt = 1000L,
                activeLensId = workLens,
                lenses = listOf(
                    LayoutBackupLens(defaultLens, "Home", 0, LensAppScope.ALL, emptySet(), false, emptyList()),
                    lens
                )
            )
        )

        val workPlan = plan.lenses.single { it.id == workLens }
        assertEquals(physics, workPlan.toAppSettingsBackup().physics)
    }

    @Test
    fun legacyPlanV2WithoutPhysicsHasNullPhysicsOnAppSettings() {
        val plan = LayoutImportApplier.planV2(
            LayoutBackup(
                schemaVersion = 2,
                exportedAt = 1000L,
                activeLensId = defaultLens,
                lenses = listOf(
                    LayoutBackupLens(defaultLens, "Home", 0, LensAppScope.ALL, emptySet(), false, emptyList(), physics = null)
                )
            )
        )
        assertNull(plan.lenses.single().toAppSettingsBackup().physics)
    }

    @Test
    fun rawExportExtractsOnlyKeysPresentInPrefsWithoutClampingOrInheriting() {
        // defaultLens: có distortion_factor và min_icon_size (do init tự ghi)
        settings.saveDistortionFactor(defaultLens, 2.5f)
        // workLens: có smart_focus_bias, không có distortion
        settings.saveSmartFocusBias(workLens, true)

        val defaultBackup = settings.getLensPhysicsBackup(defaultLens)
        val workBackup = settings.getLensPhysicsBackup(workLens)

        assertEquals(2.5f, defaultBackup.distortionFactor)
        // UtilSettings.init materializes the shared min_icon_size, so the default lens always has it.
        assertEquals(settings.autoDefaultIconSize, defaultBackup.iconSize)
        assertNull(defaultBackup.smartFocusBias)

        assertEquals(true, workBackup.smartFocusBias)
        assertNull("work không override distortion thì không được kế thừa giá trị 2.5 của default", workBackup.distortionFactor)
        assertNull(workBackup.iconSize)
    }
}
