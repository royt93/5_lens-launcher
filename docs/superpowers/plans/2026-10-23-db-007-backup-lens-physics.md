# DB-007 Backup Lens Physics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xuất và khôi phục 4 cài đặt physics/độ cong riêng từng lens trong backup v2 mà không làm mất tương thích ngược với file cũ và giữ nguyên cơ chế kế thừa của lens phụ.

**Architecture:** Bổ sung `LensPhysicsBackup` tùy chọn vào `LayoutBackupLens` và `LensAppSettingsBackup`. Export tạo `physics` object cho mọi lens, kể cả `{}`, chỉ đọc raw preference khi key tồn tại. Import file cũ (không có block `physics`) giữ nguyên physics; block có mặt ghi trường có giá trị, xóa trường thiếu ở lens phụ để kế thừa, còn lens default giữ key chung khi trường thiếu. Snapshot/restore bổ sung mọi key physics hiện có, gồm base keys và key hậu tố của tất cả lens.

**Tech Stack:** Kotlin, JSON (`org.json.JSONObject`), SharedPreferences, JUnit4, Robolectric sdk 28, AndroidX Test (`ActivityScenario`), Gradle.

## Global Constraints

- Spec đã duyệt: `docs/superpowers/specs/2026-10-23-db-007-backup-lens-physics-design.md`.
- Nhánh `dev` duy nhất; không tạo worktree, không thêm dependency mới, không tăng `schemaVersion` (vẫn là schema v2).
- Thiết bị khóa: Pixel 7 Pro `2B051FDH3006MU`, Android 17. Chỉ dùng `adb -s 2B051FDH3006MU`; tuyệt đối không dùng Gradle `connected*`.
- Bốn key physics và khoảng hợp lệ (lấy từ hằng `UtilSettings`):
  - `distortion_factor`: `MIN_DISTORTION_FACTOR` (0.5f) .. `MAX_DISTORTION_FACTOR / 2f + MIN_DISTORTION_FACTOR` (5.0f).
  - `min_icon_size`: `MIN_ICON_SIZE` (10.0f) .. `MAX_ICON_SIZE + MIN_ICON_SIZE` (55.0f).
  - `custom_distortion_factor`: `MIN_DISTORTION_FACTOR` (0.5f) .. `MAX_DISTORTION_FACTOR / 2f + MIN_DISTORTION_FACTOR` (5.0f).
  - `smart_focus_bias`: `Boolean`.
- Quy tắc lens default vs lens phụ:
  - Lens default dùng key không hậu tố (`lensKey(baseKey, "default") == baseKey`). Đây cũng là giá trị mà lens phụ kế thừa.
  - Khi import file mới: lens phụ có trường `null` thì **xóa** key để kế thừa; lens default có trường `null` thì **giữ nguyên** key trên máy (không xóa key chung).
- Toàn vẹn: giá trị physics ngoài khoảng, NaN, Infinity hoặc sai kiểu làm file bị từ chối (`fromJson` trả `null`), không clamp ngầm.
- Hoàn tất mọi cổng kiểm tra (`testDevDebugUnitTest`, `lintDevDebug`, `assembleDevDebug`, `assembleDevDebugAndroidTest`), kiểm tra bí mật (`scripts/check-secrets.sh`) và audit record đạt điểm `> 9.0/10` trước khi commit/push. Không stage `.idea`.

---

## File Map

