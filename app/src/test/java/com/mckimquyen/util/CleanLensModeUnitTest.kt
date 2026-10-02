package com.mckimquyen.util

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class CleanLensModeUnitTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val settings = UtilSettings(context)

    @Test
    fun defaultCleanLensMode_isFalse() {
        assertFalse(
            "Clean lens mode must default to false",
            settings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE)
        )
    }

    @Test
    fun cleanLensMode_canBePersistedAndToggled() {
        try {
            settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, true)
            assertTrue(settings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE))

            settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, false)
            assertFalse(settings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE))
        } finally {
            settings.save(UtilSettings.KEY_CLEAN_LENS_MODE, UtilSettings.DEFAULT_CLEAN_LENS_MODE)
        }
    }
}
