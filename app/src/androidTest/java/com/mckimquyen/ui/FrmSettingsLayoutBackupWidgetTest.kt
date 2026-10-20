package com.mckimquyen.ui

import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupEntry
import com.mckimquyen.model.LayoutBackupLens
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.util.LensAppScope
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FEAT-006: click wiring proof - a real Fragment attached to a real ActSettings, so
 * onViewCreated has actually run and set the listeners FrmSettingsMaterialYouWidgetTest's raw
 * inflate cannot reach. The launched SAF Intent itself and the preview dialog are proven by
 * LayoutBackupIoIntegrationTest (real file/Room round trip) and this story's real-device Smoke
 * section instead - there is no espresso-intents dependency in this project to assert on a
 * launched Intent without one, and adding it for one test isn't worth a new dependency.
 */
@RunWith(AndroidJUnit4::class)
class FrmSettingsLayoutBackupWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()


    @Test
    fun v2PreviewWarnsAndCancelDoesNotChangeWorkspace() {
        val backup = LayoutBackup(
            LayoutBackup.CURRENT_SCHEMA_VERSION,
            0L,
            activeLensId = "default",
            lenses = listOf(
                LayoutBackupLens(
                    LensWorkspace.DEFAULT_LENS_ID, "Backup", 0,
                    LensAppScope.SELECTED, setOf("com.example-com.example.Main"), true,
                    listOf(LayoutBackupEntry("com.example", "com.example.Main", 0, true, true, false, null, PinnedZone.NONE.name))
                )
            )
        )
        ActivityScenario.launch(ActSettings::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.showLayoutImportPreview(backup)
                val dialog = activity.layoutImportDialog ?: throw AssertionError("v2 preview missing")
                assertTrue(dialog.isShowing)
                val message = dialog.findViewById<android.widget.TextView>(android.R.id.message)?.text.toString()
                assertEquals(activity.getString(R.string.layout_import_workspace_preview_message, 1, 1), message)
                val originalActive = UtilSettings(activity).getString(UtilSettings.KEY_ACTIVE_LENS_ID)
                dialog.cancel()
                assertEquals("Cancel must not change active lens", originalActive,
                    UtilSettings(activity).getString(UtilSettings.KEY_ACTIVE_LENS_ID))
            }
        }
    }

    @Test
    fun exportAndImportRows_areClickable_onARealAttachedFragment() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val recentIcon = fragment.requireView().findViewById<ImageView>(R.id.iconRecentAppsPanel)
                assertTrue("recent-app icon must be tinted", recentIcon.imageTintList != null)
                val rowExport = fragment.requireView().findViewById<View>(R.id.llExportLayout)
                val rowImport = fragment.requireView().findViewById<View>(R.id.llImportLayout)
                assertTrue("export row must have a click listener wired", rowExport.hasOnClickListeners())
                assertTrue("import row must have a click listener wired", rowImport.hasOnClickListeners())
                val exportIconId = fragment.resources.getIdentifier(
                    "iconExportLayout", "id", fragment.requireContext().packageName
                )
                val importIconId = fragment.resources.getIdentifier(
                    "iconImportLayout", "id", fragment.requireContext().packageName
                )
                assertTrue("export icon must have a stable test/accessibility id", exportIconId != 0)
                assertTrue("import icon must have a stable test/accessibility id", importIconId != 0)
                val exportIcon = rowExport.findViewById<ImageView>(exportIconId)
                val importIcon = rowImport.findViewById<ImageView>(importIconId)
                assertNotNull("export icon must use tint", exportIcon.imageTintList)
                assertNotNull("import icon must use tint", importIcon.imageTintList)
                assertNotNull("recent-app icon must use tint", recentIcon.imageTintList)
                val exportTint = exportIcon.imageTintList?.defaultColor ?: throw AssertionError("export tint missing")
                val importTint = importIcon.imageTintList?.defaultColor ?: throw AssertionError("import tint missing")
                assertEquals("export/import icon tints must match", exportTint, importTint)
                assertEquals(exportTint, recentIcon.imageTintList?.defaultColor)

            }
        }
    }
}