- Modify: `app/src/main/java/com/mckimquyen/model/LayoutBackup.kt` — thêm `LensPhysicsBackup`, mở rộng `LayoutBackupLens`, parse/serialize JSON.
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt` — mở rộng `LensAppSettingsBackup`, `replaceWorkspacePreferences`, snapshot và rollback.
- Modify: `app/src/main/java/com/mckimquyen/util/LayoutBackupSnapshotFactory.kt` — chuyển `physics` từ settings sang `LayoutBackupLens`.
- Modify: `app/src/main/java/com/mckimquyen/util/LayoutImportApplier.kt` — chuyển `physics` từ parsed lens sang `WorkspaceLensPlan` và `toAppSettingsBackup`.
- Modify: `app/src/main/java/com/mckimquyen/util/LayoutBackupIo.kt` — export đọc 4 key bằng `prefs.contains` và đóng gói vào `LensAppSettingsBackup`.
- Create/Extend Unit tests:
  - `app/src/test/java/com/mckimquyen/model/LayoutBackupPhysicsTest.kt` — parse/serialize JSON, khoảng hợp lệ, malformed, tương thích ngược v2 cũ.
  - `app/src/test/java/com/mckimquyen/util/UtilSettingsPhysicsBackupTest.kt` — import ghi/xóa key, default chỉ ghi, file cũ không chạm, rollback.
- Create/Extend Device test:
  - `app/src/androidTest/java/com/mckimquyen/util/WorkspaceBackupPhysicsIntegrationTest.kt` — export và import khứ hồi đa lens trên thiết bị thật.

---

## Task 1: Tầng Model — LensPhysicsBackup & JSON Parse/Serialize

**Files:**
- Create: `app/src/test/java/com/mckimquyen/model/LayoutBackupPhysicsTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/model/LayoutBackup.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class LensPhysicsBackup(
      val distortionFactor: Float? = null,
      val iconSize: Float? = null,
      val customDistortionFactor: Float? = null,
      val smartFocusBias: Boolean? = null
  )
  ```
  `LayoutBackupLens(..., val physics: LensPhysicsBackup? = null)`

- [ ] **Step 1: Viết unit test RED cho parse và serialize physics trong LayoutBackupLens**

Tạo `app/src/test/java/com/mckimquyen/model/LayoutBackupPhysicsTest.kt`:

```kotlin
package com.mckimquyen.model

