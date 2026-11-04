# FISH-036 Optical Profile File Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xuất/nhập bộ quang học của lens trong trang cài đặt qua JSON riêng và SAF, với preview và ghi prefs một batch.

**Architecture:** Payload/parser độc lập với workspace backup. IO suspend giới hạn byte; `UtilSettings` capture không ghi và apply batch có rollback. `LensSettingsActivity` sở hữu target, SAF result, preview và lifecycle; không thay Room layout hoặc active lens.

**Tech Stack:** Kotlin, Android SAF ActivityResult API, `org.json` hiện có + `android.util.JsonReader` strict preflight, SharedPreferences, Room read-only existence check, lifecycleScope, JUnit4/Robolectric, AndroidJUnitRunner.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-11-04-fish-036-lens-profile-file-design.md`. Đọc và duyệt bản spec trước execution; chưa viết code trong giai đoạn plan.
- Pixel only. Trước build/test/device work, `adb devices -l`; chỉ dùng Pixel khóa `2B051FDH3006MU` nếu còn attached. Không gửi bất kỳ lệnh nào tới TECNO/Samsung/OPPO; không chạy Gradle `connected*` không target.
- JSON riêng: `format="lens-optical-profile"`, `version=1`, MIME `application/json`, tên file `lens_optical_profile.json`, giới hạn `16 * 1024` byte UTF-8.
- Bắt buộc đủ optical profile/distortion/scale/icon size/animation/hood/Smart Focus. Custom vắng giữ nguyên preset máy nhận; có thì đủ distortion/scale/time.
- Không clamp/partial import; reject unknown/duplicate/missing key, trailing data, wrong type, numeric strings, non-finite/out-of-range, malformed UTF-8.
- Distortion/custom distortion `[0.5,5.0]`; icon `[10,55]`; scale/custom scale `[1.0,3.5]`; animation/custom time `[100,400]` ms nguyên; hood `[0.2,1.0]`. Dùng constants hiện có, không số nghiệp vụ vô danh.
- Giữ scope keys cũ; default lens inheritance phải được cảnh báo. Không thêm key settings, dependency, schema hoặc Activity; không đổi workspace backup.
- Target cố định `EXTRA_LENS_ID`, không lấy mutable active lens khi result về. Cancel/parse/preview không ghi.
- Batch commit trên IO; thất bại rollback touched keys; rollback lỗi không báo success. Xóa target pending distortion trong batch thành công.
- Không Ads/IAP/TalkBack scope mới. Giữ accessibility control chuẩn và 17 locale strings.
- No push; local commits theo từng bước sau duyệt execution. Không stage `.idea/`.
- Full gate: `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest --rerun-tasks`, `git diff --check`, `bash scripts/check-secrets.sh`.

---

## Files và API đã kiểm

| File | Vai trò / lưu ý |
|---|---|
| `app/src/main/java/com/mckimquyen/ui/LensSettingsActivity.kt` | Đã tồn tại, `targetId`, `ready`, `busy`, `dialog`, `loadTarget`, `bind`, `setEnabled`, saved dialog state. Tích hợp tại đây, không `FrmLens`/`ActSettings`. |
| `app/src/main/res/layout/act_lens_settings.xml` | Scroll nội dung, controls profile/hood/SmartFocus hiện có; thêm hai button cuối nhóm quang học. |
| `app/src/main/java/com/mckimquyen/util/UtilSettings.kt` | `prefs`, `lensKey`, `snapshotKeys(Set<String>): ResetSnapshot`, `restoreKeys(ResetSnapshot): Boolean`, `autoDefaultIconSize`, `hasCustomPreset`, `getPendingDistortionFactor`. Existing capture getters có thể clamp và ghi: không dùng chúng trong preview/capture không ghi. |
| `app/src/main/java/com/mckimquyen/model/LensWorkspaceDao.kt` | `findById` suspend. Chỉ đọc tồn tại; không gọi replaceWorkspace để dọn test. |
| `app/src/main/java/com/mckimquyen/services/AppEventManager.kt` | `notifyLensScopeChanged(data: Any? = null)` giúp Home re-filter/rebind; kiểm Home lifecycle refresh trước chọn event. |
| `app/src/main/java/com/mckimquyen/util/LayoutBackupIo.kt` | Mẫu SAF stream `use`/IO, không tái dùng unbounded readText hoặc ApplicationScope giữ Activity callback. |
| `app/src/main/java/com/mckimquyen/util/LensHood.kt` | MIN .2, FULL 1, STEP .05; file giữ exact Float, bind slider mới snap. |
| `app/src/androidTest/java/com/mckimquyen/testing/DeviceStateGuard.kt` | Bảo vệ prefs/system/singleton, không snapshot Room; tự dọn test lens theo ID riêng. |
| `app/src/androidTest/java/com/mckimquyen/ui/LensSettingsActivityWidgetTest.kt` | Existing tests seed `ui033-widget` và chưa dọn hết Room lens; không chạy whole class trên Pixel trước khi xác minh fixture. Tạo class FISH-036 riêng, không copy cleanup nguy hiểm. |
| `app/src/test/java/com/mckimquyen/a11y/AllStringsTranslationTest.kt` | Kiểm strings toàn bộ locale; dùng regression, không mở TalkBack scope. |

### File map thay đổi tối thiểu

- Thêm `app/src/main/java/com/mckimquyen/model/LensOpticalProfile.kt` và test `app/src/test/java/com/mckimquyen/model/LensOpticalProfileTest.kt`.
- Thêm `app/src/main/java/com/mckimquyen/util/LensOpticalProfileIo.kt` và test `app/src/test/java/com/mckimquyen/util/LensOpticalProfileIoTest.kt`.
- Sửa `app/src/main/java/com/mckimquyen/util/UtilSettings.kt`; thêm `app/src/test/java/com/mckimquyen/util/UtilSettingsOpticalProfileTest.kt`.
- Sửa `app/src/main/java/com/mckimquyen/ui/LensSettingsActivity.kt` + `app/src/main/res/layout/act_lens_settings.xml` và `app/src/main/res/values*/strings.xml` đúng 17 locale có chuỗi hiện hành.
- Thêm `app/src/androidTest/java/com/mckimquyen/ui/LensOpticalProfileWidgetTest.kt`, `app/src/androidTest/java/com/mckimquyen/util/LensOpticalProfileIntegrationTest.kt`.
- Nếu SAF provider integration cần test ContentProvider riêng: thêm `app/src/androidTest/AndroidManifest.xml`, `app/src/androidTest/java/com/mckimquyen/testing/OpticalProfileTestProvider.kt`; chỉ test APK, exported=false, phục vụ byte fixture đọc/ghi/lỗi.
- Không thay constructor public `UtilSettings`, không thêm injected framework/interface. Test failed commit bằng Mockito Context/SharedPreferences hoặc reflection seam tương tự existing tests.

## Interfaces cố định dùng xuyên task

```kotlin
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
    fun isValid(): Boolean
    fun toJson(): String
    companion object {
        const val FORMAT = "lens-optical-profile"
        const val VERSION = 1
        const val MAX_BYTES = 16 * 1024
        const val MIME_TYPE = "application/json"
        const val FILE_NAME = "lens_optical_profile.json"
        fun parse(json: String): OpticalProfileParseResult
    }
}
sealed interface OpticalProfileParseResult {
    data class Success(val value: LensOpticalProfile) : OpticalProfileParseResult
    data object Malformed : OpticalProfileParseResult
    data object UnsupportedVersion : OpticalProfileParseResult
}

