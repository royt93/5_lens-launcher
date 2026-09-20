package com.mckimquyen.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.mckimquyen.feature.vip.ActVipManagement
import com.mckimquyen.ui.ActAbout

/**
 * ARCH-001: Intent creation and resolution helper for Settings.
 */
object SettingsIntentHelper {

    const val FEEDBACK_EMAIL_1 = "roy.mobile.dev@gmail.com"
    const val FEEDBACK_EMAIL_2 = "20testersforclosedtesting@googlegroups.com"
    const val FEEDBACK_SUBJECT = "Feedback on Fisheye Launcher App"

    val FEEDBACK_BODY: String = """
        Hello,

        I hope this message finds you well. Below are my feedback and suggestions regarding the Fisheye Launcher app:

        [Insert your feedback here]

        Thank you for your attention and support.

        Best regards,
        [Your Name]
    """.trimIndent()

    @JvmStatic
    fun createFeedbackEmailIntent(): Intent {
        return Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL_1, FEEDBACK_EMAIL_2))
            putExtra(Intent.EXTRA_SUBJECT, FEEDBACK_SUBJECT)
            putExtra(Intent.EXTRA_TEXT, FEEDBACK_BODY)
        }
    }

    @JvmStatic
    fun createHomeLauncherIntent(): Intent {
        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    @JvmStatic
    fun createWallpaperPickerChooserIntent(): Intent {
        val wallpaperIntent = Intent(Intent.ACTION_SET_WALLPAPER)
        return Intent.createChooser(wallpaperIntent, "Select Wallpaper")
    }

    @JvmStatic
    fun createAboutIntent(context: Context): Intent {
        return Intent(context, ActAbout::class.java)
    }

    @JvmStatic
    fun createVipIntent(context: Context): Intent {
        return Intent(context, ActVipManagement::class.java)
    }
}
