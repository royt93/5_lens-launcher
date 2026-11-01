package com.mckimquyen.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.mckimquyen.R
import com.mckimquyen.app.ApplicationScope
import com.mckimquyen.model.App
import com.mckimquyen.views.LensView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Polaroid lens export (brainstormed 2026-09-27, spec:
 * docs/superpowers/specs/2026-09-28-polaroid-lens-export-design.md). Snapshots a lens's
 * currently-rendered LensView into a polaroid-framed PNG for sharing.
 *
 * Pure geometry/text/intent-building functions live here and are unit-tested directly with plain
 * JUnit (PolaroidExportHelperTest) - none of them touch a Context or a string resource, on
 * purpose: R.string.app_name/R.string.lens_share_caption_via are resolved only inside
 * [exportAsync] itself, which always runs on a real Android Context (androidTest), never from a
 * JVM unit test. The Android-side-effecting `exportAsync` (bitmap capture, frame compositing,
 * file write, FileProvider) is covered by androidTest instead, since it needs a real
 * Bitmap/Canvas/File - same split this codebase already uses for ApertureRevealHelper (pure) vs
 * LayoutBackupIo (side-effecting).
 */
object PolaroidExportHelper {

    private const val BORDER_PX = 40
    private const val CAPTION_HEIGHT_PX = 180
    private const val CAPTION_LINE1_TEXT_SIZE_PX = 56f
    private const val CAPTION_LINE2_TEXT_SIZE_PX = 34f
    private const val CAPTION_LINE3_TEXT_SIZE_PX = 28f
    private const val CAPTION_LINE3_EXTRA_PX = 52
    private const val CAPTION_LINE_GAP_PX = 16
    private const val CAPTION_LINES_WITH_OPTICAL = 3
    private const val SHEET_FILE_NAME = "lenses"
    private const val SHEET_FOOTER_TEXT_SIZE_PX = 34f
    private const val SHEET_FOOTER_BASELINE_FROM_BOTTOM_PX = 36
    private const val MAX_LENS_NAME_LENGTH = 24
    private val UNSAFE_FILENAME_CHARS = Regex("[^A-Za-z0-9_-]+")

    data class CaptionLines(val line1: String, val line2: String, val line3: String = "")

    /** One lens of a contact sheet: [optical] is the pre-formatted [PolaroidCaptionFormatter.opticalLine]. */
    data class LensExportSpec(val lensId: String, val lensName: String, val optical: String)

    data class PolaroidLayout(
        val outerWidth: Int,
        val outerHeight: Int,
        val contentLeft: Int,
        val contentTop: Int,
        val captionLine1BaselineY: Int,
        val captionLine2BaselineY: Int,
        /** 0 when the caption has two lines. */
        val captionLine3BaselineY: Int = 0
    )

    /** Pure: the lens name, truncated if very long, falling back to [fallbackName] (the caller's
     *  already-resolved app name) if blank. No Context/resource dependency - the caller (exportAsync)
     *  resolves R.string.app_name/R.string.lens_share_caption_via itself, since those are only ever
     *  needed on a real Android Context, never from a unit test. */
    @JvmStatic
    fun buildCaptionLine1(lensName: String, fallbackName: String): String {
        val base = lensName.trim().ifEmpty { fallbackName }
        return if (base.length > MAX_LENS_NAME_LENGTH) base.take(MAX_LENS_NAME_LENGTH - 1) + "…" else base
    }

    /** Pure: with an [optical] line the caption is name / optical / via; without one it stays the old
     *  name / via pair (line 3 empty), so callers that have no optical data render as before. */
    @JvmStatic
    fun buildCaptionLines(lensName: String, fallbackName: String, optical: String, via: String): CaptionLines {
        val line1 = buildCaptionLine1(lensName, fallbackName)
        val opticalText = optical.trim()
        return if (opticalText.isEmpty()) CaptionLines(line1, via) else CaptionLines(line1, opticalText, via)
    }

    /** Strips characters unsafe for a filesystem path component; never returns an empty string.
     *  ponytail: Different names with identical sanitized text share one cache file; add a stable
     *  lensId suffix if a real concurrent/collision report makes per-lens uniqueness necessary. */
    @JvmStatic
    fun sanitizeFileName(lensName: String): String {
        val cleaned = UNSAFE_FILENAME_CHARS.replace(lensName.trim(), "_").trim('_')
        return cleaned.ifEmpty { "lens" }
    }

