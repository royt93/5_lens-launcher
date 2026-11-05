package com.mckimquyen.util

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.mckimquyen.R

enum class LensUnlockResult { SUCCESS, CANCELLED, UNAVAILABLE, ERROR }

interface LensUnlockAuthenticator {
    fun isAvailable(context: Context): Boolean
    fun authenticate(activity: AppCompatActivity, lensName: String, callback: (LensUnlockResult) -> Unit)
}

/** Android calls behind a seam so JVM tests need no hardware. */
interface LensUnlockPlatform {
    fun sdkInt(): Int
    fun canAuthenticate(context: Context, authenticators: Int): Int
    fun isDeviceSecure(context: Context): Boolean
    fun createCredentialIntent(activity: AppCompatActivity): Intent?
    fun authenticateBiometric(
        activity: AppCompatActivity,
        title: String,
        description: String,
        authenticators: Int,
        negativeButtonText: String?,
        callback: BiometricPrompt.AuthenticationCallback
    )
}

/**
 * API 30+: one BiometricPrompt with BIOMETRIC_STRONG | DEVICE_CREDENTIAL, no negative button.
 * API 25-29: AndroidX rejects the combined flags, so strong biometric first, then the system
 * credential screen. The owning Activity must register its result launcher before onStart() and
 * assign [credentialLauncher]/forward results to [onCredentialResult]; only RESULT_OK counts as success.
 */
class SystemLensUnlockAuthenticator(
    private val platform: LensUnlockPlatform = AndroidLensUnlockPlatform()
) : LensUnlockAuthenticator {
    var credentialLauncher: ((Intent) -> Unit)? = null
    private var pendingCredential: ((LensUnlockResult) -> Unit)? = null

    override fun isAvailable(context: Context): Boolean =
        biometricAvailable(context) || platform.isDeviceSecure(context)

    override fun authenticate(
        activity: AppCompatActivity,
        lensName: String,
        callback: (LensUnlockResult) -> Unit
    ) {
        val biometric = biometricAvailable(activity)
        val credential = platform.isDeviceSecure(activity)
        val title = activity.getString(R.string.verify_your_identity)
        val description = activity.getString(R.string.lens_unlock_description, lensName)
        when {
            platform.sdkInt() >= Build.VERSION_CODES.R && (biometric || credential) ->
                platform.authenticateBiometric(
                    activity, title, description,
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    null, promptCallback(callback, null)
                )
            biometric -> platform.authenticateBiometric(
                activity, title, description,
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
                if (credential) activity.getString(R.string.lens_unlock_use_device_credential) else activity.getString(R.string.cancel),
                promptCallback(callback, if (credential) activity else null)
            )
            credential -> launchCredential(activity, callback)
            else -> callback(LensUnlockResult.UNAVAILABLE)
        }
    }

    fun onCredentialResult(success: Boolean) {
        val callback = pendingCredential ?: return
        pendingCredential = null
        callback(if (success) LensUnlockResult.SUCCESS else LensUnlockResult.CANCELLED)
    }

    private fun biometricAvailable(context: Context) =
        platform.canAuthenticate(context, BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun launchCredential(activity: AppCompatActivity, callback: (LensUnlockResult) -> Unit) {
        val launcher = credentialLauncher
        val intent = platform.createCredentialIntent(activity)
        if (launcher == null || intent == null) {
            callback(LensUnlockResult.UNAVAILABLE)
            return
        }
        pendingCredential = callback
        launcher(intent)
    }

    /** [credentialFallback] non-null: the negative button means "use device PIN", not cancel. */
    private fun promptCallback(callback: (LensUnlockResult) -> Unit, credentialFallback: AppCompatActivity?) =
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                callback(LensUnlockResult.SUCCESS)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when {
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON && credentialFallback != null ->
                        launchCredential(credentialFallback, callback)
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED -> callback(LensUnlockResult.CANCELLED)
                    else -> callback(LensUnlockResult.ERROR)
                }
            }
        }
}

private class AndroidLensUnlockPlatform : LensUnlockPlatform {
    override fun sdkInt() = Build.VERSION.SDK_INT

    override fun canAuthenticate(context: Context, authenticators: Int) =
        BiometricManager.from(context).canAuthenticate(authenticators)

    override fun isDeviceSecure(context: Context) =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true

    @Suppress("DEPRECATION") // ponytail: the only credential-confirm intent API on minSdk 25
    override fun createCredentialIntent(activity: AppCompatActivity): Intent? =
        (activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
            ?.createConfirmDeviceCredentialIntent(activity.getString(R.string.verify_your_identity), null)

    override fun authenticateBiometric(
        activity: AppCompatActivity,
        title: String,
        description: String,
        authenticators: Int,
        negativeButtonText: String?,
        callback: BiometricPrompt.AuthenticationCallback
    ) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setDescription(description)
            .setAllowedAuthenticators(authenticators)
            .apply { if (negativeButtonText != null) setNegativeButtonText(negativeButtonText) }
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}
