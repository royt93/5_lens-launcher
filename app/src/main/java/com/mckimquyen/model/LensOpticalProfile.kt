package com.mckimquyen.model

import android.util.JsonReader
import android.util.JsonToken
import com.mckimquyen.util.LensHood
import com.mckimquyen.util.LensProfile
import com.mckimquyen.util.UtilSettings
import org.json.JSONObject
import java.io.StringReader
import java.math.BigDecimal

/** Custom distortion thuộc lens; scale/time dùng chung như preset hiện có. */
data class OpticalCustomPreset(
    val distortionFactor: Float,
    val scaleFactor: Float,
    val animationTimeMs: Long
)

data class LensOpticalProfile(
    val profile: LensProfile,
    val distortionFactor: Float,
    val scaleFactor: Float,
    val iconSize: Float,
    val animationTimeMs: Long,
    val hood: Float,
    val smartFocusBias: Boolean,
    val custom: OpticalCustomPreset? = null
) {
    fun isValid(): Boolean =
        distortionFactor.isFinite() && distortionFactor in DISTORTION_MIN..DISTORTION_MAX &&
            scaleFactor.isFinite() && scaleFactor in SCALE_MIN..SCALE_MAX &&
            iconSize.isFinite() && iconSize in ICON_MIN..ICON_MAX &&
            animationTimeMs in ANIMATION_MIN..ANIMATION_MAX &&
            hood.isFinite() && hood in LensHood.MIN..LensHood.FULL &&
            (custom?.let {
                it.distortionFactor.isFinite() && it.distortionFactor in DISTORTION_MIN..DISTORTION_MAX &&
                    it.scaleFactor.isFinite() && it.scaleFactor in SCALE_MIN..SCALE_MAX &&
                    it.animationTimeMs in ANIMATION_MIN..ANIMATION_MAX
            } ?: true)

    fun toJson(): String {
        require(isValid()) { "Invalid optical profile" }
        return JSONObject().apply {
            put(KEY_FORMAT, FORMAT)
            put(KEY_VERSION, VERSION)
            put(KEY_OPTICAL, JSONObject().apply {
                put(KEY_PROFILE, profile.name)
                put(KEY_DISTORTION, distortionFactor.toDouble())
                put(KEY_SCALE, scaleFactor.toDouble())
                put(KEY_ICON, iconSize.toDouble())
                put(KEY_ANIMATION, animationTimeMs)
                put(KEY_HOOD, hood.toDouble())
                put(KEY_SMART_FOCUS, smartFocusBias)
            })
            custom?.let { saved ->
                put(KEY_CUSTOM, JSONObject().apply {
                    put(KEY_DISTORTION, saved.distortionFactor.toDouble())
                    put(KEY_SCALE, saved.scaleFactor.toDouble())
                    put(KEY_ANIMATION, saved.animationTimeMs)
                })
            }
        }.toString()
    }

    companion object {
        const val FORMAT = "lens-optical-profile"
        const val VERSION = 1
        const val MAX_BYTES = 16 * 1024
        const val MIME_TYPE = "application/json"
        const val FILE_NAME = "lens_optical_profile.json"

        private const val KEY_FORMAT = "format"
        private const val KEY_VERSION = "version"
        private const val KEY_OPTICAL = "optical"
        private const val KEY_CUSTOM = "custom"
        private const val KEY_PROFILE = "profile"
        private const val KEY_DISTORTION = "distortionFactor"
        private const val KEY_SCALE = "scaleFactor"
        private const val KEY_ICON = "iconSize"
        private const val KEY_ANIMATION = "animationTimeMs"
        private const val KEY_HOOD = "hood"
        private const val KEY_SMART_FOCUS = "smartFocusBias"
        private const val DISTORTION_MIN = UtilSettings.MIN_DISTORTION_FACTOR
        private const val DISTORTION_MAX = DISTORTION_MIN + UtilSettings.MAX_DISTORTION_FACTOR / 2f
        private const val SCALE_MIN = UtilSettings.MIN_SCALE_FACTOR
        private const val SCALE_MAX = SCALE_MIN + UtilSettings.MAX_SCALE_FACTOR / 2f
        private const val ICON_MIN = UtilSettings.MIN_ICON_SIZE
        private const val ICON_MAX = ICON_MIN + UtilSettings.MAX_ICON_SIZE
        private const val ANIMATION_MIN = UtilSettings.MIN_ANIMATION_TIME
        private const val ANIMATION_MAX = ANIMATION_MIN + UtilSettings.MAX_ANIMATION_TIME / 2L
        private val ROOT_KEYS = setOf(KEY_FORMAT, KEY_VERSION, KEY_OPTICAL, KEY_CUSTOM)
        private val CUSTOM_KEYS = setOf(KEY_DISTORTION, KEY_SCALE, KEY_ANIMATION)
        private val OPTICAL_KEYS = CUSTOM_KEYS + setOf(KEY_PROFILE, KEY_ICON, KEY_HOOD, KEY_SMART_FOCUS)
        private val JSON_NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

        fun parse(json: String): OpticalProfileParseResult {
            if (json.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return OpticalProfileParseResult.Malformed
            return try {
                JsonReader(StringReader(json)).use { reader ->
                    reader.isLenient = false
                    reader.beginObject()
                    val seen = mutableSetOf<String>()
                    var format: String? = null
                    var version: Long? = null
                    var optical: Map<String, Any>? = null
                    var custom: Map<String, Any>? = null
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        require(key in ROOT_KEYS && seen.add(key))
                        when (key) {
                            KEY_FORMAT -> format = reader.strictString()
                            KEY_VERSION -> version = reader.strictNumber().longValueExact()
                            KEY_OPTICAL -> optical = reader.readValues(OPTICAL_KEYS)
                            KEY_CUSTOM -> custom = reader.readValues(CUSTOM_KEYS)
                        }
                    }
                    reader.endObject()
                    require(reader.peek() == JsonToken.END_DOCUMENT)
                    require(format == FORMAT && version != null)
                    val values = requireNotNull(optical)
                    val profileName = values.getValue(KEY_PROFILE) as String
                    val shape = requireNotNull(LensProfile.entries.find { it.name == profileName })
                    val value = LensOpticalProfile(
                        profile = shape,
                        distortionFactor = values.boundedFloat(KEY_DISTORTION, DISTORTION_MIN, DISTORTION_MAX),
                        scaleFactor = values.boundedFloat(KEY_SCALE, SCALE_MIN, SCALE_MAX),
                        iconSize = values.boundedFloat(KEY_ICON, ICON_MIN, ICON_MAX),
                        animationTimeMs = values.animationTime(),
                        hood = values.boundedFloat(KEY_HOOD, LensHood.MIN, LensHood.FULL),
                        smartFocusBias = values.getValue(KEY_SMART_FOCUS) as Boolean,
                        custom = custom?.let {
                            OpticalCustomPreset(
                                it.boundedFloat(KEY_DISTORTION, DISTORTION_MIN, DISTORTION_MAX),
                                it.boundedFloat(KEY_SCALE, SCALE_MIN, SCALE_MAX),
                                it.animationTime()
                            )
                        }
                    )
                    require(value.isValid())
                    if (version == VERSION.toLong()) OpticalProfileParseResult.Success(value)
                    else OpticalProfileParseResult.UnsupportedVersion
                }
            } catch (_: java.io.IOException) {
                OpticalProfileParseResult.Malformed
            } catch (_: IllegalArgumentException) {
                OpticalProfileParseResult.Malformed
            } catch (_: IllegalStateException) {
                OpticalProfileParseResult.Malformed
            } catch (_: ArithmeticException) {
                OpticalProfileParseResult.Malformed
            }
        }

        private fun JsonReader.strictString(): String {
            require(peek() == JsonToken.STRING)
            return nextString()
        }

        private fun JsonReader.strictNumber(): BigDecimal {
            require(peek() == JsonToken.NUMBER)
            val text = nextString()
            require(JSON_NUMBER.matches(text))
            return BigDecimal(text)
        }

        private fun JsonReader.readValues(keys: Set<String>): Map<String, Any> {
            beginObject()
            val values = mutableMapOf<String, Any>()
            while (hasNext()) {
                val key = nextName()
                require(key in keys && key !in values)
                values[key] = when (key) {
                    KEY_PROFILE -> strictString()
                    KEY_SMART_FOCUS -> {
                        require(peek() == JsonToken.BOOLEAN)
                        nextBoolean()
                    }
                    else -> strictNumber()
                }
            }
            endObject()
            require(values.keys == keys)
            return values
        }

        private fun Map<String, Any>.boundedFloat(key: String, min: Float, max: Float): Float {
            val number = getValue(key) as BigDecimal
            // Kiểm decimal gốc trước khi làm tròn Float để không nhận số nằm sát ngoài biên.
            require(number >= BigDecimal(min.toString()) && number <= BigDecimal(max.toString()))
            return number.toFloat()
        }

        private fun Map<String, Any>.animationTime(): Long {
            val value = (getValue(KEY_ANIMATION) as BigDecimal).longValueExact()
            require(value in ANIMATION_MIN..ANIMATION_MAX)
            return value
        }
    }
}

sealed interface OpticalProfileParseResult {
    data class Success(val value: LensOpticalProfile) : OpticalProfileParseResult
    data object Malformed : OpticalProfileParseResult
    data object UnsupportedVersion : OpticalProfileParseResult
}
