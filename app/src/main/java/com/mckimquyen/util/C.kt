package com.mckimquyen.util

import android.os.Build
import java.io.File
import java.util.Calendar

const val URL_POLICY_NOTION =
    "https://loitp.notion.site/loitp/Privacy-Policy-319b1cd8783942fa8923d2a3c9bce60f/"
const val PKG_NAME = "com.mckimquyen.lenslauncher"

fun isValid(): Boolean {
    // 1. Kiểm tra xem device có phải là máy ảo hay không
    if (isEmulator()) {
        return false
    }

    // 2. Kiểm tra xem device có phải là máy đã root hay không
    if (isRooted()) {
        return false
    }

    // 3. Kiểm tra xem datetime của device có là quá khứ so với ngày 1/12/2024 hay không
    val currentDate = Calendar.getInstance()
    val cutoffDate = Calendar.getInstance().apply {
        set(2024, Calendar.DECEMBER, 1) // Ngày 1/12/2024
    }

    return !currentDate.before(cutoffDate)

    // Nếu tất cả các điều kiện đều pass thì return true
}

// Phương thức kiểm tra máy ảo (emulator)
fun isEmulator(): Boolean {
    return (Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.BOARD == "QC_Reference_Phone" ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.HOST.startsWith("Build") ||
            (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
            "google_sdk" == Build.PRODUCT)
}

// Phương thức kiểm tra máy root
fun isRooted(): Boolean {
    val buildTags = Build.TAGS
    return if (buildTags != null && buildTags.contains("test-keys")) {
        true
    } else {
        try {
            val file = File("/system/app/Superuser.apk")
            if (file.exists()) {
                true
            } else {
                val path = arrayOf(
                    "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su",
                    "/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su",
                    "/data/local/su"
                )
                path.any { File(it).exists() }
            }
        } catch (_: Exception) {
            false
        }
    }
}
