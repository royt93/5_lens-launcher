package com.mckimquyen.ui.settings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.mckimquyen.services.AppEventManager
import com.mckimquyen.ui.FrmSettings
import com.mckimquyen.util.UtilSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class IconPackResetRefreshTest {
    @get:Rule val executor = InstantTaskExecutorRule()

    @Test
    fun resetClearsPackIdentityAndRequestsRefresh() {
        val settings = UtilSettings(RuntimeEnvironment.getApplication())
        settings.saveIconPackPackageName(PACKAGE_NAME)
        val fragment = FrmSettings()
        FrmSettings::class.java.getDeclaredField("utilSettings").apply { isAccessible = true }
            .set(fragment, settings)
        var refreshes = 0
        val observer = Observer<Any?> { refreshes++ }
        AppEventManager.appsUpdated.observeForever(observer)
        val baseline = refreshes
        try {
            FrmSettings::class.java.getDeclaredMethod("resetToDefault").apply { isAccessible = true }
                .invoke(fragment)
            assertEquals(UtilSettings.DEFAULT_ICON_PACK_PACKAGE_NAME,
                settings.getString(UtilSettings.KEY_ICON_PACK_PACKAGE_NAME))
            assertTrue("reset phải yêu cầu nạp lại icons đang hiển thị", refreshes > baseline)
        } finally {
            AppEventManager.appsUpdated.removeObserver(observer)
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.example.selectedicons"
    }
}