sealed interface OpticalProfileReadResult {
    data class Success(val value: LensOpticalProfile) : OpticalProfileReadResult
    data object Malformed : OpticalProfileReadResult
    data object UnsupportedVersion : OpticalProfileReadResult
    data object TooLarge : OpticalProfileReadResult
    data object IoFailure : OpticalProfileReadResult
}
object LensOpticalProfileIo {
    suspend fun read(resolver: ContentResolver, uri: Uri): OpticalProfileReadResult
    suspend fun write(resolver: ContentResolver, uri: Uri, value: LensOpticalProfile): Boolean
    internal fun readStream(stream: InputStream): OpticalProfileReadResult
}

enum class OpticalProfileApplyResult { APPLIED, FAILED_RESTORED, RESTORE_FAILED }
// Trong UtilSettings:
fun captureOpticalProfile(lensId: String): LensOpticalProfile?
fun opticalProfileKeys(lensId: String, includesCustom: Boolean): Set<String>
fun applyOpticalProfile(lensId: String, profile: LensOpticalProfile): OpticalProfileApplyResult
```

`applyOpticalProfile` kiểm payload và nonblank lens ID; Room existence kiểm ở caller trong lifecycleScope trước/sau apply. Payload API không tự chọn target. Snapshot lấy ngay trước commit; `restoreKeys` phục hồi đúng key vắng/kiểu nguyên bản. Cho phép false result khi validation fail nhưng không chạy rollback vô nghĩa nếu chưa ghi; dùng `FAILED_RESTORED` với state không đổi và kiểm test rõ.

---

## Task 1 — Strict format và round-trip (TDD)

**Files:** model và model unit test nêu trên. **Produces:** `LensOpticalProfile`, `OpticalCustomPreset`, `OpticalProfileParseResult`.

- [ ] **1. RED: thêm fixture và assertions ở test, chưa model.** Robolectric sdk 28, manifest NONE vì parser dùng Android JsonReader/org.json; không bật includeAndroidResources toàn dự án.

```kotlin
private fun sample(custom: OpticalCustomPreset? = null) = LensOpticalProfile(
    LensProfile.ANAMORPHIC, 3.3f, 1.3f, 22f, 250L, 0.45f, true, custom
)
@Test fun roundTripWithCustom() {
    val value = sample(OpticalCustomPreset(4.1f, 1.6f, 300L))
    assertEquals(OpticalProfileParseResult.Success(value), LensOpticalProfile.parse(value.toJson()))
}
@Test fun numericStringDoesNotCoerce() {
    val json = sample().toJson().replace("\"animationTimeMs\":250", "\"animationTimeMs\":\"250\"")
    assertEquals(OpticalProfileParseResult.Malformed, LensOpticalProfile.parse(json))
}
@Test fun absentCustomStaysAbsent() {
    val value = sample()
    assertFalse(JSONObject(value.toJson()).has("custom"))
    assertEquals(OpticalProfileParseResult.Success(value), LensOpticalProfile.parse(value.toJson()))
}
```

- [ ] **2. Chạy RED:** `./gradlew testDevDebugUnitTest --tests '*LensOpticalProfileTest'`; thiếu model/API là lỗi dự kiến đầu tiên.
- [ ] **3. Thêm model signatures và serializer.** `toJson` require `isValid`; root/optical/custom keys chính xác spec. Serialize Float qua `.toDouble()` để round-trip đọc lại Float.

```kotlin
JSONObject().apply {
    put("format", FORMAT)
    put("version", VERSION)
    put("optical", JSONObject().apply {
        put("profile", profile.name)
        put("distortionFactor", distortionFactor.toDouble())
        put("scaleFactor", scaleFactor.toDouble())
        put("iconSize", iconSize.toDouble())
        put("animationTimeMs", animationTimeMs)
        put("hood", hood.toDouble())
        put("smartFocusBias", smartFocusBias)
    })
    custom?.let { saved ->
        put("custom", JSONObject().apply {
            put("distortionFactor", saved.distortionFactor.toDouble())
            put("scaleFactor", saved.scaleFactor.toDouble())
            put("animationTimeMs", saved.animationTimeMs)
        })
    }
}.toString()
```

Dùng named constants cho tất cả key strings, format, version, range. `isValid` kiểm finite/range mọi field, custom nếu có; không ép hood vào STEP.

- [ ] **4. Parser minimal nhưng strict:** chạy `JsonReader(StringReader(json))` với `isLenient=false`. Preflight root/object chỉ primitive hoặc optical/custom object; mỗi object có `HashSet<String>` reject duplicate/unknown; từ chối array/nested object trong optical/custom, null, numeric string, wrong token. Numeric token đọc lexical bằng `nextString()` sau `peek()==NUMBER`, đối chiếu JSON number grammar; integer field dùng `BigDecimal(...).longValueExact()` để tránh overflow/truncate. Check END_DOCUMENT sau endObject. Sau preflight, `JSONObject` map sang fields và `isValid`. UnsupportedVersion chỉ trả cho integer version khác 1 sau khi header hợp lệ; không crash. Catch parsing/number exceptions, không catch cancellation/Error chung.
- [ ] **5. Mở rộng từng test RED/GREEN:** mọi required field bị remove; unknown root/optical/custom; duplicate từng object; suffix garbage; version sai type/fractional/negative/new; all four enums; custom null/incomplete; wrong Boolean/profile; NaN/Infinity/exponents overflow; numeric boundaries ngay dưới/trên range; Float rounding không làm hợp lệ số Double ngoài range. Parser test phải assert input thật đã đổi, tránh replace không trúng.
- [ ] **6. GREEN:** `./gradlew testDevDebugUnitTest --tests '*LensOpticalProfileTest' --rerun-tasks`; 0 fail. Commit model/tests: `feat(profile): add strict optical profile JSON format (FISH-036)`.

## Task 2 — Bounded UTF-8 SAF IO (TDD)

**Files:** IO object và IO tests. **Consumes:** model/parser, MAX_BYTES. **Produces:** read/write APIs cố định.

- [ ] **1. RED:** test bounded read/UTF8 và stream closure trước khi code.

```kotlin
@Test fun rejectsOversizedInputBeforeParse() {
    val bytes = ByteArray(LensOpticalProfile.MAX_BYTES + 1) { ' '.code.toByte() }
    assertEquals(OpticalProfileReadResult.TooLarge,
        LensOpticalProfileIo.readStream(ByteArrayInputStream(bytes)))
}
@Test fun rejectsMalformedUtf8() {
    assertEquals(OpticalProfileReadResult.Malformed,
        LensOpticalProfileIo.readStream(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28))))
}
```

Thêm stream recording số byte đã đọc, exact-limit whitespace-padded valid JSON, limit+1, IOException giữa read, valid round-trip, unsupported version. `readStream` không tự close stream caller sở hữu; `read(resolver, uri)` phải `.use` close mọi nhánh.
- [ ] **2. Verify RED:** `./gradlew testDevDebugUnitTest --tests '*LensOpticalProfileIoTest'`.
- [ ] **3. IO implementation:** bounded `ByteArrayOutputStream`, buffer cap named; không `readText/readBytes` unbounded. Chỉ đọc nhiều nhất MAX_BYTES+1. UTF8 decoder dùng REPORT cho malformed/unmappable. Map parser result tới read result. Suspend wrapper dùng `withContext(Dispatchers.IO)` và `ensureActive` trước/sau đọc; luôn rethrow CancellationException.

```kotlin
suspend fun read(resolver: ContentResolver, uri: Uri): OpticalProfileReadResult =
    withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(uri)?.use(::readStream) ?: OpticalProfileReadResult.IoFailure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Logger.e("LensOpticalProfileIo: read failed", error)
            OpticalProfileReadResult.IoFailure
        }
    }
