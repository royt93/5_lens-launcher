package com.mckimquyen.ui

import android.view.View
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.R
import com.mckimquyen.search.PermissionAskedFlags
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** SEARCH-015 widget proof: the Settings row clears the "already asked" flags the engines read. */
@RunWith(AndroidJUnit4::class)
class FrmSettingsResetPermissionPromptsWidgetTest {
    @get:org.junit.Rule val deviceStateGuard = com.mckimquyen.testing.DeviceStateGuard()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clearPrefs() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun rowIsVisibleLabelledAndAtLeast48dpTall() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val row = fragment.requireView().findViewById<View>(R.id.llResetPermissionPrompts)
                assertTrue(row.isClickable)
                val label = fragment.requireView().findViewById<android.widget.TextView>(R.id.tvResetPermissionPrompts)
                assertEquals(context.getString(R.string.setting_reset_permission_prompts), label.text.toString())
                val minPx = 48 * context.resources.displayMetrics.density
                assertTrue("row must be a >=48dp touch target", row.minimumHeight >= minPx.toInt())
            }
        }
    }

    @Test
    fun tappingTheRowClearsEveryAskedFlag() {
        val settings = UtilSettings(context)
        PermissionAskedFlags.ALL_KEYS.forEach { settings.save(it, true) }

        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.requireView().findViewById<View>(R.id.llResetPermissionPrompts).performClick()
            }
        }

        PermissionAskedFlags.ALL_KEYS.forEach { assertFalse("$it must be cleared", settings.getBoolean(it)) }
    }

    @Test
    fun tappingWithNothingSetIsHarmless() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.requireView().findViewById<View>(R.id.llResetPermissionPrompts).performClick()
            }
        }

        PermissionAskedFlags.ALL_KEYS.forEach { assertFalse(UtilSettings(context).getBoolean(it)) }
    }
}
