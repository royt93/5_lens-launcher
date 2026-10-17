package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.model.EdgeSwipeMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsEdgeSwipeTest {

    private lateinit var context: Context
    private lateinit var settings: UtilSettings

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun defaultEdgeSwipeModeIsBoth() {
        assertEquals(EdgeSwipeMode.BOTH, settings.getEdgeSwipeMode())
    }

    @Test
    fun saveAndRetrieveAllEdgeSwipeModes() {
        for (mode in EdgeSwipeMode.entries) {
            settings.saveEdgeSwipeMode(mode)
            assertEquals(mode, UtilSettings(context).getEdgeSwipeMode())
        }
    }

    @Test
    fun corruptValueFallsBackSafelyToBoth() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(UtilSettings.KEY_EDGE_SWIPE_LENS_SWITCH, "NOT_AN_ENUM")
            .commit()
        assertEquals(EdgeSwipeMode.BOTH, settings.getEdgeSwipeMode())
    }
}