```

Write require value valid, serialize/encode ≤ MAX_BYTES, `openOutputStream(uri,"wt")?.use { it.write(bytes) }`, Boolean result. Do not delete provider document on failure (may be user-owned overwrite); report partial file possibility. Catch error + close failure as false.
- [ ] **4. Verify GREEN:** targeted IO + parser tests. ContentResolver mock checks null/error/closed stream; unsupported versions preserved, cancellation not mapped to IoFailure. Commit: `feat(profile): add bounded SAF profile IO (FISH-036)`.

## Task 3 — Non-writing capture và one-batch settings application (TDD)

**Files:** `UtilSettings.kt`, new settings tests. **Consumes:** typed payload; `snapshotKeys`/`restoreKeys`. **Produces:** capture, touched keys, apply result.

- [ ] **1. RED:** capture does not mutate raw prefs, effective inheritance, target/global keys and omitted Custom.

```kotlin
@Test fun captureIsReadOnlyAndResolvesInheritance() {
    rawPrefs.edit().putFloat(UtilSettings.KEY_DISTORTION_FACTOR, 4.1f).commit()
    val before = rawPrefs.all.toMap()
    val value = requireNotNull(settings.captureOpticalProfile("work"))
    assertEquals(4.1f, value.distortionFactor, 0f)
    assertEquals(before, rawPrefs.all)
}
@Test fun applyPreservesUnrelatedAndAbsentCustom() {
    settings.saveDistortionFactor("other", 4.4f)
    settings.saveCustomScaleFactor(1.6f)
    settings.savePendingDistortionFactor("work", 4.8f)
    assertEquals(OpticalProfileApplyResult.APPLIED, settings.applyOpticalProfile("work", sample()))
    assertEquals(3.3f, settings.getDistortionFactor("work"), 0f)
    assertEquals(1.3f, settings.getFloat(UtilSettings.KEY_SCALE_FACTOR), 0f)
    assertEquals(4.4f, settings.getDistortionFactor("other"), 0f)
    assertEquals(1.6f, settings.getCustomScaleFactor(), 0f)
    assertNull(settings.getPendingDistortionFactor("work"))
}
```

- [ ] **2. Verify RED:** `./gradlew testDevDebugUnitTest --tests '*UtilSettingsOpticalProfileTest'`.
- [ ] **3. Capture raw `prefs.all` once:** resolver accepts exactly Float for float keys, Long for duration, Boolean for bias and String known enum. Missing keys resolve current default/inheritance without calling mutating getters. Wrong type/nonfinite/out-of-range => null, no repair. Default icon `autoDefaultIconSize`. profile/hood do not inherit; custom only if global sentinel exists, custom distortion same fallback chain lens→global custom→effective distortion. `custom` absent leaves all custom out of touched-key set.
- [ ] **4. Implement touched-key set:** lensKey for distortion/icon/profile/hood/bias + pending; global scale/time; only if Custom included add lens custom distortion + global custom scale/time. No layout/scope/frozen/active/global unrelated key.
- [ ] **5. Apply one editor:** validate ID/payload before snapshot; editor `.putFloat/.putLong/.putBoolean/.putString` all fields, `.remove(pendingKey)`, optional custom fields; commit once. If false/exception, `restoreKeys(snapshot)` once; distinguish FAILED_RESTORED and RESTORE_FAILED. Main caller runs IO; do not add asynchronous nested scope. Add `@Suppress("UseKtx")` comment because Boolean commit is required. Do not change all existing save methods.
- [ ] **6. RED/GREEN failure cases:** Mockito editor first commit false/throws, second restore true/false; `verify(editor, times(1)).commit()` for success; exact key set; default lens inheritance; secondary lens leaves shared distortion unchanged; custom present updates sentinel and effective values; blank target rejects without editor; invalid typed value no writes. Restore absent keys absent, no loss of unrelated keys. Compare raw maps, not getters that can mutate.
- [ ] **7. GREEN regression:** `./gradlew testDevDebugUnitTest --tests '*UtilSettingsOpticalProfileTest' --tests '*UtilSettingsPerLensTest' --tests '*UtilSettingsCustomPresetTest' --tests '*UtilSettingsPhysicsBackupTest' --tests '*UtilSettingsResetSnapshotTest'`; commit `feat(profile): apply optical settings as one preference batch (FISH-036)`.

## Task 4 — SAF buttons, preview và lifecycle (TDD)

**Files:** Activity/layout/17 locale strings + `LensOpticalProfileWidgetTest.kt`. **Consumes:** all APIs above. **Produces:** two working entry points and safe preview/confirm.

- [ ] **1. Safe test fixture first:** `DeviceStateGuard`; seed unique lens ID `f036-<UUID>` only after capturing ID-specific absence. Never replaceWorkspace or clear prefs. In finally close ActivityScenario, cancel refresh work if fixture seeded fake apps, delete rows only for generated test ID and delete that lens via DAO; verify original lenses/rows remain. If test needs default lens, prefs guard only, do not seed fake app rows. Add tests before controls exist.

```kotlin
@Test fun buttonsExistAndBindDoesNotWrite() {
    val before = prefs.all.toMap()
    launch(targetId).use { scenario ->
        awaitReady(scenario)
        scenario.onActivity { activity ->
            assertNotNull(activity.findViewById<View>(R.id.btLensSettingsExportProfile))
            assertNotNull(activity.findViewById<View>(R.id.btLensSettingsImportProfile))
        }
    }
    assertEquals(before, prefs.all)
}
```

`awaitReady` poll existing slider enabled, 8s named timeout/50ms poll; no arbitrary sleep replacing asynchronous completion. Init `UtilSettings` and locale migration before before-map, to avoid testing unrelated first-launch initialization.
- [ ] **2. Verify RED build:** `./gradlew assembleDevDebugAndroidTest`; missing IDs expected. Before device execution verify attached Pixel and backup per Task 6.
- [ ] **3. Controls:** after SmartFocus and before Apps group add outlined full-width MaterialButtons `btLensSettingsExportProfile` and `btLensSettingsImportProfile`, minHeight 48dp, wrap_content text. Add both to `setEnabled` list. No new icon needed.
- [ ] **4. Strings:** define named keys `lens_optical_export`, `_import`, `_preview_title`, `_target_group`, `_global_group`, `_custom_group`, `_change_format`, `_global_warning`, `_inheritance_warning`, `_custom_unchanged`, `_export_success`, `_export_failed`, `_invalid`, `_unsupported`, `_too_large`, `_read_failed`, `_target_missing`, `_import_success`, `_import_failed`, `_restore_failed`. Use existing labels for field names/enums and yes/no resources; preserve placeholder signatures in all locale folders with strings.xml. `_change_format` format `%1$s: %2$s → %3$s`; warnings explain scope, no hardcoded UI English.
- [ ] **5. Register ActivityResult launchers as fields:** `CreateDocument(MIME_TYPE)` and `OpenDocument()` before STARTED. Click requires `ready && !busy`; capture snapshot for export, save `pendingExportJson` to Bundle before launching file name. Import captures existing targetId and launches `arrayOf(MIME_TYPE)`; never read active ID. Null URI resets pending operation without pref writes. No broad MIME or external intent receiving feature.
- [ ] **6. IO callback:** lifecycleScope launch read/write, `busy` disables controls, rethrow cancellation; no Activity captured by ApplicationScope. onResume `loadTarget()` must not show stale pending app/unfreeze dialogs over profile preview; add distinct dialog kind and restore bounded parsed JSON only after target ready. Preserve JSON in Bundle, clear pending apply before executing explicit confirmation so recreate never repeats it.
- [ ] **7. Preview:** implement `private fun showOpticalProfilePreview(value: LensOpticalProfile)` with `MaterialAlertDialogBuilder`, scrollable message showing every current→incoming value grouped by target/global/custom. Use capture without writes for current values. If Custom absent explicit unchanged; if default target include inheritance warning. Negative button/Back/dismiss drops draft; no commit. Recreate captures draft before onDestroy dismiss callback and re-parses it on create.
- [ ] **8. Confirm:** set busy before launching; snapshot keys, Room `findById(id)` IO check; if gone report target missing and finish without applying. In `withContext(NonCancellable + Dispatchers.IO)` once confirmation starts, perform batch apply, post-existence check and rollback as needed using application context/settings only. NonCancellable bounded to local storage section, never provider read. If deleted, restore global keys but remove lens-specific keys for deleted target (do not resurrect it); default lens cannot be deleted by production guard. No Room writes. Rebind only if Activity still live. Post `notifyLensScopeChanged(id)` on Main only after success; verify Home's handler reloads all bound pages (global fields matter), or rely on existing onResume reload and document/test exact behavior. Do not switch lens.
- [ ] **9. Widget tests through result delivery:** use Instrumentation `ActivityMonitor` for SAF intents to capture request/result and deliver selected Uri back through production ActivityResult path; preserve intent request ID across recreate. For assertions on dialog, use existing internal `dialogForTest`; add narrow result seam only if platform monitoring cannot deliver, never test a duplicate nonproduction apply method.
- [ ] **10. RED/GREEN matrix:** controls launch correct SAF MIME/name; picker cancel leaves raw prefs equal; preview includes all values/global/default warnings; Cancel/Back no writes; confirmation target non-active keeps active ID and other lens keys; double tap exactly one commit; deleted target no write; recreate with picker/preview no auto apply; destroy no leaked dialog; malformed file shows error no preview/write. `./gradlew assembleDevDebugAndroidTest` then run just new widget class on Pixel, with result text parsed for failure (adb exit 0 alone not pass).
- [ ] **11. Host regression:** `./gradlew testDevDebugUnitTest --tests '*AllStringsTranslationTest' --tests '*LensOpticalProfile*' --tests '*UtilSettingsOpticalProfileTest'`; run widget class after safe backup; commit `feat(profile): add SAF import preview in lens settings (FISH-036)`.

## Task 5 — Real URI and Home integration (TDD)

**Files:** `LensOpticalProfileIntegrationTest.kt`; optional test-only provider/manifest.

- [ ] **1. RED tests before integration fixes:** all four enums and optional Custom roundtrip through actual ContentResolver stream; null/throwing streams; exact byte ceiling, malformed UTF8; close exceptions; no Room changes; import target deleted between picker and confirm.
- [ ] **2. Provider fixture:** internal test APK provider handles only explicitly generated content URIs; serve input via `ParcelFileDescriptor` pipe from app-private test fixture files; output descriptor to generated temp file. All producer threads close descriptors; remove fixtures in finally. No network, no exported provider, no raw user-file access. Declare exact authority `${applicationId}.opticalprofile.test` in androidTest manifest after confirming test APK applicationId expansion. Verify grant/read permission across target/test UID or install provider in test context and invoke resolver via instrumentation context; do not broaden provider export to solve permission failures.
- [ ] **3. Home behavior:** target generated lens + existing default baseline; import profile through production screen, exit to Home, select generated lens; assert persisted values and actual `LensView` reads target distortion/profile/hood. Recreate/relaunch and verify imported values persist, default unaffected except allowed global scale/time. Do not assert only fixture copies. Preserve app snapshot and close all scenarios before cleanup.
- [ ] **4. Cancellation/error:** I/O failure and malformed result no prefs diff; failure result isn't success toast; canceled preview survives recreate only as draft, never commit. Commit failure and failed rollback unit seams from Task 3 cover disk-failure behavior not injectable on real Pixel.
- [ ] **5. Device command:** `adb -s 2B051FDH3006MU shell am instrument -w -e class com.mckimquyen.util.LensOpticalProfileIntegrationTest com.mckimquyen.lenslauncher.test/androidx.test.runner.AndroidJUnitRunner`; capture log and require `OK (N tests)` with expected count, no failures/crash/zero-tests. Run widget class separately same runner.
- [ ] **6. GREEN and commit:** `test(profile): verify SAF URI and Home import behavior (FISH-036)`. No test-produced files/DB fixtures left behind.

## Task 6 — Mutation, final gate, Pixel SAF smoke, audit

**Files:** story/tracker/README and `doc/task/AUDIT_FISH-036_2026-11-04.md` (use actual finish date if later).

- [ ] **1. Host gate:** run exact full command in constraints; preserve log, count XML test totals with failures/errors/skips, parse lint counts, hash app/test APK. No concurrent source mutations during builds.
- [ ] **2. Mutation one at a time:** allow wrong format/version; remove range/finite check; read beyond MAX_BYTES; accept numeric strings/duplicate key; write source or active lens instead of target; remove preview confirm guard; always overwrite Custom when absent; remove pending-clear; write global scale to suffixed key; skip failed-commit rollback. Each targeted test must fail for assertion not compile error; revert exact mutation immediately. Then full gate on restored source. A surviving mutant is a test gap, not pass.
- [ ] **3. Lock/check Pixel:** `adb devices -l`; if Pixel absent stop. Never call device MCP tools with implicit target: previous tool returned TECNO screen despite Pixel adb lock. Every shell/screenshot/install uses `adb -s 2B051FDH3006MU`. Capture screenshots serially then Read, never parallel Read before file write completes.
- [ ] **4. Backup before install/test:** force-stop target package only; `run-as` tar app data excluding lib symlink; pull installed main/test APK using `pm path`; record every regular-file SHA-256 from tar, Room dump with WAL applied, prefs raw map, system locale/app locale/font/animation/HOME/permissions/foreground. Save under unique `/tmp/f036-pixel-<timestamp>/`, validate archive completeness/readability. Never rm/overwrite an existing backup folder. If package not debuggable and data cannot back up, stop before tests; no fallback destructive reset.
- [ ] **5. Install current APKs:** locate paths from output-metadata, not guessed filenames; `adb -s ... install -r <host-apk>` (not `adb shell pm install <host-path>`). Run new safe widget/integration classes separately. Do not run unrelated classes with destructive fixture unless separately audited. Force-stop and compare baseline immediately after each class. Any unexpected rows/files require restoring proven backup before continuing, no ad hoc SQL deletion without owner approval.
- [ ] **6. SAF smoke:** from manager open settings for a generated test lens; export valid JSON via system document picker to test-created document; pull/read actual bytes, verify fields and no app IDs/layout. Change test lens values, import file, inspect preview global/default warnings, Cancel check unchanged; re-import Confirm, Home correct, app relaunch persists; corrupt copy rejects and no pref/Room diff. Exercise Custom omitted and target non-active. Stop on ad/consent screenshot until owner `done`; don't bypass.
- [ ] **7. Restore:** close Activity, force-stop app, restore original APKs/data/system/foreground with exact serial. Compare each regular-file hash plus Room rows and prefs; do not substitute `replaceWorkspace` for physical backup restore (it reassigns IDs/preserves fixture stats). Any allowed difference must be explicit, explained and owner-approved; otherwise remain inprogress.
- [ ] **8. Audit:** list real test counts/fail/skip, mutation outcomes, exact source/APK SHA, device/time/screenshots/logs, lifecycle/resource review, remaining limits. Score honestly >9.0 only when all required gates true; no fabricated “atomic Room+prefs” guarantee.
- [ ] **9. Status/commit:** update `doc/feature.md` immediately on decisions and story todo→inprogress only after execution approval. Move to done after audit gate; README links accurate. Commit relevant paths only, ending with `Co-Authored-By: Claude Code <noreply@anthropic.com>`. No `.idea/`, no push.

## Plan self-review

- [x] Payload and validation Task 1; byte/encoding/SAF boundary Task 2; no-write capture/batch/rollback Task 3; preview/lifecycle/UI/17 locales Task 4; URI/Home boundaries Task 5; mutation/backup/restore/audit Task 6.
- [x] API signatures and class/package names shared consistently; source targets current LensSettingsActivity, not stale agent checkout.
- [x] Custom absent, default inheritance, target not active/deleted, pending pinch, provider failures and process/recreate behavior addressed.
- [x] No new schema/dependency/global-scope change; no writes through workspace replacement or ApplicationScope Activity callback.
- [x] Device target explicitly bound; no implicit MCP screenshot tool; backup before any test.
- [x] No implementation performed by this plan. Owner execution choice still required.
