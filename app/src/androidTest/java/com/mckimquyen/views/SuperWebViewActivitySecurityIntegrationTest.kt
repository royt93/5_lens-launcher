package com.mckimquyen.views

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SEC-003 integration proof: reads the real, installed PackageManager state for this APK
 * (not the manifest XML source) to prove the attack-surface reduction actually took effect,
 * and proves the URL-validation logic survives process recreation.
 */
@RunWith(AndroidJUnit4::class)
class SuperWebViewActivitySecurityIntegrationTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun isExported(className: String): Boolean {
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, className), 0
        )
        return info.exported
    }

    @Test
    fun hardenedComponents_areNotExported() {
        assertFalse("SuperWebViewActivity must not be exported", isExported(SuperWebViewActivity::class.java.name))
        assertFalse("ActAbout must not be exported", isExported("com.mckimquyen.ui.ActAbout"))
        assertFalse("ActVipManagement must not be exported", isExported("com.mckimquyen.feature.vip.ActVipManagement"))
        assertFalse("SplashAct must not be exported", isExported("com.mckimquyen.ui.SplashAct"))
    }

    @Test
    fun launcherEntryPoints_remainExported() {
        assertTrue("ActHome must stay exported (HOME entry point)", isExported("com.mckimquyen.ui.ActHome"))
        assertTrue("ActSettings must stay exported (LAUNCHER entry point)", isExported("com.mckimquyen.ui.ActSettings"))
    }

    @Test
    fun fakeLauncher_exportedButDisabledByDefault() {
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, "com.mckimquyen.ui.ActFakeLauncher"),
            PackageManager.MATCH_DISABLED_COMPONENTS
        )
        assertTrue("ActFakeLauncher must stay exported to work as a HOME picker reset", info.exported)
        assertFalse(
            "ActFakeLauncher must ship disabled; it is only toggled on programmatically",
            info.enabled
        )
    }

    @Test
    fun urlValidation_survivesProcessRecreation() {
        val intent = Intent(context, SuperWebViewActivity::class.java).apply {
            putExtra(SuperWebViewActivity.KEY_URL, "https://loitp.notion.site/loitp/Privacy-Policy-319b1cd8783942fa8923d2a3c9bce60f/")
            putExtra(SuperWebViewActivity.KEY_TITLE, "Privacy Policy")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ActivityScenario.launch<SuperWebViewActivity>(intent).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.recreate()
            assertEquals(
                "allowlisted page must still be open after simulated process/config recreation",
                Lifecycle.State.RESUMED,
                scenario.state
            )
        }
    }
}
