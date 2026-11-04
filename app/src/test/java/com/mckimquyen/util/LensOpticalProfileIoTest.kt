package com.mckimquyen.util

import com.mckimquyen.model.LensOpticalProfile
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.ByteArrayOutputStream
import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verifyNoInteractions
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LensOpticalProfileIoTest {
    private fun paddedJson(byteCount: Int): ByteArray {
        val json = LensOpticalProfile(
            LensProfile.FISHEYE, 2.5f, 1f, 21f, 200L, 1f, false
        ).toJson()
        val bytes = json.toByteArray(Charsets.UTF_8)
        require(byteCount >= bytes.size)
        return bytes + ByteArray(byteCount - bytes.size) { ' '.code.toByte() }
    }

    @Test fun acceptsInputAtExactByteLimit() {
        assertEquals(
            OpticalProfileReadResult.Success(
                LensOpticalProfile(LensProfile.FISHEYE, 2.5f, 1f, 21f, 200L, 1f, false)
            ),
            LensOpticalProfileIo.readStream(ByteArrayInputStream(paddedJson(LensOpticalProfile.MAX_BYTES)))
        )
    }

    @Test fun rejectsInputOneByteOverLimitWithoutReadingPastLimitPlusOne() {
        val stream = CountingInputStream(paddedJson(LensOpticalProfile.MAX_BYTES + 10))
        assertEquals(OpticalProfileReadResult.TooLarge, LensOpticalProfileIo.readStream(stream))
        assertEquals(LensOpticalProfile.MAX_BYTES + 1, stream.bytesRead)
    }

    @Test fun rejectsMalformedUtf8InsteadOfReplacingBytes() {
        assertEquals(
            OpticalProfileReadResult.Malformed,
            LensOpticalProfileIo.readStream(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28)))
        )
    }

    @Test fun rejectsIoFailureDuringRead() {
        val stream = object : InputStream() {
            private var sent = false
            override fun read(): Int {
                if (!sent) {
                    sent = true
                    return '{'.code
                }
                throw IOException("simulated read failure")
            }
        }
        assertEquals(OpticalProfileReadResult.IoFailure, LensOpticalProfileIo.readStream(stream))
    }

    @Test fun resolverReadClosesStreamAndPreservesUnsupportedVersion() = runBlocking {
        val resolver = mock(ContentResolver::class.java)
        val uri = Uri.parse("content://f036/test")
        val json = String(paddedJson(400), Charsets.UTF_8).replace("\"version\":1", "\"version\":2")
        var closed = false
        val stream = object : ByteArrayInputStream(json.toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        `when`(resolver.openInputStream(uri)).thenReturn(stream)
        assertEquals(OpticalProfileReadResult.UnsupportedVersion, LensOpticalProfileIo.read(resolver, uri))
        assertTrue(closed)
    }

    @Test fun nullResolverStreamsReportFailure() = runBlocking {
        val resolver = mock(ContentResolver::class.java)
        val uri = Uri.parse("content://f036/missing")
        assertEquals(OpticalProfileReadResult.IoFailure, LensOpticalProfileIo.read(resolver, uri))
        assertFalse(LensOpticalProfileIo.write(resolver, uri, sample()))
    }

    @Test fun writesUtf8JsonAndClosesOutput() = runBlocking {
        val resolver = mock(ContentResolver::class.java)
        val uri = Uri.parse("content://f036/output")
        var closed = false
        val stream = object : ByteArrayOutputStream() {
            override fun close() { closed = true; super.close() }
        }
        `when`(resolver.openOutputStream(uri, "wt")).thenReturn(stream)
        assertTrue(LensOpticalProfileIo.write(resolver, uri, sample()))
        assertTrue(closed)
        assertEquals(sample().toJson(), stream.toString("UTF-8"))
    }

    @Test fun closeFailureDoesNotReportSuccessfulWriteOrRead() = runBlocking {
        val resolver = mock(ContentResolver::class.java)
        val uri = Uri.parse("content://f036/close-error")
        val output = object : ByteArrayOutputStream() {
            override fun close() { throw IOException("simulated close failure") }
        }
        val input = object : ByteArrayInputStream(sample().toJson().toByteArray()) {
            override fun close() { throw IOException("simulated close failure") }
        }
        `when`(resolver.openOutputStream(uri, "wt")).thenReturn(output)
        `when`(resolver.openInputStream(uri)).thenReturn(input)
        assertFalse(LensOpticalProfileIo.write(resolver, uri, sample()))
        assertEquals(OpticalProfileReadResult.IoFailure, LensOpticalProfileIo.read(resolver, uri))
    }

    @Test fun cancellationIsNotConvertedToIoFailure() = runBlocking {
        val resolver = mock(ContentResolver::class.java)
        val uri = Uri.parse("content://f036/cancel")
        `when`(resolver.openInputStream(uri)).thenThrow(CancellationException("cancelled"))
        try {
            LensOpticalProfileIo.read(resolver, uri)
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) {
            // Đây là kết quả yêu cầu, không chuyển thành IoFailure.
        }
    }

    @Test fun writeFailureClosesStreamAndDoesNotReportSuccess() = runBlocking {
        val resolver = mock(ContentResolver::class.java)
        val uri = Uri.parse("content://f036/write-error")
        var closed = false
        val stream = object : java.io.OutputStream() {
            override fun write(value: Int) { throw IOException("simulated write failure") }
            override fun close() { closed = true }
        }
        `when`(resolver.openOutputStream(uri, "wt")).thenReturn(stream)
        assertFalse(LensOpticalProfileIo.write(resolver, uri, sample()))
        assertTrue(closed)
    }

    @Test fun invalidTypedProfileDoesNotOpenDestination() = runBlocking {
        val resolver = mock(ContentResolver::class.java)
        assertFalse(LensOpticalProfileIo.write(resolver, Uri.parse("content://f036/invalid"), sample().copy(hood = Float.NaN)))
        verifyNoInteractions(resolver)
    }

    @Test fun zeroLengthReadDoesNotSpinAndCallerOwnsStream() {
        var closed = false
        val source = ByteArrayInputStream(sample().toJson().toByteArray())
        val stream = object : InputStream() {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
            override fun read(): Int = source.read()
            override fun close() { closed = true }
        }
        assertEquals(OpticalProfileReadResult.Success(sample()), LensOpticalProfileIo.readStream(stream))
        assertFalse(closed)
        stream.close()
        assertTrue(closed)
    }

    private fun sample() = LensOpticalProfile(LensProfile.FISHEYE, 2.5f, 1f, 21f, 200L, 1f, false)

    private class CountingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var bytesRead = 0
            private set

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) bytesRead += it }

        override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }
    }
}
