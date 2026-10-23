package com.mckimquyen.model

import com.mckimquyen.util.LensAppScope
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DB-007: per-lens physics block in backup v2 lens JSON. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LayoutBackupPhysicsTest {

    private fun baseLensJson(id: String = "work", order: Int = 1) = JSONObject().apply {
        put("id", id)
        put("name", "Work")
        put("orderIndex", order)
        put("scope", LensAppScope.ALL.name)
        put("selectedAppIds", JSONArray())
        put("frozen", false)
        put("entries", JSONArray())
    }

    private fun lensWith(physics: LensPhysicsBackup?) = LayoutBackupLens(
        id = "work",
        name = "Work",
        orderIndex = 1,
        scope = LensAppScope.ALL,
        selectedAppIds = emptySet(),
        frozen = false,
        entries = emptyList(),
        physics = physics
    )

    @Test
    fun roundTripSerializesAndParsesAllFourPhysicsFields() {
        val physics = LensPhysicsBackup(
            distortionFactor = 2.5f,
            iconSize = 32.0f,
            customDistortionFactor = 1.75f,
            smartFocusBias = true
        )

        val json = lensWith(physics).toJson()
        assertTrue(json.has("physics"))

        assertEquals(physics, LayoutBackupLens.fromJson(json)?.physics)
    }

    @Test
    fun boundaryValuesAreAccepted() {
        val physics = LensPhysicsBackup(
            distortionFactor = 0.5f,
            iconSize = 55.0f,
            customDistortionFactor = 5.0f,
            smartFocusBias = false
        )
        assertEquals(physics, LayoutBackupLens.fromJson(lensWith(physics).toJson())?.physics)
    }

    @Test
    fun lensWithoutPhysicsOmitsTheKeyAndParsesBackAsNull() {
        val json = lensWith(null).toJson()
        assertFalse(json.has("physics"))
        assertNull(LayoutBackupLens.fromJson(json)?.physics)
    }

    @Test
    fun legacyV2WithoutPhysicsFieldParsesWithNullPhysics() {
        val parsed = LayoutBackupLens.fromJson(baseLensJson())
        assertNotNull(parsed)
        assertNull("file v2 cũ không có block physics phải parse ra null", parsed?.physics)
    }

    @Test
    fun emptyPhysicsObjectRoundTripsAsEmptyNotNull() {
        val json = lensWith(LensPhysicsBackup()).toJson()
        assertTrue("physics rỗng vẫn phải được ghi để phân biệt với file cũ", json.has("physics"))
        assertEquals(0, json.getJSONObject("physics").length())
        assertEquals(LensPhysicsBackup(), LayoutBackupLens.fromJson(json)?.physics)
    }

    @Test
    fun jsonNullFieldInsidePhysicsMeansNoOverride() {
        val json = baseLensJson().apply {
            put("physics", JSONObject().apply {
                put("distortionFactor", JSONObject.NULL)
                put("iconSize", 20.0)
            })
        }
        assertEquals(LensPhysicsBackup(iconSize = 20.0f), LayoutBackupLens.fromJson(json)?.physics)
    }

    @Test
    fun malformedPhysicsContainerRejectsEntireLens() {
        val badContainers: List<Any> = listOf("not-an-object", JSONArray(), 123, JSONObject.NULL)
        for (bad in badContainers) {
            val json = baseLensJson().apply { put("physics", bad) }
            assertNull("physics sai kiểu ($bad) phải từ chối cả lens", LayoutBackupLens.fromJson(json))
        }
    }

    @Test
    fun outOfRangeOrWrongTypeFieldsRejectEntireLens() {
        val invalid = listOf(
            JSONObject().apply { put("distortionFactor", 0.49) },
            JSONObject().apply { put("distortionFactor", 5.01) },
            JSONObject().apply { put("iconSize", 9.9) },
            JSONObject().apply { put("iconSize", 55.1) },
            JSONObject().apply { put("customDistortionFactor", 0.1) },
            JSONObject().apply { put("customDistortionFactor", 5.5) },
            JSONObject().apply { put("distortionFactor", "string-not-float") },
            JSONObject().apply { put("iconSize", true) },
            JSONObject().apply { put("smartFocusBias", "not-a-bool") },
            JSONObject().apply { put("smartFocusBias", 1) }
        )
        for (bad in invalid) {
            val json = baseLensJson().apply { put("physics", bad) }
            assertNull("giá trị không hợp lệ phải từ chối lens: $bad", LayoutBackupLens.fromJson(json))
        }
    }

    @Test
    fun oneMalformedLensPhysicsRejectsWholeV2File() {
        val good = lensWith(LensPhysicsBackup(distortionFactor = 1.0f)).copy(id = "default", name = "Home", orderIndex = 0)
        val badLens = lensWith(null).toJson().apply {
            put("physics", JSONObject().apply { put("iconSize", 999.0) })
        }
        val file = JSONObject(LayoutBackup(2, 1000L, lenses = listOf(good, lensWith(null))).toJson()).apply {
            put("lenses", JSONArray().put(good.toJson()).put(badLens))
        }
        assertEquals(LayoutBackupParseResult.Malformed, LayoutBackup.parse(file.toString()))
    }
}
