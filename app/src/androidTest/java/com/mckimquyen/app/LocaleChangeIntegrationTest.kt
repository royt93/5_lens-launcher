package com.mckimquyen.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.services.AppEventManager
import com.mckimquyen.testing.DeviceStateGuard
import com.mckimquyen.util.LocaleHelper
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CORE-006: Integration test proving system ACTION_LOCALE_CHANGED and in-app locale switch
 * trigger app refresh notifications on a real Android device.
 */
@RunWith(AndroidJUnit4::class)
class LocaleChangeIntegrationTest {

    @get:Rule
    val deviceStateGuard = DeviceStateGuard()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private var originalLanguage: String = "en"

    @Before
    fun setUp() {
        originalLanguage = LocaleHelper.getLanguage(context)
        LocaleHelper.setLocale(context, "en")
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @After
    fun tearDown() {
        LocaleHelper.setLocale(context, originalLanguage)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    fun applicationRegistersLocaleReceiver() {
        val app = context as RApplication
        assertNotNull("RApplication must register dynamic locale receiver", app.localeReceiverForTest)
    }

    @Test
    fun sendingLocaleChangedBroadcastTriggersAppsUpdated() {
        val app = context as RApplication
        val receiver = requireNotNull(app.localeReceiverForTest) { "localeReceiver must not be null" }

        val sentinel = Any()
        AppEventManager.notifyAppsUpdated(sentinel)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val intent = Intent(Intent.ACTION_LOCALE_CHANGED)
        receiver.onReceive(context, intent)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertTrue(
            "ACTION_LOCALE_CHANGED must publish new appsUpdated event",
            AppEventManager.appsUpdated.value !== sentinel
        )
    }

    @Test
    fun changingInAppLocaleTriggersAppsUpdated() {
        val sentinel = Any()
        AppEventManager.notifyAppsUpdated(sentinel)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        LocaleHelper.setLocale(context, "vi")
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertTrue(
            "Changing in-app locale must publish new appsUpdated event",
            AppEventManager.appsUpdated.value !== sentinel
        )
    }
}