import com.mckimquyen.util.LensAppScope
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun roundTripSerializesAndParsesAllFourPhysicsFields() {
        val physics = LensPhysicsBackup(
            distortionFactor = 2.5f,
            iconSize = 32.0f,
            customDistortionFactor = 1.8f,
            smartFocusBias = true
        )
        val lens = LayoutBackupLens(
            id = "work",
            name = "Work",
            orderIndex = 1,
            scope = LensAppScope.ALL,
            selectedAppIds = emptySet(),
            frozen = false,
            entries = emptyList(),
            physics = physics
        )

        val json = lens.toJson()
        assertTrue(json.has("physics"))
        val parsed = LayoutBackupLens.fromJson(json)

        assertNotNull(parsed)
        assertEquals(physics, parsed?.physics)
    }

    @Test
    fun legacyV2WithoutPhysicsFieldParsesWithNullPhysics() {
        val json = baseLensJson()
        val parsed = LayoutBackupLens.fromJson(json)
        assertNotNull(parsed)
        assertNull("file v2 cũ không có block physics phải parse ra null", parsed?.physics)
    }

    @Test
    fun emptyPhysicsObjectParsesWithAllNullFields() {
        val json = baseLensJson().apply {
            put("physics", JSONObject())
        }
        val parsed = LayoutBackupLens.fromJson(json)
        assertNotNull(parsed)
        assertEquals(LensPhysicsBackup(), parsed?.physics)
    }

    @Test
    fun malformedPhysicsTypeRejectsEntireLens() {
        val badTypes = listOf(
            "not-an-object",
            JSONArray(),
            123,
            JSONObject.NULL
        )
        for (bad in badTypes) {
            val json = baseLensJson().apply { put("physics", bad) }
            assertNull("physics sai kiểu ($bad) phải từ chối cả lens", LayoutBackupLens.fromJson(json))
        }
    }

    @Test
    fun outOfRangeOrNonFiniteFloatsRejectEntireLens() {
        // distortion min 0.5 max 5.0; iconSize min 10.0 max 55.0
        val invalidObjects = listOf(
            JSONObject().apply { put("distortionFactor", 0.49) }, // dưới min 0.5
            JSONObject().apply { put("distortionFactor", 5.01) }, // trên max 5.0
            JSONObject().apply { put("iconSize", 9.9) },          // dưới min 10.0
            JSONObject().apply { put("iconSize", 55.1) },         // trên max 55.0
            JSONObject().apply { put("customDistortionFactor", 0.1) },
            JSONObject().apply { put("distortionFactor", "string-not-float") },
            JSONObject().apply { put("smartFocusBias", "not-a-bool") }
        )
        for (bad in invalidObjects) {
            val json = baseLensJson().apply { put("physics", bad) }
            assertNull("giá trị ngoài khoảng phải từ chối lens: $bad", LayoutBackupLens.fromJson(json))
        }
    }
}
```

- [ ] **Step 2: Chạy kiểm tra để xác nhận RED**

Run: `./gradlew testDevDebugUnitTest --tests '*LayoutBackupPhysicsTest'`
Expected: FAIL do chưa có `LensPhysicsBackup` và `LayoutBackupLens` chưa có tham số `physics`.

- [ ] **Step 3: Cài đặt LensPhysicsBackup và cập nhật LayoutBackupLens**

Trong `app/src/main/java/com/mckimquyen/model/LayoutBackup.kt`:
1. Thêm `data class LensPhysicsBackup`:
   ```kotlin
   data class LensPhysicsBackup(
       val distortionFactor: Float? = null,
       val iconSize: Float? = null,
       val customDistortionFactor: Float? = null,
       val smartFocusBias: Boolean? = null
   ) {
       fun toJson(): JSONObject = JSONObject().apply {
           distortionFactor?.let { put(KEY_DISTORTION_FACTOR, it.toDouble()) }
           iconSize?.let { put(KEY_ICON_SIZE, it.toDouble()) }
           customDistortionFactor?.let { put(KEY_CUSTOM_DISTORTION_FACTOR, it.toDouble()) }
           smartFocusBias?.let { put(KEY_SMART_FOCUS_BIAS, it) }
       }

       companion object {
           const val KEY_DISTORTION_FACTOR = "distortionFactor"
           const val KEY_ICON_SIZE = "iconSize"
           const val KEY_CUSTOM_DISTORTION_FACTOR = "customDistortionFactor"
           const val KEY_SMART_FOCUS_BIAS = "smartFocusBias"
       }
   }
   ```
2. Thêm `val physics: LensPhysicsBackup? = null` vào `LayoutBackupLens`.
3. Trong `LayoutBackupLens.toJson()`:
   ```kotlin
   physics?.let { put(KEY_PHYSICS, it.toJson()) }
   ```
4. Thêm `private const val KEY_PHYSICS = "physics"` và validation helper trong companion của `LayoutBackupLens`:
   ```kotlin
   private fun JSONObject.boundedFloat(key: String, min: Float, max: Float): Float? {
       if (!has(key) || isNull(key)) return null
       val number = opt(key) as? Number ?: throw IllegalArgumentException("Not a number")
       val value = number.toFloat()
       if (!value.isFinite() || value < min || value > max) {
           throw IllegalArgumentException("Out of bounds")
       }
       return value
   }
   ```
5. Trong `LayoutBackupLens.fromJson(json)`:
   Parse `physics` an toàn; nếu `json.has(KEY_PHYSICS)` mà không phải `JSONObject` hoặc ném exception do sai khoảng thì `return null`. Trả về `LayoutBackupLens(..., physics = parsedPhysics)`.

- [ ] **Step 4: Chạy lại test để xác nhận GREEN**

Run: `./gradlew testDevDebugUnitTest --tests '*LayoutBackupPhysicsTest' --tests '*LayoutBackupExportSnapshotTest'`
Expected: All tests pass.

---

## Task 2: Tầng UtilSettings — Lưu trữ, Xóa override và Rollback

**Files:**
- Create: `app/src/test/java/com/mckimquyen/util/UtilSettingsPhysicsBackupTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`

**Interfaces:**
- `LensAppSettingsBackup(..., val physics: LensPhysicsBackup? = null)`
- `replaceWorkspacePreferences` ghi 4 key khi có giá trị; xóa override lens phụ khi null; lens default giữ nguyên khi null.
- `snapshotWorkspacePreferences` & `restoreWorkspacePreferences` bao gồm đầy đủ 4 key cho mọi lens.

- [ ] **Step 1: Viết unit test RED cho replace, default rule và rollback trong UtilSettings**

Tạo `app/src/test/java/com/mckimquyen/util/UtilSettingsPhysicsBackupTest.kt`:

```kotlin
package com.mckimquyen.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.mckimquyen.model.LensPhysicsBackup
import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class UtilSettingsPhysicsBackupTest {

    private lateinit var context: Context
    private lateinit var settings: UtilSettings
    private val defaultLens = LensWorkspace.DEFAULT_LENS_ID
    private val workLens = "work"

    private fun rawPrefs() = PreferenceManager.getDefaultSharedPreferences(context)

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        rawPrefs().edit().clear().commit()
        settings = UtilSettings(context)
    }

    @Test
    fun importAppliesOverridesToSecondaryLensAndClearsUnspecifiedOverrides() {
        // Máy có sẵn override cho workLens
        settings.saveDistortionFactor(workLens, 3.0f)
        settings.saveIconSize(workLens, 40.0f)
        settings.saveSmartFocusBias(workLens, true)

        // File mới chỉ mang distortionFactor cho workLens, các trường khác null (không override)
        val workPhysics = LensPhysicsBackup(distortionFactor = 1.5f, iconSize = null, smartFocusBias = null)
        val defaultPhysics = LensPhysicsBackup(distortionFactor = 2.0f, iconSize = 25.0f)

        settings.replaceWorkspacePreferences(
            listOf(
                LensAppSettingsBackup(defaultLens, LensAppScope.ALL, emptySet(), false, physics = defaultPhysics),
                LensAppSettingsBackup(workLens, LensAppScope.ALL, emptySet(), false, physics = workPhysics)
            ),
            defaultLens
        )

        // workLens: distortion được cập nhật; iconSize và smartFocus bị xóa override để kế thừa default
        assertEquals(1.5f, settings.getDistortionFactor(workLens), 0.01f)
        assertFalse(rawPrefs().contains("${UtilSettings.KEY_ICON_SIZE}_$workLens"))
        assertFalse(rawPrefs().contains("${UtilSettings.KEY_SMART_FOCUS_BIAS}_$workLens"))

        // defaultLens: các key không hậu tố được ghi đúng
        assertEquals(2.0f, settings.getDistortionFactor(defaultLens), 0.01f)
        assertEquals(25.0f, settings.getIconSize(defaultLens), 0.01f)
    }

    @Test
    fun defaultLensRetainsExistingKeysWhenNewBackupOmitsThem() {
        // Default lens đã có sẵn iconSize trên máy
        settings.saveIconSize(defaultLens, 30.0f)

        // Backup mới có block physics cho default nhưng không có iconSize (null)
        val defaultPhysics = LensPhysicsBackup(distortionFactor = 1.8f, iconSize = null)

        settings.replaceWorkspacePreferences(
            listOf(
                LensAppSettingsBackup(defaultLens, LensAppScope.ALL, emptySet(), false, physics = defaultPhysics)
            ),
            defaultLens
        )

        // Quyết định owner: key default không được xóa khi thiếu trường để không làm đổi giá trị kế thừa của lens phụ
        assertEquals(1.8f, settings.getDistortionFactor(defaultLens), 0.01f)
        assertTrue("key chung của default không được bị xóa", rawPrefs().contains(UtilSettings.KEY_ICON_SIZE))
        assertEquals(30.0f, settings.getIconSize(defaultLens), 0.01f)
    }

    @Test
    fun legacyBackupWithNullPhysicsLeavesAllDevicePhysicsUntouched() {
        settings.saveDistortionFactor(defaultLens, 2.2f)
        settings.saveDistortionFactor(workLens, 4.4f)

        // Backup v2 cũ: physics = null
        settings.replaceWorkspacePreferences(
            listOf(
                LensAppSettingsBackup(defaultLens, LensAppScope.ALL, emptySet(), false, physics = null),
                LensAppSettingsBackup(workLens, LensAppScope.ALL, emptySet(), false, physics = null)
            ),
            defaultLens
        )

        assertEquals(2.2f, settings.getDistortionFactor(defaultLens), 0.01f)
        assertEquals(4.4f, settings.getDistortionFactor(workLens), 0.01f)
    }

    @Test
    fun rollbackRestoresPreviousPhysicsStateCompletely() {
        settings.saveDistortionFactor(defaultLens, 2.0f)
        settings.saveDistortionFactor(workLens, 3.5f)

        val snapshot = settings.snapshotWorkspacePreferences()

        // Thay đổi bằng import mới
        val newDefault = LensPhysicsBackup(distortionFactor = 1.0f)
        val newWork = LensPhysicsBackup(distortionFactor = 5.0f)
        settings.replaceWorkspacePreferences(
            listOf(
                LensAppSettingsBackup(defaultLens, LensAppScope.ALL, emptySet(), false, physics = newDefault),
                LensAppSettingsBackup(workLens, LensAppScope.ALL, emptySet(), false, physics = newWork)
            ),
            defaultLens
        )
        assertEquals(1.0f, settings.getDistortionFactor(defaultLens), 0.01f)

        // Rollback
        assertTrue(settings.restoreWorkspacePreferences(snapshot))

        assertEquals(2.0f, settings.getDistortionFactor(defaultLens), 0.01f)
        assertEquals(3.5f, settings.getDistortionFactor(workLens), 0.01f)
    }
}
```

- [ ] **Step 2: Chạy kiểm tra để xác nhận RED**

Run: `./gradlew testDevDebugUnitTest --tests '*UtilSettingsPhysicsBackupTest'`
Expected: FAIL do chưa có tham số `physics` trong `LensAppSettingsBackup` và `replaceWorkspacePreferences` chưa xử lý physics.

- [ ] **Step 3: Cập nhật UtilSettings.kt**

1. Cập nhật `LensAppSettingsBackup`:
   ```kotlin
   data class LensAppSettingsBackup(
       val lensId: String,
       val scope: LensAppScope,
       val selectedAppIds: Set<String>,
       val frozen: Boolean,
       val temporarilyHiddenAppIds: Set<String> = emptySet(),
       val physics: LensPhysicsBackup? = null
   )
   ```
2. Thêm danh sách base keys physics vào `UtilSettings`:
   ```kotlin
   companion object {
       val LENS_PHYSICS_BASE_KEYS = listOf(
           KEY_DISTORTION_FACTOR,
           KEY_ICON_SIZE,
           KEY_CUSTOM_DISTORTION_FACTOR,
           KEY_SMART_FOCUS_BIAS
       )
   }
   ```
3. Trong `replaceWorkspacePreferences`:
   Với mỗi lens trong `lensSettings`:
   ```kotlin
   val physics = lens.physics
   if (physics != null) {
       val isDefault = lens.lensId == LensWorkspace.DEFAULT_LENS_ID
       // distortionFactor
       val distKey = lensKey(KEY_DISTORTION_FACTOR, lens.lensId)
       if (physics.distortionFactor != null) editor.putFloat(distKey, physics.distortionFactor)
       else if (!isDefault) editor.remove(distKey)

       // iconSize
       val iconKey = lensKey(KEY_ICON_SIZE, lens.lensId)
       if (physics.iconSize != null) editor.putFloat(iconKey, physics.iconSize)
       else if (!isDefault) editor.remove(iconKey)

       // customDistortionFactor
       val customKey = lensKey(KEY_CUSTOM_DISTORTION_FACTOR, lens.lensId)
       if (physics.customDistortionFactor != null) editor.putFloat(customKey, physics.customDistortionFactor)
       else if (!isDefault) editor.remove(customKey)

       // smartFocusBias
       val biasKey = lensKey(KEY_SMART_FOCUS_BIAS, lens.lensId)
       if (physics.smartFocusBias != null) editor.putBoolean(biasKey, physics.smartFocusBias)
       else if (!isDefault) editor.remove(biasKey)
   }
   ```
4. Thêm `private fun isLensPhysicsKey(key: String)`: khớp mỗi base key trong `LENS_PHYSICS_BASE_KEYS` hoặc `"${base}_"` + hậu tố. KHÔNG sửa `isWorkspacePreferenceKey` (import file cũ không được xóa physics).
5. `snapshotWorkspacePreferences`: gộp thêm `prefs.all.filterKeys(::isLensPhysicsKey)` vào `values` (Float/Boolean, bỏ null).
6. `restoreWorkspacePreferences`: xóa mọi key thỏa `isWorkspacePreferenceKey || isLensPhysicsKey` rồi ghi lại từ snapshot; thêm nhánh `is Float -> editor.putFloat(key, value)` vào `when`. Key vắng lúc snapshot bị xóa lại, vì nó nằm ngoài `values`.
7. Thêm `fun getLensPhysicsBackup(lensId: String): LensPhysicsBackup` (raw read bằng `prefs.contains`, xem Task 3) vào cùng file.

- [ ] **Step 4: Chạy lại test để xác nhận GREEN**

Run: `./gradlew testDevDebugUnitTest --tests '*UtilSettingsPhysicsBackupTest' --tests '*UtilSettingsLensScopeTest'`
Expected: All tests pass.

---

## Task 3: Nối Export/Import Pipeline và Integration Tests

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/util/LayoutBackupSnapshotFactory.kt`
- Modify: `app/src/main/java/com/mckimquyen/util/LayoutImportApplier.kt`
- Modify: `app/src/main/java/com/mckimquyen/util/LayoutBackupIo.kt`
- Create: `app/src/test/java/com/mckimquyen/util/WorkspaceBackupPhysicsRoundTripTest.kt`

