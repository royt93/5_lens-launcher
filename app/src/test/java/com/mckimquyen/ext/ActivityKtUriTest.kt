package com.mckimquyen.ext

import android.app.Activity
import android.content.Intent
import androidx.core.net.toUri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit tests cho các extension function trong `Activity.kt`/`Context.kt` build `Intent`
 * bằng URI — thêm khi dọn lint `UseKtx` (`Uri.parse(x)` -> `x.toUri()`,
 * `SharedPreferences.edit()...apply()` -> `edit { ... }`). Các hàm này trước đây
 * chưa có test nào; khóa lại đúng `action`/`data` của `Intent` được tạo ra để chứng
 * minh việc đổi API không làm lệch URI thật sự được dùng để mở app khác.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ActivityKtUriTest {

    private fun activity() = Robolectric.buildActivity(Activity::class.java).create().get()

    @Test
    fun `rateApp opens the market details page for the given package`() {
        val activity = activity()
        activity.rateApp(packageName = "com.example.app")

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("market://details?id=com.example.app".toUri(), started.data)
    }

    @Test
    fun `rateApp does nothing for a blank package name`() {
        val activity = activity()
        activity.rateApp(packageName = "")

        assertEquals(null, shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `moreApp opens the developer's Play Store page`() {
        val activity = activity()
        activity.moreApp(nameOfDeveloper = "SAIGON PHANTOM LABS")

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(
            "https://play.google.com/store/apps/developer?id=SAIGON PHANTOM LABS".toUri(),
            started.data
        )
    }

    @Test
    fun `uninstallApp targets the exact package via an ACTION_DELETE intent`() {
        val activity = activity()
        activity.uninstallApp(packageName = "com.example.target")

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_DELETE, started.action)
        assertEquals("package:com.example.target".toUri(), started.data)
    }

    @Test
    fun `playYoutube opens the exact video URL`() {
        val activity = activity()
        activity.playYoutube(url = "http://www.youtube.com/watch?v=Hxy8BZGQ5Jo")

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("http://www.youtube.com/watch?v=Hxy8BZGQ5Jo".toUri(), started.data)
    }

    @Test
    fun `playYoutube does nothing for a null or blank url`() {
        val activity = activity()
        activity.playYoutube(url = null)

        assertEquals(null, shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `playYoutubeWithId builds the standard watch URL`() {
        val activity = activity()
        activity.playYoutubeWithId(id = "abc123")

        val started = shadowOf(activity).nextStartedActivity
        assertEquals("http://www.youtube.com/watch?v=abc123".toUri(), started.data)
    }

    @Test
    fun `likeFacebookFanpage opens the resolved Facebook URL`() {
        val activity = activity()
        activity.likeFacebookFanpage()

        // The Facebook app is never installed under Robolectric, so getFacebookPageURL()
        // deterministically falls back to the plain web URL branch.
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://www.facebook.com/hoidammedocsach".toUri(), started.data)
    }

    @Test
    fun `searchIconPack opens the Play Store icon-pack search`() {
        val activity = activity()
        activity.searchIconPack()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("market://search?q=icon%20pack&c=apps".toUri(), started.data)
    }

    @Test
    fun `launchSystemSetting opens the app details settings for the given package`() {
        // Uses an Activity Context (rather than the bare Application Context) because
        // starting an Activity from a non-Activity Context requires FLAG_ACTIVITY_NEW_TASK,
        // which this extension function does not set.
        val activity = activity()
        activity.launchSystemSetting(packageName = "com.example.target")

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertEquals("package:com.example.target".toUri(), started.data)
    }
}
