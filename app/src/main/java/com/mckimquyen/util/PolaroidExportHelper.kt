package com.mckimquyen.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mckimquyen.R

/**
 * Polaroid lens export (brainstormed 2026-09-27, spec:
 * docs/superpowers/specs/2026-09-28-polaroid-lens-export-design.md). Snapshots a lens's
 * currently-rendered LensView into a polaroid-framed PNG for sharing.
 *
 * Pure geometry/text/intent-building functions live here and are unit-tested directly
 * (PolaroidExportHelperTest). The Android-side-effecting `exportAsync` (bitmap capture, frame
 * compositing, file write, FileProvider) is added in a later task and covered by androidTest,
 * since it needs a real Bitmap/Canvas/File - same split this codebase already uses for
 * ApertureRevealHelper (pure) vs LayoutBackupIo (side-effecting).
 */
object PolaroidExportHelper {

    private const val BORDER_PX = 40
    private const val CAPTION_HEIGHT_PX = 180
    private const val CAPTION_LINE1_TEXT_SIZE_PX = 56f
    private const val CAPTION_LINE2_TEXT_SIZE_PX = 34f
    private const val CAPTION_LINE_GAP_PX = 16
    private const val MAX_LENS_NAME_LENGTH = 24
    private val UNSAFE_FILENAME_CHARS = Regex("[^A-Za-z0-9_-]+")

    data class CaptionLines(val line1: String, val line2: String)

    data class PolaroidLayout(
        val outerWidth: Int,
        val outerHeight: Int,
        val contentLeft: Int,
        val contentTop: Int,
        val captionLine1BaselineY: Int,
        val captionLine2BaselineY: Int
    )

    /** line1 = the lens name (truncated if very long, falls back to the app name if blank);
     *  line2 = a fixed "via <app name>" branding line. */
    @JvmStatic
    fun formatCaption(context: Context, lensName: String): CaptionLines {
        val trimmed = lensName.trim()
        val base = trimmed.ifEmpty { context.getString(R.string.app_name) }
        val line1 = if (base.length > MAX_LENS_NAME_LENGTH) {
            base.take(MAX_LENS_NAME_LENGTH - 1) + "…"
        } else {
            base
        }
        val line2 = context.getString(R.string.lens_share_caption_via, context.getString(R.string.app_name))
        return CaptionLines(line1, line2)
    }

    /** Strips characters unsafe for a filesystem path component; never returns an empty string. */
    @JvmStatic
    fun sanitizeFileName(lensName: String): String {
        val cleaned = UNSAFE_FILENAME_CHARS.replace(lensName.trim(), "_").trim('_')
        return cleaned.ifEmpty { "lens" }
    }

    /** Pure geometry: a white polaroid frame around [contentWidthPx]x[contentHeightPx], with a
     *  caption band below it. No Android Context/view dependency. */
    @JvmStatic
    fun calculatePolaroidLayout(contentWidthPx: Int, contentHeightPx: Int): PolaroidLayout {
        val w = contentWidthPx.coerceAtLeast(1)
        val h = contentHeightPx.coerceAtLeast(1)
        val outerWidth = w + BORDER_PX * 2
        val outerHeight = h + BORDER_PX * 2 + CAPTION_HEIGHT_PX
        val captionTop = BORDER_PX + h + BORDER_PX
        val line1Baseline = captionTop + CAPTION_LINE1_TEXT_SIZE_PX.toInt()
        val line2Baseline = line1Baseline + CAPTION_LINE2_TEXT_SIZE_PX.toInt() + CAPTION_LINE_GAP_PX
        return PolaroidLayout(
            outerWidth = outerWidth,
            outerHeight = outerHeight,
            contentLeft = BORDER_PX,
            contentTop = BORDER_PX,
            captionLine1BaselineY = line1Baseline,
            captionLine2BaselineY = line2Baseline
        )
    }

    /** ACTION_SEND for [imageUri], matching ext/Activity.kt's shareApp() pattern (the caller
     *  wraps this in Intent.createChooser(...) with R.string.share_via). */
    @JvmStatic
    fun buildShareIntent(context: Context, imageUri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