**Interfaces:**
- `LayoutBackupSnapshotFactory`: map `physics = settings.physics` vào `LayoutBackupLens`.
- `LayoutImportApplier`: map `physics = lens.physics` vào `WorkspaceLensPlan` và `toAppSettingsBackup()`.
- `LayoutBackupIo.exportAsync`: trích xuất physics từ `prefs` qua `prefs.contains()` cho từng lens.

- [ ] **Step 1: Viết unit test RED cho export snapshot factory & import applier với physics**

Tạo `app/src/test/java/com/mckimquyen/util/WorkspaceBackupPhysicsRoundTripTest.kt` (thêm test Robolectric export raw-read: key vắng ⇒ `physics` rỗng, key có ⇒ giá trị thô không bị getter clamp):

```kotlin
package com.mckimquyen.util

import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.LayoutBackupLens
import com.mckimquyen.model.LensPhysicsBackup
import com.mckimquyen.model.LensWorkspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkspaceBackupPhysicsRoundTripTest {

    @Test
    fun snapshotFactoryTransfersPhysicsToBackupLenses() {
        val lenses = listOf(
            LensWorkspace("default", "Home", 0, 1L),
            LensWorkspace("work", "Work", 1, 2L)
        )
        val defaultPhysics = LensPhysicsBackup(distortionFactor = 1.2f)
        val workPhysics = LensPhysicsBackup(distortionFactor = 3.4f, smartFocusBias = true)

        val appSettings = listOf(
            LensAppSettingsBackup("default", LensAppScope.ALL, emptySet(), false, physics = defaultPhysics),
            LensAppSettingsBackup("work", LensAppScope.ALL, emptySet(), false, physics = workPhysics)
        )

        val backup = LayoutBackupSnapshotFactory.create(
            lenses = lenses,
            rows = emptyList(),
            appSettings = appSettings,
            exportedAt = 1000L
        )

        assertEquals(defaultPhysics, backup.lenses.single { it.id == "default" }.physics)
        assertEquals(workPhysics, backup.lenses.single { it.id == "work" }.physics)
    }

    @Test
    fun importApplierPassesPhysicsThroughToAppSettingsBackup() {
        val physics = LensPhysicsBackup(iconSize = 28.0f)
        val lens = LayoutBackupLens(
            id = "work",
            name = "Work",
            orderIndex = 1,
            scope = LensAppScope.ALL,
            selectedAppIds = emptySet(),
            frozen = false,
            entries = emptyList(),
            physics = physics
        )

        val plan = LayoutImportApplier.planV2(
            com.mckimquyen.model.LayoutBackup(
                schemaVersion = 2,
                exportedAt = 1000L,
                activeLensId = "work",
                lenses = listOf(
                    LayoutBackupLens("default", "Home", 0, LensAppScope.ALL, emptySet(), false, emptyList()),
                    lens
                )
            )
        )

        val workPlan = plan.lenses.single { it.id == "work" }
        assertEquals(physics, workPlan.toAppSettingsBackup().physics)
    }
}
```

