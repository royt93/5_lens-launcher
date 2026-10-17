package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.model.SearchFilterPreset
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsSearchFilterTest {

    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun defaultFilterPresetIsAll() {
        assertEquals(SearchFilterPreset.ALL, settings.getSearchFilterPreset())
    }

    @Test
    fun saveAndRetrieveSearchFilterPreset() {
        settings.saveSearchFilterPreset(SearchFilterPreset.FAVORITES)
        assertEquals(SearchFilterPreset.FAVORITES, UtilSettings(context).getSearchFilterPreset())

        settings.saveSearchFilterPreset(SearchFilterPreset.THIS_LENS)
        assertEquals(SearchFilterPreset.THIS_LENS, UtilSettings(context).getSearchFilterPreset())

        settings.saveSearchFilterPreset(SearchFilterPreset.HIDDEN)
        assertEquals(SearchFilterPreset.HIDDEN, UtilSettings(context).getSearchFilterPreset())
    }

    @Test
    fun invalidStoredValueFallsBackToAll() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(UtilSettings.KEY_SEARCH_FILTER_PRESET, "CORRUPTED_VALUE")
            .commit()
        assertEquals(SearchFilterPreset.ALL, settings.getSearchFilterPreset())
    }
}
