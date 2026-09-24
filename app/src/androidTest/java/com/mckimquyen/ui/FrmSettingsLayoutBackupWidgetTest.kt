package com.mckimquyen.ui

import android.view.View
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
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