    /** Pure geometry: a white polaroid frame around [contentWidthPx]x[contentHeightPx], with a
     *  caption band below it ([captionLines] >= 3 adds room for a third, smaller line). No Android
     *  Context/view dependency. */
    @JvmStatic
    @JvmOverloads
    fun calculatePolaroidLayout(contentWidthPx: Int, contentHeightPx: Int, captionLines: Int = 2): PolaroidLayout {
        val w = contentWidthPx.coerceAtLeast(1)
        val h = contentHeightPx.coerceAtLeast(1)
        val outerWidth = w + BORDER_PX * 2
        val hasLine3 = captionLines >= CAPTION_LINES_WITH_OPTICAL
        val outerHeight = h + BORDER_PX * 2 + CAPTION_HEIGHT_PX + if (hasLine3) CAPTION_LINE3_EXTRA_PX else 0
        val captionTop = BORDER_PX + h + BORDER_PX
        val line1Baseline = captionTop + CAPTION_LINE1_TEXT_SIZE_PX.toInt()
        val line2Baseline = line1Baseline + CAPTION_LINE2_TEXT_SIZE_PX.toInt() + CAPTION_LINE_GAP_PX
        return PolaroidLayout(
            outerWidth = outerWidth,
            outerHeight = outerHeight,
            contentLeft = BORDER_PX,
            contentTop = BORDER_PX,
            captionLine1BaselineY = line1Baseline,
            captionLine2BaselineY = line2Baseline,
            captionLine3BaselineY = if (hasLine3) line2Baseline + CAPTION_LINE3_TEXT_SIZE_PX.toInt() + CAPTION_LINE_GAP_PX else 0
        )
    }

