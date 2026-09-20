package com.mckimquyen.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.roy.sdkadbmob.AdManager
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Utility to configure test device IDs for AdMob & AppLovin.
 * Guarantees that release builds running on developer test devices (e.g. TECNO BG6)
 * will NEVER display real ads, strictly receiving test ads to prevent invalid traffic penalties.
 */
object TestDeviceHelper {

    /**
     * Hashed device ID of developer test devices:
     * - adaa42e7-9cc6-4a8a-9c90-d4d87842b12c: TECNO BG6 GAID (118743744X002560)
     * - 6B822442D4E755EB60D4E3943510317A: TECNO BG6 MD5 of android_id
     * - 884670AFCACDD337E31BB6153C6DB17E: Developer test device 1
     * - 05B522309BC31052952BBCD5CC85ACA8: Developer test device 2
     */
    val KNOWN_TEST_DEVICE_IDS = listOf(
        "adaa42e7-9cc6-4a8a-9c90-d4d87842b12c", // TECNO BG6 GAID
        "6B822442D4E755EB60D4E3943510317A",     // TECNO BG6 MD5 android_id
        "884670AFCACDD337E31BB6153C6DB17E",
        "05B522309BC31052952BBCD5CC85ACA8",
        AdRequest.DEVICE_ID_EMULATOR
    )

    /**
     * Compute AdMob's standard uppercase MD5 hashed Android ID for the current device.
     */
    @SuppressLint("HardwareIds")
    @JvmStatic
    fun getHashedAndroidId(context: Context): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: return ""
            hashMd5Upper(androidId)
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Pure MD5 hex string in uppercase (matching AdMob logcat format).
     */
    @JvmStatic
    fun hashMd5Upper(input: String): String {
        if (input.isEmpty()) return ""
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
            val sb = StringBuilder()
            for (b in digest) {
                sb.append(String.format("%02X", b))
            }
            sb.toString()
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Get all test device IDs combining known devices and current hardware ID.
     */
    @JvmStatic
    fun getAllTestDeviceIds(context: Context): List<String> {
        val result = LinkedHashSet(KNOWN_TEST_DEVICE_IDS)
        val currentHashed = getHashedAndroidId(context)
        if (currentHashed.isNotEmpty()) {
            result.add(currentHashed)
        }
        return result.toList()
    }

    /**
     * Check if the current device is a registered developer test device.
     */
    @JvmStatic
    fun isTestDevice(context: Context): Boolean {
        val currentHashed = getHashedAndroidId(context)
        return currentHashed.isNotEmpty() && KNOWN_TEST_DEVICE_IDS.contains(currentHashed)
    }

    /**
     * Apply test device configuration across MobileAds and AdManager wrapper.
     * Safe to invoke on all build types (debug and release).
     */
    @JvmStatic
    fun configureTestDevices(context: Context) {
        val testDeviceIds = getAllTestDeviceIds(context)

        // 1. Configure Google Mobile Ads SDK (AdMob)
        try {
            val requestConfig = RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
            MobileAds.setRequestConfiguration(requestConfig)
        } catch (t: Throwable) {
            Logger.e("Failed to set MobileAds test devices", t)
        }

        // 2. Configure AdManager wrapper SDK
        try {
            AdManager.setTestDeviceIds(*testDeviceIds.toTypedArray())
        } catch (t: Throwable) {
            Logger.e("Failed to set AdManager test devices", t)
        }
    }
}
