package com.mckimquyen.feature.vip

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Instrumentation tests cho lint fix `PluralsCandidate`: `vip_entry_redeemed` and
 * `vip_activation_success_message` moved from a hardcoded `%d days` string to `<plurals>`.
 * Runs on a real device rather than Robolectric — Robolectric's `getQuantityString` shadow
 * for this project's test SDK level failed to resolve the "one"/"other" quantity items even
 * though the resources are correct (verified by lint 0 errors and a clean resource-link build),
 * so the real Android runtime is the trustworthy environment for this specific check.
 *
 * Forces an English configuration context regardless of the test device's system locale —
 * without this, a device set to e.g. Vietnamese (which has no plural distinction) would
 * correctly return the "ngày" text and this test would need locale-specific assertions instead.
 */
@RunWith(AndroidJUnit4::class)
class VipPluralsInstrumentedTest {

    private val resources = run {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(targetContext.resources.configuration)
        configuration.setLocale(Locale.ENGLISH)
        targetContext.createConfigurationContext(configuration).resources
    }

    @Test
    fun vipEntryRedeemed_usesSingularDayFormFor1() {
        assertEquals("VIP 1 day", resources.getQuantityString(R.plurals.vip_entry_redeemed, 1, 1))
    }

    @Test
    fun vipEntryRedeemed_usesPluralDaysFormForOthers() {
        assertEquals("VIP 3 days", resources.getQuantityString(R.plurals.vip_entry_redeemed, 3, 3))
        assertEquals("VIP 30 days", resources.getQuantityString(R.plurals.vip_entry_redeemed, 30, 30))
        assertEquals("VIP 0 days", resources.getQuantityString(R.plurals.vip_entry_redeemed, 0, 0))
    }

    @Test
    fun vipActivationSuccessMessage_usesSingularDayFormFor1() {
        assertEquals(
            "Congratulations! VIP has been successfully activated for 1 day.",
            resources.getQuantityString(R.plurals.vip_activation_success_message, 1, 1)
        )
    }

    @Test
    fun vipActivationSuccessMessage_usesPluralDaysFormForOthers() {
        assertEquals(
            "Congratulations! VIP has been successfully activated for 3 days.",
            resources.getQuantityString(R.plurals.vip_activation_success_message, 3, 3)
        )
    }
}
