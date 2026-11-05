package com.mckimquyen.util

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LensUnlockAuthenticatorTest {
    private val context = RuntimeEnvironment.getApplication()
    private val activity = Mockito.mock(AppCompatActivity::class.java)
    private val platform = FakePlatform()
    private lateinit var authenticator: SystemLensUnlockAuthenticator

    @Before fun setup() {
        Mockito.`when`(activity.getString(R.string.verify_your_identity)).thenReturn("Verify identity")
        Mockito.`when`(activity.getString(R.string.lens_unlock_description, "Private")).thenReturn("Unlock Private")
        Mockito.`when`(activity.getString(R.string.lens_unlock_use_device_credential)).thenReturn("Use device PIN")
        Mockito.`when`(activity.getString(R.string.cancel)).thenReturn("Cancel")
        authenticator = SystemLensUnlockAuthenticator(platform).also {
            it.credentialLauncher = { platform.credentialLaunches++ }
        }
    }

    @Test fun api30UsesCombinedAuthenticatorsWithoutNegativeAction() {
        platform.sdk = 30
        platform.biometricStatus = BiometricManager.BIOMETRIC_SUCCESS
        platform.deviceSecure = true
        assertTrue(authenticator.isAvailable(context))

        var result: LensUnlockResult? = null
        authenticator.authenticate(activity, "Private") { result = it }

        assertEquals(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            platform.allowedAuthenticators)
        assertEquals(null, platform.negativeButtonText)
        platform.biometricCallback?.onAuthenticationSucceeded(Mockito.mock(BiometricPrompt.AuthenticationResult::class.java))
        assertEquals(LensUnlockResult.SUCCESS, result)
        assertEquals(0, platform.credentialLaunches)
    }

    @Test fun api28UsesStrongBiometricAndNegativeActionLaunchesDeviceCredential() {
        platform.sdk = 28
        platform.biometricStatus = BiometricManager.BIOMETRIC_SUCCESS
        platform.deviceSecure = true
        var result: LensUnlockResult? = null
        authenticator.authenticate(activity, "Private") { result = it }

        assertEquals(BiometricManager.Authenticators.BIOMETRIC_STRONG, platform.allowedAuthenticators)
        assertEquals("Use device PIN", platform.negativeButtonText)
        platform.biometricCallback?.onAuthenticationError(BiometricPrompt.ERROR_NEGATIVE_BUTTON, "Use device PIN")
        assertEquals(1, platform.credentialLaunches)
        authenticator.onCredentialResult(true)
        assertEquals(LensUnlockResult.SUCCESS, result)
    }

    @Test fun api29UsesStrongBiometricOnlyAndCancelDoesNotUnlock() {
        platform.sdk = 29
        platform.biometricStatus = BiometricManager.BIOMETRIC_SUCCESS
        platform.deviceSecure = false
        var result: LensUnlockResult? = null
        authenticator.authenticate(activity, "Private") { result = it }
        assertEquals(BiometricManager.Authenticators.BIOMETRIC_STRONG, platform.allowedAuthenticators)
        platform.biometricCallback?.onAuthenticationError(BiometricPrompt.ERROR_USER_CANCELED, "cancelled")
        assertEquals(LensUnlockResult.CANCELLED, result)
    }

    @Test fun secureCredentialIsUsedDirectlyWhenBiometricUnavailable() {
        platform.biometricStatus = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        platform.deviceSecure = true
        var result: LensUnlockResult? = null
        authenticator.authenticate(activity, "Private") { result = it }
        assertEquals(1, platform.credentialLaunches)
        authenticator.onCredentialResult(false)
        assertEquals(LensUnlockResult.CANCELLED, result)
    }

    @Test fun unavailableAndPromptErrorsNeverReportSuccess() {
        platform.biometricStatus = BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        platform.deviceSecure = false
        assertFalse(authenticator.isAvailable(context))
        var result: LensUnlockResult? = null
        authenticator.authenticate(activity, "Private") { result = it }
        assertEquals(LensUnlockResult.UNAVAILABLE, result)

        platform.biometricStatus = BiometricManager.BIOMETRIC_SUCCESS
        platform.deviceSecure = false
        authenticator.authenticate(activity, "Private") { result = it }
        platform.biometricCallback?.onAuthenticationError(BiometricPrompt.ERROR_HW_UNAVAILABLE, "unavailable")
        assertEquals(LensUnlockResult.ERROR, result)
    }

    private class FakePlatform : LensUnlockPlatform {
        var sdk = 28
        var biometricStatus = BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        var deviceSecure = false
        var allowedAuthenticators = 0
        var negativeButtonText: String? = null
        var biometricCallback: BiometricPrompt.AuthenticationCallback? = null
        var credentialLaunches = 0

        override fun sdkInt() = sdk
        override fun canAuthenticate(context: Context, authenticators: Int) = biometricStatus
        override fun isDeviceSecure(context: Context) = deviceSecure
        override fun createCredentialIntent(activity: AppCompatActivity): Intent? = Intent("credential")
        override fun authenticateBiometric(
            activity: AppCompatActivity,
            title: String,
            description: String,
            authenticators: Int,
            negativeButtonText: String?,
            callback: BiometricPrompt.AuthenticationCallback
        ) {
            allowedAuthenticators = authenticators
            this.negativeButtonText = negativeButtonText
            biometricCallback = callback
        }
    }
}
