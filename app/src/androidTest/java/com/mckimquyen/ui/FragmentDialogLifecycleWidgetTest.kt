package com.mckimquyen.ui

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Dialog windows retained by fragments must close when their view/activity is recreated. */
@RunWith(AndroidJUnit4::class)
class FragmentDialogLifecycleWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    @Test
    fun searchHintDialogIsDismissedAndReleasedWhenSettingsViewIsRecreated() {
        var oldDialog: androidx.appcompat.app.AlertDialog? = null
        launchFragmentInContainer<FrmSettings>(themeResId = com.mckimquyen.R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                // Real user path: the same row click that opens the search-hint dialog in production.
                fragment.requireView().findViewById<android.view.View>(com.mckimquyen.R.id.llSearchHintText).performClick()
                val dialog = requireNotNull(searchHintField().get(fragment) as androidx.appcompat.app.AlertDialog)
                oldDialog = dialog
                assertTrue(dialog.isShowing)
            }
            scenario.recreate()
            scenario.onFragment { fragment ->
                assertFalse("search hint dialog must dismiss on view recreation", requireNotNull(oldDialog).isShowing)
                assertNull("fragment must release dismissed dialog reference", searchHintField().get(fragment))
            }
        }
    }

    @Test
    fun pendingUninstallDialogIsDismissedAndReleasedWhenAppsViewIsRecreated() {
        var oldDialog: androidx.appcompat.app.AlertDialog? = null
        launchFragmentInContainer<FrmApps>(themeResId = com.mckimquyen.R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val dialog = MaterialAlertDialogBuilder(fragment.requireContext())
                    .setTitle("Uninstall lifecycle")
                    .setMessage("test")
                    .show()
                oldDialog = dialog
                pendingUninstallField().set(fragment, dialog)
                assertTrue(dialog.isShowing)
            }
            scenario.recreate()
            scenario.onFragment { fragment ->
                assertFalse("pending uninstall dialog must dismiss on view recreation", requireNotNull(oldDialog).isShowing)
                assertNull("fragment must release dismissed dialog reference", pendingUninstallField().get(fragment))
            }
        }
    }

    private fun searchHintField() = FrmSettings::class.java.getDeclaredField("searchHintDialog")
        .apply { isAccessible = true }

    private fun pendingUninstallField() = FrmApps::class.java.getDeclaredField("pendingUninstallDialog")
        .apply { isAccessible = true }
}
