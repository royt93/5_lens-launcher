package com.mckimquyen.ui

import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.util.UtilSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrmSettingsT9WidgetTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val settings get() = UtilSettings(context)
    private var hadKey = false
    private var originalState = false

    @Before
    fun setUp() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        hadKey = prefs.contains(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD)
        originalState = settings.getEnableT9SearchKeypad()
        settings.saveEnableT9SearchKeypad(false)
    }

    @After
    fun tearDown() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().apply {
            if (hadKey) {
                putBoolean(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD, originalState)
            } else {
                remove(UtilSettings.KEY_ENABLE_T9_SEARCH_KEYPAD)
            }
        }.commit()
    }

    @Test
    fun switchTogglesSettingValue() {
        launchFragmentInContainer<FrmSettings>(themeResId = R.style.AppTheme_NoActionBar).use { scenario ->
            scenario.onFragment { fragment ->
                val sw = fragment.requireView().findViewById<SwitchCompat>(R.id.swEnableT9SearchKeypad)
                assertNotNull("swEnableT9SearchKeypad must exist", sw)
                assertFalse("Default must be off", sw.isChecked)
                sw.isChecked = true
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assert(settings.getEnableT9SearchKeypad()) { "enabling switch must persist true" }
        }
    }
}
