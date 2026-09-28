package com.mckimquyen.util

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PolaroidExportHelperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `formatCaption uses the lens name and an app-name branding line`() {
        val lines = PolaroidExportHelper.formatCaption(context, "Work")
        assertEquals("Work", lines.line1)
        assertEquals(
            context.getString(R.string.lens_share_caption_via, context.getString(R.string.app_name)),
            lines.line2
        )
    }

    @Test
    fun `formatCaption falls back to the app name for a blank lens name`() {
        val lines = PolaroidExportHelper.formatCaption(context, "   ")
        assertEquals(context.getString(R.string.app_name), lines.line1)
    }

    @Test
    fun `formatCaption truncates a very long lens name with an ellipsis`() {
        val longName = "A".repeat(40)
        val lines = PolaroidExportHelper.formatCaption(context, longName)
        assertEquals(24, lines.line1.length)
        assertTrue(lines.line1.endsWith("…"))
    }

    @Test
    fun `formatCaption keeps Vietnamese diacritics intact`() {
        val lines = PolaroidExportHelper.formatCaption(context, "Công việc")
        assertEquals("Công việc", lines.line1)
    }

    @Test
    fun `sanitizeFileName keeps a simple name unchanged`() {
        assertEquals("Work", PolaroidExportHelper.sanitizeFileName("Work"))
    }

    @Test
    fun `sanitizeFileName replaces unsafe characters with underscores`() {
        assertEquals("Work_Personal", PolaroidExportHelper.sanitizeFileName("Work/Personal"))
    }

    @Test
    fun `sanitizeFileName falls back to a default for a blank name`() {
        assertEquals("lens", PolaroidExportHelper.sanitizeFileName("   "))
    }

    @Test
    fun `sanitizeFileName falls back to a default for an all-emoji name`() {
        assertEquals("lens", PolaroidExportHelper.sanitizeFileName("😀😀"))
    }

    @Test
    fun `calculatePolaroidLayout adds a border and a caption band around the content`() {
        val layout = PolaroidExportHelper.calculatePolaroidLayout(800, 800)
        assertEquals(880, layout.outerWidth)
        assertEquals(1060, layout.outerHeight)
        assertEquals(40, layout.contentLeft)
        assertEquals(40, layout.contentTop)
        assertTrue(layout.captionLine1BaselineY > layout.contentTop + 800)
        assertTrue(layout.captionLine2BaselineY > layout.captionLine1BaselineY)
    }

    @Test
    fun `calculatePolaroidLayout coerces non-positive content size to at least 1px`() {
        val layout = PolaroidExportHelper.calculatePolaroidLayout(0, -5)
        assertEquals(81, layout.outerWidth)
        assertEquals(261, layout.outerHeight)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `buildShareIntent targets an image share with the given uri and read permission`() {
        val uri = "content://com.mckimquyen.lenslauncher.fileprovider/polaroid/Work.png".toUri()
        val intent = PolaroidExportHelper.buildShareIntent(context, uri)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM))
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }
}