- [ ] **Step 2: Nối code trong SnapshotFactory, ImportApplier và LayoutBackupIo**

1. Trong `LayoutBackupSnapshotFactory.kt` (dòng ~38): thêm đối số cuối `physics = settings.physics` (đặt tên) vào `LayoutBackupLens(...)`.
2. Trong `LayoutImportApplier.kt`: thêm `val physics: LensPhysicsBackup? = null` cuối `WorkspaceLensPlan`; `toAppSettingsBackup()` truyền `physics = physics`; trong `planV2` (dòng ~106) thêm `it.physics` làm đối số cuối của `WorkspaceLensPlan(...)`. `planV1` không đổi.
   Lưu ý: `LensGroupPolicy.canonicalizeBackup` dùng `lens.copy(...)` nên giữ `physics`.
3. Trong `LayoutBackupIo.kt`:
   Thêm helper trong `UtilSettings`:
   ```kotlin
   fun getLensPhysicsBackup(lensId: String): LensPhysicsBackup {
       val isDefault = lensId == LensWorkspace.DEFAULT_LENS_ID
       fun rawFloat(baseKey: String): Float? {
           val key = lensKey(baseKey, lensId)
           return if (prefs.contains(key)) prefs.getFloat(key, 0f) else null
       }
       fun rawBoolean(baseKey: String): Boolean? {
           val key = lensKey(baseKey, lensId)
           return if (prefs.contains(key)) prefs.getBoolean(key, false) else null
       }
       return LensPhysicsBackup(
           distortionFactor = rawFloat(KEY_DISTORTION_FACTOR),
           iconSize = rawFloat(KEY_ICON_SIZE),
           customDistortionFactor = rawFloat(KEY_CUSTOM_DISTORTION_FACTOR),
           smartFocusBias = rawBoolean(KEY_SMART_FOCUS_BIAS)
       )
   }
   ```
   Sau đó trong `LayoutBackupIo.exportAsync`, khi khởi tạo `LensAppSettingsBackup` cho từng lens:
   ```kotlin
   LensAppSettingsBackup(
       lens.id,
       settings.getLensAppScope(lens.id),
       settings.getLensAppSelection(lens.id),
       settings.isLensFrozen(lens.id),
       settings.getLensHiddenAppIds(lens.id),
       physics = settings.getLensPhysicsBackup(lens.id)
   )
   ```

