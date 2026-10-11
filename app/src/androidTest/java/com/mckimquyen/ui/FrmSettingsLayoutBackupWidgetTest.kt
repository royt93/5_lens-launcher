package com.mckimquyen.ui

import android.view.View
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
                assertTrue(message.contains("workspace"))
                assertTrue(message.contains("1"))
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
                val rowExport = fragment.requireView().findViewById<View>(R.id.llExportLayout)
                val rowImport = fragment.requireView().findViewById<View>(R.id.llImportLayout)
                assertTrue("export row must have a click listener wired", rowExport.hasOnClickListeners())
                assertTrue("import row must have a click listener wired", rowImport.hasOnClickListeners())
            }
        }
    }
}
