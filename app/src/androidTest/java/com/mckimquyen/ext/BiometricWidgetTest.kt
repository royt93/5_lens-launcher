package com.mckimquyen.ext

import androidx.biometric.BiometricManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Coverage gap found by whole-codebase audit sweep: Biometric.kt backs app-lock authentication
 * (security-relevant) and had zero test at any tier before this.
 *
 * Scope note: only [Biometric.isHaveBiometric] is covered here. `toggleLockApp`'s
 * authentication-result callback lives inside a private, anonymous
 * `BiometricPrompt.AuthenticationCallback` - exercising it for real needs enrolled biometric
 * hardware (not available on the test devices this project uses), and
 * `BiometricPrompt.AuthenticationResult` has only a package-private constructor in the androidx
 * library, so testing it here would mean either a production refactor purely for testability or
 * a fragile double-reflection test into androidx internals. Left untested rather than doing
 * either.
 */
@RunWith(AndroidJUnit4::class)
class BiometricWidgetTest {

    @Test
    fun isHaveBiometric_matchesBiometricManagerDirectly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

        assertEquals(expected, Biometric.isHaveBiometric(context))
    }
}