- [ ] **Step 3: Chạy toàn bộ unit tests để xác nhận GREEN**

Run: `./gradlew testDevDebugUnitTest`
Expected: 1000+ tests pass, 0 failures.

---

## Task 4: Kiểm thử Thiết bị, Đo đạc và Đóng Story trên Pixel 7 Pro

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/util/WorkspaceBackupPhysicsIntegrationTest.kt`
- Modify: `doc/task/inprogress/p2-db-db-007-backup-v2-include-lens-physics.md` -> move to `done/`
- Create: `doc/task/AUDIT_DB-007_2026-10-23.md`
- Modify: `doc/feature.md`, `doc/task/README.md`

- [ ] **Step 1: Viết test tích hợp xuất/nhập khứ hồi trên AndroidTest**

Tạo `app/src/androidTest/java/com/mckimquyen/util/WorkspaceBackupPhysicsIntegrationTest.kt`:
Kiểm tra export workspace với 2 lens (default và work có override physics khác nhau) -> clear prefs/reset -> import lại backup -> xác nhận các key physics được phục hồi chính xác.

- [ ] **Step 2: Chạy kiểm thử trên Pixel 7 Pro `2B051FDH3006MU`**

```bash
set -e
S=2B051FDH3006MU
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb -s $S install -r "$(find app/build/outputs/apk/dev/debug -maxdepth 1 -name '*.apk' -print -quit)"
adb -s $S install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb -s $S shell am instrument -w -e class com.mckimquyen.util.WorkspaceBackupPhysicsIntegrationTest,com.mckimquyen.ui.FrmSettingsLayoutBackupWidgetTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: All tests pass (OK).

