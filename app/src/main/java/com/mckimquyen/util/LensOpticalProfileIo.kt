package com.mckimquyen.util

import android.content.ContentResolver
import android.net.Uri
import com.mckimquyen.model.LensOpticalProfile
import com.mckimquyen.model.OpticalProfileParseResult
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

sealed interface OpticalProfileReadResult {
    data class Success(val value: LensOpticalProfile) : OpticalProfileReadResult
    data object Malformed : OpticalProfileReadResult
    data object UnsupportedVersion : OpticalProfileReadResult
    data object TooLarge : OpticalProfileReadResult
    data object IoFailure : OpticalProfileReadResult
}

object LensOpticalProfileIo {
    private const val BUFFER_BYTES = 1024

    suspend fun read(resolver: ContentResolver, uri: Uri): OpticalProfileReadResult =
        withContext(Dispatchers.IO) {
            try {
                ensureActive()
                val result = resolver.openInputStream(uri)?.use(::readStream)
                    ?: OpticalProfileReadResult.IoFailure
                ensureActive()
                result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.e("LensOpticalProfileIo: read failed", error)
                OpticalProfileReadResult.IoFailure
            }
        }

    suspend fun write(resolver: ContentResolver, uri: Uri, value: LensOpticalProfile): Boolean =
        withContext(Dispatchers.IO) {
            try {
                ensureActive()
                val bytes = value.toJson().toByteArray(Charsets.UTF_8)
                require(bytes.size <= LensOpticalProfile.MAX_BYTES)
                resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                    ?: return@withContext false
                ensureActive()
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.e("LensOpticalProfileIo: write failed", error)
                false
            }
        }

    /** Caller đóng stream; chỉ đọc tối đa MAX_BYTES + 1 để phát hiện file quá lớn. */
    internal fun readStream(stream: InputStream): OpticalProfileReadResult {
        return try {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            while (output.size() <= LensOpticalProfile.MAX_BYTES) {
                val remaining = LensOpticalProfile.MAX_BYTES + 1 - output.size()
                val count = stream.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                if (count == 0) {
                    val byte = stream.read()
                    if (byte < 0) break
                    output.write(byte)
                } else {
                    output.write(buffer, 0, count)
                }
            }
            if (output.size() > LensOpticalProfile.MAX_BYTES) return OpticalProfileReadResult.TooLarge
            val json = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray())).toString()
            when (val result = LensOpticalProfile.parse(json)) {
                is OpticalProfileParseResult.Success -> OpticalProfileReadResult.Success(result.value)
                OpticalProfileParseResult.Malformed -> OpticalProfileReadResult.Malformed
                OpticalProfileParseResult.UnsupportedVersion -> OpticalProfileReadResult.UnsupportedVersion
            }
        } catch (_: CharacterCodingException) {
            OpticalProfileReadResult.Malformed
        } catch (_: IOException) {
            OpticalProfileReadResult.IoFailure
        }
    }
}
