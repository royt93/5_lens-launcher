package com.mckimquyen.ext

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.*
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.mckimquyen.R
import com.mckimquyen.util.isValid
import com.mckimquyen.views.SuperWebViewActivity

//check xem app hien tai co phai la default launcher hay khong
fun Context.isDefaultLauncher(): Boolean {
    val intent = Intent(Intent.ACTION_MAIN)
    intent.addCategory(Intent.CATEGORY_HOME)
    val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.resolveActivity(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }

    val currentLauncherName = resolveInfo?.activityInfo?.packageName
    if (currentLauncherName == packageName) {
        return true
    }
    return false
}

//mo app setting default cua device
fun Context.launchSystemSetting(
    packageName: String,
) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    intent.data = "package:$packageName".toUri()
    this.startActivity(intent)
}

/*
         * send email support
         */
fun Context?.sendEmail(
) {
    this?.let { context ->
        val emailIntent = Intent(Intent.ACTION_SENDTO)
        val emails = context.resources.getStringArray(R.array.support_emails)
        val emailAddresses = emails.joinToString(",")
        emailIntent.data = "mailto:$emailAddresses".toUri()
        context.startActivity(Intent.createChooser(emailIntent, "Send feedback"))
    }
}

//fun Context.openBrowserPolicy(
//) {
//    this.openUrlInBrowser(url = URL_POLICY_NOTION, isUsingInternalWebView = false, title = "Term and Policy")
//}

fun Context?.openUrlInBrowser(
    url: String?,
    title: String?,
    isUsingInternalWebView: Boolean = true,
) {
    if (this == null || url.isNullOrEmpty()) {
        return
    }
    if (isUsingInternalWebView && isValid()) {
        val intent = Intent(/* packageContext = */ this, /* cls = */ SuperWebViewActivity::class.java)
        intent.putExtra(SuperWebViewActivity.KEY_URL, url)
        intent.putExtra(SuperWebViewActivity.KEY_TITLE, title)
        this.startActivity(intent)
    } else {
        try {
//            val defaultBrowser =
//                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
//            defaultBrowser.data = Uri.parse(url)
//            this.startActivity(defaultBrowser)
//            this.tranIn()
            val i = Intent(Intent.ACTION_VIEW)
            i.data = url.toUri()
            this.startActivity(i)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun Context.showDialog2(
    title: String? = null,
    msg: String? = null,
    button1: String = getString(R.string.confirm),
    button2: String = getString(R.string.cancel),
    onClickButton1: Runnable? = null,
    onClickButton2: Runnable? = null,
    isCancelable: Boolean = true,  // Add parameter to control cancelable behavior
    onDismiss: Runnable? = null,  // Add callback for dismiss event
): AlertDialog {
    val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.LightAlertDialogCustom))

    if (title.isNullOrEmpty()) {
        // do nothing
    } else {
        builder.setTitle(title)
    }
    if (msg.isNullOrEmpty()) {
        // do nothing
    } else {
        builder.setMessage(msg)
    }

    builder.setPositiveButton(button1) { _, _ ->
        onClickButton1?.run()
    }
    builder.setNegativeButton(button2) { _, _ ->
        onClickButton2?.run()
    }
    val dialog = builder.create()
    dialog.setCancelable(isCancelable)

    // Add dismiss listener if callback provided
    if (onDismiss != null) {
        dialog.setOnDismissListener {
            onDismiss.run()
        }
    }

    dialog.show()

    // Set rounded background
    dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)

    val color = ContextCompat.getColor(this, R.color.colorPrimary)
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
    return dialog
}