- [ ] **Step 3: Khởi động ActHome và chụp ảnh smoke**

```bash
adb -s $S shell am start -W -n com.mckimquyen.lenslauncher/com.mckimquyen.ui.ActHome
adb -s $S exec-out screencap -p > /tmp/db007-smoke.png
```
Đọc ảnh để đảm bảo không bị che bởi quảng cáo (tuân thủ R4).

- [ ] **Step 4: Lập file Audit `doc/task/AUDIT_DB-007_2026-10-23.md` và chấm điểm**

- [ ] **Step 5: Di chuyển story vào `done/`, cập nhật `doc/feature.md` và `doc/task/README.md`**

- [ ] **Step 6: Kiểm tra bảo mật và commit/push lên `origin/dev`**

```bash
git diff --check
scripts/check-secrets.sh
git add -A -- app/src/main/java/com/mckimquyen/model/LayoutBackup.kt \
              app/src/main/java/com/mckimquyen/util/UtilSettings.kt \
              app/src/main/java/com/mckimquyen/util/LayoutBackupSnapshotFactory.kt \
              app/src/main/java/com/mckimquyen/util/LayoutImportApplier.kt \
              app/src/main/java/com/mckimquyen/util/LayoutBackupIo.kt \
              app/src/test/java/com/mckimquyen/model/LayoutBackupPhysicsTest.kt \
              app/src/test/java/com/mckimquyen/util/UtilSettingsPhysicsBackupTest.kt \
              app/src/test/java/com/mckimquyen/util/WorkspaceBackupPhysicsRoundTripTest.kt \
              app/src/androidTest/java/com/mckimquyen/util/WorkspaceBackupPhysicsIntegrationTest.kt \
              doc/feature.md doc/task/README.md \
              doc/task/done/p2-db-db-007-backup-v2-include-lens-physics.md \
              doc/task/AUDIT_DB-007_2026-10-23.md \
              docs/superpowers/plans/2026-10-23-db-007-backup-lens-physics.md \
              docs/superpowers/specs/2026-10-23-db-007-backup-lens-physics-design.md
git commit -m "feat(backup): export and restore per-lens physics in workspace backup v2 (DB-007)" \
           -m "Adds optional LensPhysicsBackup to backup v2 lenses so distortion factor, icon size, custom distortion, and smart focus bias survive restore. Existing v2 files without physics leave device settings intact. Audit 9.3/10." \
           -m "Co-Authored-By: Claude Code <noreply@anthropic.com>"
git push origin dev
```
