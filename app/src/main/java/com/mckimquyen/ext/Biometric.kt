package com.mckimquyen.ext

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.mckimquyen.R

object Biometric {
    /**
     * Checks if biometric authentication is available on the device.
     * Uses BIOMETRIC_STRONG for enhanced security.
     *
     * @param c Context
     * @return true if biometric authentication is available and enrolled, false otherwise
     */
    fun isHaveBiometric(
        c: Context
    ): Boolean {
        val biometricManager = BiometricManager.from(c)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    fun toggleLockApp(
        a: AppCompatActivity,
        appName: String,
        packageName: String,
        isAppLock: Boolean,
        onAuthenticationSucceeded: ((String, Boolean) -> Unit),
    ) {
        val description = if (isAppLock) {
            "Lock $appName?\n\npackageName: $packageName"
        } else {
            "Unlock $appName?\n\npackageName: $packageName"
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(a.getString(R.string.verify_your_identity))
            .setDescription(description)
            .setNegativeButtonText(a.getString(R.string.cancel)).build()
        instanceOfBiometricPrompt(
            activity = a,
            packageName = packageName,
            isAppLock = isAppLock,
            onAuthenticationSucceeded = onAuthenticationSucceeded,
        ).authenticate(
            promptInfo
        )

    }

    private fun instanceOfBiometricPrompt(
        activity: AppCompatActivity,
        packageName: String,
        isAppLock: Boolean,
        onAuthenticationSucceeded: ((String, Boolean) -> Unit),
    ): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.d("BiometricPrompt", "onAuthenticationError $errorCode $errString")

                // Show error toast to user (skip for user cancellation)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    val errorMessage = activity.getString(
                        R.string.biometric_error_generic,
                        errString
                    )
                    Toast.makeText(activity, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.d("BiometricPrompt", "onAuthenticationFailed")

                // Show failed toast to user
                val failedMessage = activity.getString(R.string.biometric_error_authentication_failed)
                Toast.makeText(activity, failedMessage, Toast.LENGTH_SHORT).show()
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                if (isAppLock) {
                    onAuthenticationSucceeded.invoke(packageName, false)
                } else {
                    onAuthenticationSucceeded.invoke(packageName, true)
                }
            }
        }
        return BiometricPrompt(activity, executor, callback)
    }
}
