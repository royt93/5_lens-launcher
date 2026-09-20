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

@RunWith(RobolectricTestRunner::class)
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
}
