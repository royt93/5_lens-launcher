package com.mckimquyen.model

import com.mckimquyen.util.LensProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LensOpticalProfileTest {
    private fun sample(custom: OpticalCustomPreset? = null) = LensOpticalProfile(
        LensProfile.ANAMORPHIC, 3.3f, 1.3f, 22f, 250L, 0.45f, true, custom
    )

    private fun fixture() = JSONObject(VALID_JSON)

    private fun assertMalformed(json: String) {
        assertEquals(json, OpticalProfileParseResult.Malformed, LensOpticalProfile.parse(json))
    }

    @Test fun roundTripIncludesEveryFieldAndCustom() {
        val value = sample(OpticalCustomPreset(4.1f, 1.6f, 300L))
        assertEquals(OpticalProfileParseResult.Success(value), LensOpticalProfile.parse(value.toJson()))
    }

    @Test fun absentCustomStaysAbsent() {
        val value = sample()
        assertFalse(JSONObject(value.toJson()).has("custom"))
        assertEquals(OpticalProfileParseResult.Success(value), LensOpticalProfile.parse(value.toJson()))
    }

    @Test fun supportsEveryKnownProfile() {
        LensProfile.entries.forEach { profile ->
            val value = sample().copy(profile = profile)
            assertEquals(OpticalProfileParseResult.Success(value), LensOpticalProfile.parse(value.toJson()))
        }
    }

    @Test fun rejectsMissingRequiredFields() {
        listOf("format", "version", "optical").forEach { key ->
            assertMalformed(fixture().apply { remove(key) }.toString())
        }
        fixture().getJSONObject("optical").keys().asSequence().toList().forEach { key ->
            val root = fixture()
            root.getJSONObject("optical").remove(key)
            assertMalformed(root.toString())
        }
    }

    @Test fun rejectsWrongTypesWithoutCoercion() {
        val invalidValues = mapOf(
            "profile" to listOf<Any>(1, true, "UNKNOWN", JSONObject.NULL),
            "distortionFactor" to listOf<Any>("2.5", true, JSONObject.NULL, JSONObject(), emptyList<Any>()),
            "scaleFactor" to listOf<Any>("1.0", false, JSONObject.NULL),
            "iconSize" to listOf<Any>("21", true, JSONObject.NULL),
            "animationTimeMs" to listOf<Any>("200", false, 200.1, JSONObject.NULL),
            "hood" to listOf<Any>("1.0", false, JSONObject.NULL),
            "smartFocusBias" to listOf<Any>("true", 1, JSONObject.NULL)
        )
        invalidValues.forEach { (key, values) -> values.forEach { value ->
            val root = fixture()
            root.getJSONObject("optical").put(key, value)
            assertMalformed(root.toString())
        } }
    }

    @Test fun rejectsUnknownFieldsAtEveryLevel() {
        assertMalformed(fixture().put("layout", JSONObject()).toString())
        val optical = fixture()
        optical.getJSONObject("optical").put("appIds", "private")
        assertMalformed(optical.toString())
        val custom = JSONObject(sample(OpticalCustomPreset(3f, 1f, 200L)).toJson())
        custom.getJSONObject("custom").put("unknown", true)
        assertMalformed(custom.toString())
    }

    @Test fun rejectsDuplicateFieldsEvenIfValuesMatch() {
        assertMalformed(VALID_JSON.replace("\"version\":1", "\"version\":1,\"version\":1"))
        assertMalformed(VALID_JSON.replace("\"hood\":1.0", "\"hood\":1.0,\"hood\":1.0"))
        val custom = "\"custom\":{\"distortionFactor\":3,\"scaleFactor\":1,\"animationTimeMs\":200,\"animationTimeMs\":200}"
        assertMalformed(VALID_JSON.dropLast(1) + "," + custom + "}")
    }

    @Test fun rejectsMalformedJsonAndTrailingData() {
        listOf("", "[]", "null", "{", VALID_JSON + "{}", VALID_JSON + " garbage",
            VALID_JSON.replace("\"hood\":1.0", "hood:1.0"),
            VALID_JSON.replace("\"profile\":\"FISHEYE\"", "\"profile\":'FISHEYE'"),
            VALID_JSON.replace("\"version\":1", "\"version\":01"),
            "/* comment */$VALID_JSON").forEach(::assertMalformed)
        assertTrue(LensOpticalProfile.parse(" \n$VALID_JSON\t") is OpticalProfileParseResult.Success)
    }

    @Test fun distinguishesUnsupportedVersionFromInvalidHeader() {
        assertEquals(OpticalProfileParseResult.UnsupportedVersion,
            LensOpticalProfile.parse(fixture().put("version", 2).toString()))
        listOf<Any>("1", true, 1.5, JSONObject.NULL, "9223372036854775808").forEach { version ->
            assertMalformed(fixture().put("version", version).toString())
        }
        assertMalformed(fixture().put("format", "workspace-backup").toString())
    }

    @Test fun acceptsRangeBoundariesWithoutSliderSnapping() {
        listOf(
            sample().copy(distortionFactor = 0.5f, scaleFactor = 1f, iconSize = 10f, animationTimeMs = 100, hood = 0.2f),
            sample().copy(distortionFactor = 5f, scaleFactor = 3.5f, iconSize = 55f, animationTimeMs = 400, hood = 1f),
            sample().copy(hood = 0.333f)
        ).forEach { value ->
            assertTrue(value.isValid())
            assertEquals(OpticalProfileParseResult.Success(value), LensOpticalProfile.parse(value.toJson()))
        }
    }

    @Test fun rejectsNumbersOutsideRangeBeforeFloatRounding() {
        mapOf(
            "distortionFactor" to listOf(0.499999999, 5.000000001),
            "scaleFactor" to listOf(0.999999999, 3.500000001),
            "iconSize" to listOf(9.999999999, 55.000000001),
            "animationTimeMs" to listOf(99, 401, Long.MAX_VALUE),
            "hood" to listOf(0.199999999, 1.000000001)
        ).forEach { (key, values) -> values.forEach { value ->
            val root = fixture()
            root.getJSONObject("optical").put(key, value)
            assertMalformed(root.toString())
        } }
    }

    @Test fun rejectsNonFiniteAndOverflowNumbers() {
        listOf("NaN", "Infinity", "-Infinity", "1e9999", "-1e9999", "1e-9999").forEach { number ->
            assertMalformed(VALID_JSON.replace("\"distortionFactor\":2.5", "\"distortionFactor\":$number"))
        }
        assertMalformed(VALID_JSON.replace("\"animationTimeMs\":200", "\"animationTimeMs\":9223372036854775808"))
        assertMalformed(VALID_JSON.replace("\"animationTimeMs\":200", "\"animationTimeMs\":200.000000000000000001"))
    }

    @Test fun customObjectMustBeCompleteAndValid() {
        listOf<Any>(JSONObject.NULL, true, "preset", JSONObject()).forEach { custom ->
            assertMalformed(fixture().put("custom", custom).toString())
        }
        val saved = OpticalCustomPreset(3f, 1f, 200L)
        listOf("distortionFactor", "scaleFactor", "animationTimeMs").forEach { key ->
            val root = JSONObject(sample(saved).toJson())
            root.getJSONObject("custom").remove(key)
            assertMalformed(root.toString())
        }
        listOf(saved.copy(distortionFactor = 5.1f), saved.copy(scaleFactor = 0.9f),
            saved.copy(animationTimeMs = 401)).forEach { invalid ->
            assertFalse(sample(invalid).isValid())
        }
        val root = fixture().put("custom", JSONObject()
            .put("distortionFactor", 5.000000001).put("scaleFactor", 1).put("animationTimeMs", 200))
        assertMalformed(root.toString())
    }

    @Test fun serializerRejectsInvalidTypedPayload() {
        listOf(sample().copy(distortionFactor = Float.NaN), sample().copy(scaleFactor = Float.POSITIVE_INFINITY),
            sample().copy(iconSize = 56f), sample().copy(animationTimeMs = 0), sample().copy(hood = 0f)).forEach { value ->
            assertFalse(value.isValid())
            assertThrows(IllegalArgumentException::class.java) { value.toJson() }
        }
    }

    @Test fun outputContainsOnlyPublicOpticalFields() {
        val root = JSONObject(sample().toJson())
        assertEquals(setOf("format", "version", "optical"), root.keys().asSequence().toSet())
        assertEquals(setOf("profile", "distortionFactor", "scaleFactor", "iconSize", "animationTimeMs", "hood", "smartFocusBias"),
            root.getJSONObject("optical").keys().asSequence().toSet())
    }

    private companion object {
        const val VALID_JSON = "{\"format\":\"lens-optical-profile\",\"version\":1,\"optical\":{\"profile\":\"FISHEYE\",\"distortionFactor\":2.5,\"scaleFactor\":1.0,\"iconSize\":21.0,\"animationTimeMs\":200,\"hood\":1.0,\"smartFocusBias\":false}}"
    }
}
