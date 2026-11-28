package com.mckimquyen.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.feature.vip.ActVipManagement
import com.mckimquyen.ui.ActAbout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class SettingsIntentHelperTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `createFeedbackEmailIntent constructs correct mailto intent and extras`() {
        val intent = SettingsIntentHelper.createFeedbackEmailIntent()
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals(Uri.parse("mailto:"), intent.data)
        assertArrayEquals(
            arrayOf(SettingsIntentHelper.FEEDBACK_EMAIL_1, SettingsIntentHelper.FEEDBACK_EMAIL_2),
            intent.getStringArrayExtra(Intent.EXTRA_EMAIL)
        )
        assertEquals(SettingsIntentHelper.FEEDBACK_SUBJECT, intent.getStringExtra(Intent.EXTRA_SUBJECT))
        val body = intent.getStringExtra(Intent.EXTRA_TEXT)
        assertNotNull(body)
        assertTrue(body!!.contains("Fisheye Launcher app"))
        assertTrue(!body.contains("roy.mobile.dev@gmail.com"))
    }

    @Test
    fun `createHomeLauncherIntent targets main home category with new task flag`() {
        val intent = SettingsIntentHelper.createHomeLauncherIntent()
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_HOME))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun `createWallpaperPickerChooserIntent creates valid chooser for set wallpaper`() {
        val chooserIntent = SettingsIntentHelper.createWallpaperPickerChooserIntent()
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)
        val target = chooserIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(target)
        assertEquals(Intent.ACTION_SET_WALLPAPER, target!!.action)
    }

    @Test
    fun `createAboutIntent targets ActAbout component`() {
        val intent = SettingsIntentHelper.createAboutIntent(context)
        assertEquals(ActAbout::class.java.name, intent.component?.className)
    }

    @Test
    fun `createVipIntent targets ActVipManagement component`() {
        val intent = SettingsIntentHelper.createVipIntent(context)
        assertEquals(ActVipManagement::class.java.name, intent.component?.className)
    }

    /** Audit R8: feedback did nothing, silently, when no mail app existed. A real Activity that resolves, refuses or does not resolve stands in for the phone. */
    class MailHost : android.app.Activity() {
        override fun startActivity(intent: Intent?) {
            started++
            if (refuse) throw SecurityException("refused")
        }

        companion object {
            var refuse = false
            var started = 0
        }
    }

    private fun mailHost(refuse: Boolean = false, hasMailApp: Boolean = true): android.app.Activity {
        MailHost.refuse = refuse
        MailHost.started = 0
        val activity = org.robolectric.Robolectric.buildActivity(MailHost::class.java).setup().get()
        if (hasMailApp) {
            org.robolectric.Shadows.shadowOf(activity.packageManager).addResolveInfoForIntent(
                SettingsIntentHelper.createFeedbackEmailIntent(),
                android.content.pm.ResolveInfo().apply {
                    activityInfo = android.content.pm.ActivityInfo().apply { packageName = "com.example.mail"; name = "com.example.mail.Compose" }
                }
            )
        }
        return activity
    }

    @Test
    fun `startIfResolvable opens the mail app when one exists`() {
        val activity = mailHost()

        assertTrue(SettingsIntentHelper.startIfResolvable(activity, SettingsIntentHelper.createFeedbackEmailIntent()))
        assertEquals("positive control: the intent really reached startActivity", 1, MailHost.started)
    }

    @Test
    fun `startIfResolvable reports false and starts nothing when no mail app exists`() {
        val activity = mailHost(hasMailApp = false)

        assertEquals(false, SettingsIntentHelper.startIfResolvable(activity, SettingsIntentHelper.createFeedbackEmailIntent()))
        assertEquals(0, MailHost.started)
    }

    @Test
    fun `startIfResolvable reports false when the mail app refuses the start`() {
        val activity = mailHost(refuse = true)

        assertEquals(false, SettingsIntentHelper.startIfResolvable(activity, SettingsIntentHelper.createFeedbackEmailIntent()))
        assertEquals("positive control: the start was attempted", 1, MailHost.started)
    }
}