    /** ACTION_SEND for [imageUri], matching ext/Activity.kt's shareApp() pattern (the caller
     *  wraps this in Intent.createChooser(...) with R.string.share_via). */
    @JvmStatic
    fun buildShareIntent(imageUri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun renderFrame(content: Bitmap, layout: PolaroidLayout, caption: CaptionLines): Bitmap {
        val framed = createBitmap(layout.outerWidth, layout.outerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(framed)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(content, layout.contentLeft.toFloat(), layout.contentTop.toFloat(), null)

        val line1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = CAPTION_LINE1_TEXT_SIZE_PX
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }
        val line2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = CAPTION_LINE2_TEXT_SIZE_PX
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }
        val line3Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textSize = CAPTION_LINE3_TEXT_SIZE_PX
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }
        val centerX = layout.outerWidth / 2f
        canvas.drawText(caption.line1, centerX, layout.captionLine1BaselineY.toFloat(), line1Paint)
        canvas.drawText(caption.line2, centerX, layout.captionLine2BaselineY.toFloat(), line2Paint)
        if (caption.line3.isNotEmpty()) {
            canvas.drawText(caption.line3, centerX, layout.captionLine3BaselineY.toFloat(), line3Paint)
        }
        return framed
    }

    private fun writeToCache(context: Context, bitmap: Bitmap, fileName: String): File {
        val dir = File(context.cacheDir, "polaroid").apply { mkdirs() }
        val file = File(dir, "$fileName.png")
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return file
    }

    /**
     * Must be called from the main thread - [lensView].draw() requires it. Captures the bitmap
     * synchronously on the calling thread, then moves to [Dispatchers.IO] only for
     * compositing/compress/file-write, then delivers [onDone] on [Dispatchers.Main]. Delivers
     * `null` on any failure: an unlaid-out view (width/height <= 0), an OutOfMemoryError, or an
     * I/O/FileProvider failure.
     */
    @JvmStatic
    fun exportAsync(lensView: LensView, lensName: String, context: Context, onDone: (Uri?) -> Unit) =
        exportAsync(lensView, lensName, "", context, onDone)

    /** [optical] is the pre-formatted optical line ([PolaroidCaptionFormatter.opticalLine]); blank keeps the old caption. */
    @JvmStatic
    fun exportAsync(lensView: LensView, lensName: String, optical: String, context: Context, onDone: (Uri?) -> Unit) {
        val width = lensView.width
        val height = lensView.height
        if (width <= 0 || height <= 0) {
            onDone(null)
            return
        }
        lensView.resetToIdleForExport()
        val contentBitmap = try {
            createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                lensView.draw(Canvas(bmp))
            }
        } catch (error: OutOfMemoryError) {
            Logger.e("PolaroidExportHelper: bitmap alloc failed", error)
            onDone(null)
            return
        }

        val appContext = context.applicationContext
        val appName = appContext.getString(R.string.app_name)
        val caption = buildCaptionLines(
            lensName, appName, optical, appContext.getString(R.string.lens_share_caption_via, appName)
        )
        val fileName = sanitizeFileName(lensName)
        ApplicationScope.scope.launch(Dispatchers.IO) {
            val uri = try {
                val layout = calculatePolaroidLayout(
                    contentBitmap.width,
                    contentBitmap.height,
                    if (caption.line3.isEmpty()) 2 else CAPTION_LINES_WITH_OPTICAL
                )
                val framed = renderFrame(contentBitmap, layout, caption)
                val file = writeToCache(appContext, framed, fileName)
                framed.recycle()
                FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
            } catch (error: Exception) {
                Logger.e("PolaroidExportHelper: export failed", error)
                null
            } finally {
                contentBitmap.recycle()
            }
            withContext(Dispatchers.Main) { onDone(uri) }
        }
    }

    /**
     * FISH-037: up to [PolaroidContactSheetLayout.MAX_LENSES] lenses as one sheet, each a miniature polaroid
     * with its own name and optical line, "via <app>" printed once in the footer. Main thread only: pages are
     * captured one at a time (the lens in [liveView]/[liveLensId] from the live view, every other lens from a
     * temporary LensView that is never attached to a window), drawn into the sheet and recycled at once.
     * Only compress and file write run on [Dispatchers.IO]. Delivers `null` on any failure.
     * ponytail: the sheet is always written to cache/polaroid/lenses.png, so a new export replaces the last one.
     * ponytail: the page/cell `recycle()` calls bound peak memory but change no pixel, so no test can see them
     * (mutation "skip recycle" survives by design); the 6-lens and 64 MB caps are what the tests pin.
     */
    @JvmStatic
    fun exportContactSheetAsync(
        context: Context,
        host: View,
        specs: List<LensExportSpec>,
        liveView: LensView?,
        liveLensId: String?,
        appsForLens: (String) -> ArrayList<App>,
        onDone: (Uri?) -> Unit
    ) {
        val chosen = specs.take(PolaroidContactSheetLayout.MAX_LENSES)
        val live = liveView?.takeIf { it.width > 0 && it.height > 0 }
        val pageWidth = live?.width ?: host.width
        val pageHeight = live?.height ?: host.height
        if (chosen.isEmpty() || pageWidth <= 0 || pageHeight <= 0) {
            onDone(null)
            return
        }
        val appContext = context.applicationContext
        val frame = calculatePolaroidLayout(pageWidth, pageHeight)
        val sheetLayout = PolaroidContactSheetLayout.compute(chosen.size, frame.outerWidth, frame.outerHeight)
        val sheet = try {
            createBitmap(sheetLayout.width, sheetLayout.height, Bitmap.Config.ARGB_8888)
        } catch (error: OutOfMemoryError) {
            Logger.e("PolaroidExportHelper: sheet alloc failed", error)
            onDone(null)
            return
        }
        val appName = appContext.getString(R.string.app_name)
        val via = appContext.getString(R.string.lens_share_caption_via, appName)
        val drawn = try {
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.WHITE)
            val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)
            chosen.forEachIndexed { index, spec ->
                val page = capturePage(appContext, spec, live, liveLensId, pageWidth, pageHeight, appsForLens)
                val framed = try {
                    renderFrame(page, frame, CaptionLines(buildCaptionLine1(spec.lensName, appName), spec.optical))
                } finally {
                    page.recycle()
                }
                try {
                    val cell = sheetLayout.cells[index]
                    canvas.drawBitmap(
                        framed, null,
                        Rect(cell.left, cell.top, cell.left + cell.width, cell.top + cell.height),
                        scalePaint
                    )
                } finally {
                    framed.recycle()
                }
            }
            drawSheetFooter(canvas, sheetLayout, via)
            true
        } catch (error: OutOfMemoryError) {
            Logger.e("PolaroidExportHelper: sheet composite out of memory", error)
            false
        } catch (error: Exception) {
            Logger.e("PolaroidExportHelper: sheet composite failed", error)
            false
        }
        if (!drawn) {
            sheet.recycle()
            onDone(null)
            return
        }
        ApplicationScope.scope.launch(Dispatchers.IO) {
            var file: File? = null
            val uri = try {
                file = writeToCache(appContext, sheet, SHEET_FILE_NAME)
                FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
            } catch (error: Exception) {
                Logger.e("PolaroidExportHelper: sheet export failed", error)
                file?.delete()
                null
            } finally {
                sheet.recycle()
            }
            withContext(Dispatchers.Main) { onDone(uri) }
        }
    }

    private fun capturePage(
        context: Context,
        spec: LensExportSpec,
        live: LensView?,
        liveLensId: String?,
        width: Int,
        height: Int,
        appsForLens: (String) -> ArrayList<App>
    ): Bitmap {
        val view = if (live != null && spec.lensId == liveLensId) {
            live
        } else {
            LensView(context).apply {
                // lensId first: its setter re-arranges Smart Focus, so setting it after setApps would do it twice.
                lensId = spec.lensId
                setApps(appsForLens(spec.lensId))
                setHasOtherLenses(false)
                measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                layout(0, 0, width, height)
            }
        }
        view.resetToIdleForExport()
        return createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun drawSheetFooter(canvas: Canvas, layout: SheetLayout, via: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            textSize = SHEET_FOOTER_TEXT_SIZE_PX
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }
        canvas.drawText(via, layout.width / 2f, (layout.height - SHEET_FOOTER_BASELINE_FROM_BOTTOM_PX).toFloat(), paint)
    }
}
