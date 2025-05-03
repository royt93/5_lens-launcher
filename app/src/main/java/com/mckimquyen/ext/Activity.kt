package com.mckimquyen.ext

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Telephony
import android.util.Log
import android.view.*
import android.widget.Toast
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.model.ReviewErrorCode
import com.mckimquyen.R
import java.util.Calendar

//mo hop thoai de select launcher default
fun Context.chooseLauncher(cls: Class<*>) {
    val componentName = ComponentName(this, cls)
    this.packageManager.setComponentEnabledSetting(
        componentName,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP
    )
    val selector = Intent(Intent.ACTION_MAIN)
    selector.addCategory(Intent.CATEGORY_HOME)
    selector.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    this.startActivity(selector)
    this.packageManager.setComponentEnabledSetting(
        componentName,
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
        PackageManager.DONT_KILL_APP
    )
}

//mo play store va search cac app ve icon
fun Activity.searchIconPack() {
    val url = "market://search?q=icon%20pack&c=apps"
    try {
        this.startActivity(
            Intent(
                Intent.ACTION_VIEW, Uri.parse(url)
            )
        )
    } catch (ex: Exception) {
        ex.printStackTrace()
        this.moreApp()
    }
}

//mo app dong ho mac dinh cua device
fun Activity.launchClockApp() {
    try {
        val i = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        this.startActivity(i)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

//mo app calendar mac dinh cua device
fun Activity.launchCalendar() {
    val calendarUri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
    this.startActivity(Intent(Intent.ACTION_VIEW, calendarUri))
}

//go mot app bat ky nao do
fun Activity.uninstallApp(
    packageName: String,
) {
    val intent = Intent(Intent.ACTION_DELETE)
    intent.data = Uri.parse("package:$packageName")
    this.startActivity(intent)
}

fun Activity.toggleFullScreen() {
    val attrs = this.window.attributes
    attrs.flags = attrs.flags xor WindowManager.LayoutParams.FLAG_FULLSCREEN
    this.window.attributes = attrs
}

@SuppressLint("SourceLockedOrientationActivity")
fun Activity.toggleScreenOrientation() {
    val s = getScreenOrientation()
    if (s == Configuration.ORIENTATION_LANDSCAPE) {
        this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    } else if (s == Configuration.ORIENTATION_PORTRAIT) {
        this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}

@SuppressLint("SourceLockedOrientationActivity")
fun Activity.changeScreenPortrait() {
    this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}

fun Activity.changeScreenLandscape() {
    this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
}

fun Activity.getScreenOrientation(): Int {
    return this.resources.configuration.orientation
}

@Suppress("unused")
fun Activity.setSoftInputMode(
    mode: Int,
) {
    this.window.setSoftInputMode(mode)
}

// https://gist.github.com/mustafasevgi/8c6b638ffd5fca90d45d
fun Activity?.sendSMS(
    text: String,
) {
    if (this == null) {
        return
    }
    val defaultSmsPackageName =
        Telephony.Sms.getDefaultSmsPackage(this) // Need to change the build to API 19

    val sendIntent = Intent(Intent.ACTION_SEND)
    sendIntent.type = "text/plain"
    sendIntent.putExtra(Intent.EXTRA_TEXT, text)

    if (defaultSmsPackageName != null)
    // Can be null in case that there is no default, then the user would be able to choose
    // any app that support this intent.
    {
        sendIntent.setPackage(defaultSmsPackageName)
    }
    this.startActivity(sendIntent)
}

fun Activity.rateAppInApp(forceRateInApp: Boolean = false) {
    //import gradle app
//    implementation("com.google.android.play:review:2.0.2")
//    implementation("com.google.android.play:review-ktx:2.0.2")

    val sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    val lastReviewTime = sharedPreferences.getLong("last_review_time", 0L)
//    Log.d("roy93~", "requestReview lastReviewTime $lastReviewTime")
    val currentTime = Calendar.getInstance().timeInMillis
    val daysSinceLastReview = (currentTime - lastReviewTime) / (1000 * 60 * 60 * 24)
//    Log.d("roy93~", "requestReview forceRateInApp $forceRateInApp")
//    Log.d("roy93~", "requestReview daysSinceLastReview $daysSinceLastReview")
    if (daysSinceLastReview >= 7 || forceRateInApp) {
//    if (daysSinceLastReview >= 7) {
        val reviewManager = ReviewManagerFactory.create(this)
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo: ReviewInfo = task.result
                reviewManager.launchReviewFlow(this, reviewInfo)
                sharedPreferences.edit().putLong("last_review_time", currentTime).apply()
//                Log.d("roy93~", "requestReview result ${task.result}")
//                Log.d("roy93~", "requestReview isSuccessful ${task.isSuccessful}")
//                Log.d("roy93~", "requestReview isCanceled ${task.isCanceled}")
//                Log.d("roy93~", "requestReview isComplete ${task.isComplete}")
//                Log.d("roy93~", "requestReview exception ${task.exception}")
            } else {
                @ReviewErrorCode val reviewErrorCode = (task.exception as ReviewException).errorCode
//                Log.e("roy93~", "requestReview error $reviewErrorCode")
            }
        }
    }
}

fun Activity.rateApp(
    packageName: String? = null,
) {
    if (packageName.isNullOrEmpty()) {
        return
    }
    try {
        this.startActivity(
            Intent(
                Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")
            )
        )
    } catch (e: android.content.ActivityNotFoundException) {
        e.printStackTrace()
        this.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("http://play.google.com/store/apps/details?id=$packageName")
            )
        )
    }
}

