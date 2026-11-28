package com.mckimquyen.util

import com.mckimquyen.model.LayoutBackup
import com.mckimquyen.model.LayoutBackupParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Audit R5: the layout import read the whole picked file with `readText()`, so a huge file filled the heap with an OutOfMemoryError that the `catch (Exception)`
 * did not catch and the process died. The reader now stops at MAX_IMPORT_BYTES + 1 bytes and a file over the cap becomes "malformed".
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LayoutBackupIoBoundedReadTest {

    /** Hands out [total] bytes without ever allocating them, and counts how many were really read. */
    private class EndlessStream(private val total: Long) : InputStream() {
        var served = 0L
        override fun read(): Int = if (served >= total) -1 else { served++; ' '.code }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (served >= total) return -1
            val n = minOf(len.toLong(), total - served).toInt()
            java.util.Arrays.fill(b, off, off + n, ' '.code.toByte())
            served += n
            return n
        }
    }

    @Test fun aNormalFileIsReadWhole() {
        val json = """{"schemaVersion":1,"entries":[]}"""

        assertEquals(json, LayoutBackupIo.readBoundedText(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))))
    }

    @Test fun multiByteTextSurvives() {
        val json = "{\"name\":\"Thư mục — 日本語\"}"

        assertEquals(json, LayoutBackupIo.readBoundedText(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))))
    }

    @Test fun aFileExactlyAtTheCapIsStillRead() {
        val stream = EndlessStream(LayoutBackupIo.MAX_IMPORT_BYTES.toLong())

        val text = LayoutBackupIo.readBoundedText(stream)

        assertEquals(LayoutBackupIo.MAX_IMPORT_BYTES, text.length)
    }

    @Test fun aFileOverTheCapIsRefusedWithoutReadingItAll() {
        val stream = EndlessStream(Long.MAX_VALUE / 2)

        val text = LayoutBackupIo.readBoundedText(stream)

        assertEquals(LayoutBackupIo.TOO_LARGE_MARKER, text)
        assertTrue("it must stop at the cap plus one chunk, read ${stream.served}", stream.served <= LayoutBackupIo.MAX_IMPORT_BYTES + 8 * 1024L)
    }

    @Test fun theRefusalIsReportedByTheParserAsMalformed() {
        // Robolectric supplies the real org.json, so the marker really goes through LayoutBackup.parse.
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(LayoutBackupIo.TOO_LARGE_MARKER))
    }
}