fun Activity.moreApp(
    nameOfDeveloper: String = "SAIGON PHANTOM LABS",
) {
    try {
        val uri = "https://play.google.com/store/apps/developer?id=$nameOfDeveloper"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        this.startActivity(intent)
    } catch (_: Exception) {
        //do nothing
    }
}

fun Activity.shareApp(
) {
    try {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_SUBJECT, this.getString(R.string.app_name))
        var sAux = "\nỨng dụng này rất bổ ích, thân mời bạn tải về cài đặt để trải nghiệm\n\n"
        sAux = sAux + "https://play.google.com/store/apps/details?id=" + this.packageName
        intent.putExtra(Intent.EXTRA_TEXT, sAux)
        this.startActivity(Intent.createChooser(intent, "Vui lòng chọn"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun Activity.share(
    msg: String,
) {
    try {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_SUBJECT, this.getString(R.string.app_name))
        // String sAux = "\nỨng dụng này rất bổ ích, thân mời bạn tải về cài đặt để trải nghiệm\n\n";
        // sAux = sAux + "https://play.google.com/store/apps/details?id=" + activity.getPackageName();
        intent.putExtra(Intent.EXTRA_TEXT, msg)
        this.startActivity(Intent.createChooser(intent, "Share via"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// like fanpage
fun Activity?.likeFacebookFanpage(
) {
    this?.apply {
        try {
            val facebookIntent = Intent(Intent.ACTION_VIEW)
            val facebookUrl = getFacebookPageURL()
            facebookIntent.data = Uri.parse(facebookUrl)
            startActivity(facebookIntent)
        } catch (e: Exception) {
            Toast.makeText(
                /* context = */ this,
                /* text = */ "Error $e.\nPlease try again later",
                /* duration = */ Toast.LENGTH_SHORT
            ).show()
        }
    }
}

fun Context.getFacebookPageURL(): String {
    val facebookUrl = "https://www.facebook.com/hoidammedocsach"
    val facebookPageId = "hoidammedocsach"
    val packageManager = this.packageManager
    return try {
        val versionCode = packageManager.getPackageInfo("com.facebook.katana", 0).versionCode
        if (versionCode >= 3002850) {
            "fb://facewebmodal/f?href=$facebookUrl"
        } else {
            "fb://page/$facebookPageId"
        }
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
        facebookUrl
    }
}

// playYoutube(activity, "http://www.youtube.com/watch?v=Hxy8BZGQ5Jo");
fun Activity.playYoutube(
    url: String?,
) {
    if (url.isNullOrEmpty()) {
        return
    }
    this.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

fun Activity.playYoutubeWithId(
    id: String,
) {
    this.playYoutube(url = "http://www.youtube.com/watch?v=$id")
}

fun Activity.setChangeStatusBarTintToDark(
    shouldChangeStatusBarTintToDark: Boolean,
) {
    val decor = this.window.decorView
    if (shouldChangeStatusBarTintToDark) {
        decor.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    } else {
        // We want to change tint color to white again.
        // You can also record the flags in advance so that you can turn UI back completely if
        // you have set other flags before, such as translucent or full screen.
        decor.systemUiVisibility = 0
    }
}

val screenWidth: Int
    get() = Resources.getSystem().displayMetrics.widthPixels

val screenHeight: Int
    get() = Resources.getSystem().displayMetrics.heightPixels

fun Context.getScreenHeightIncludeNavigationBar(): Int {
    val windowManager = this.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val display = windowManager.defaultDisplay
    val outPoint = Point()
    // include navigation bar
    display.getRealSize(outPoint)
    val mRealSizeHeight: Int = if (outPoint.y > outPoint.x) {
        outPoint.y
        // mRealSizeWidth = outPoint.x;
    } else {
        outPoint.x
        // mRealSizeWidth = outPoint.y;
    }
    return mRealSizeHeight
}

@SuppressLint("ObsoleteSdkInt")
fun Activity.showStatusBar(
) {
    if (Build.VERSION.SDK_INT < 16) {
        this.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    } else {
        val decorView = this.window.decorView
        // Show Status Bar.
        val uiOptions = View.SYSTEM_UI_FLAG_VISIBLE
        decorView.systemUiVisibility = uiOptions
    }
}

@SuppressLint("ObsoleteSdkInt")
fun Activity.hideStatusBar(
) {
    // Hide Status Bar
    if (Build.VERSION.SDK_INT < 16) {
        this.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    } else {
        val decorView = this.window.decorView
        // Hide Status Bar.
        val uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
        decorView.systemUiVisibility = uiOptions
    }
}

fun Activity.toggleFullscreen(
) {
    val attrs = this.window.attributes
    attrs.flags = attrs.flags xor WindowManager.LayoutParams.FLAG_FULLSCREEN
    // attrs.flags ^= WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
    // attrs.flags ^= WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
    this.window.attributes = attrs
    /*if (isFullScreen(activity)) {
        hideNavigationBar(activity)
    } else {
        showNavigationBar(activity)
    }*/
}

fun Activity.toggleFullscreen(
    isFullScreen: Boolean,
) {
    if (isFullScreen) {
        this.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    } else {
        this.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
}

fun Activity.hideNavigationBar(
) {
    // set navigation bar status, remember to disable "setNavigationBarTintEnabled"
    val flags =
        (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    // This work only for android 4.4+
    this.window.decorView.systemUiVisibility = flags

    // Code below is to handle presses of Volume up or Volume down.
    // Without this, after pressing volume buttons, the navigation bar will
    // show up and won't hide
    val decorView = this.window.decorView
    decorView.setOnSystemUiVisibilityChangeListener { visibility ->
        if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
            decorView.systemUiVisibility = flags
        }
    }
}

fun Activity.showNavigationBar(
) {
    // set navigation bar status, remember to disable "setNavigationBarTintEnabled"
    val flags =
        (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    // This work only for android 4.4+
    this.window.decorView.systemUiVisibility = flags

    // Code below is to handle presses of Volume up or Volume down.
    // Without this, after pressing volume buttons, the navigation bar will
    // show up and won't hide
    val decorView = this.window.decorView
    decorView.setOnSystemUiVisibilityChangeListener { visibility ->
        if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
            decorView.systemUiVisibility = flags
        }
    }
}

@SuppressLint("ObsoleteSdkInt")
fun Activity.hideDefaultControls(
) {
    val window = this.window ?: return
    window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    val decorView = window.decorView
    var uiOptions = decorView.systemUiVisibility
    if (Build.VERSION.SDK_INT >= 14) {
        uiOptions = uiOptions or View.SYSTEM_UI_FLAG_LOW_PROFILE
    }
    if (Build.VERSION.SDK_INT >= 16) {
        uiOptions = uiOptions or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }
    if (Build.VERSION.SDK_INT >= 19) {
        uiOptions = uiOptions or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
    decorView.systemUiVisibility = uiOptions
}

@SuppressLint("ObsoleteSdkInt")
fun Activity.showDefaultControls(
) {
    val window = this.window ?: return
    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
    val decorView = window.decorView
    var uiOptions = decorView.systemUiVisibility
    if (Build.VERSION.SDK_INT >= 14) {
        uiOptions = uiOptions and View.SYSTEM_UI_FLAG_LOW_PROFILE.inv()
    }
    if (Build.VERSION.SDK_INT >= 16) {
        uiOptions = uiOptions and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv()
    }
    if (Build.VERSION.SDK_INT >= 19) {
        uiOptions = uiOptions and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY.inv()
    }
    decorView.systemUiVisibility = uiOptions
}
