# TEST-005 — Test suite rò trạng thái thiết bị và Robolectric không pin SDK

| Field | Value |
|---|---|
| Type | fix |
| Status | done 2026-10-20 |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Test determinism |
| Estimate | 5 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

`androidTest/.../a11y/AccessibilityFontScaleWidgetTest.kt:43` `settings put system font_scale 2.0` trong `@BeforeClass`, restore ở `@AfterClass` (`:51`); process bị kill giữa chừng để máy ở font 2.0. 7 file instrumented sửa `RAppsSingleton.apps` không có `@After`: `SearchResultCountHeaderWidgetTest`, `AppSearchWidgetTest`, `ActHomeSearchConfigurationWidgetTest`, `AppSearchIntegrationTest`, `AppPersistentMainThreadTest`, `AccessibilityActionsIntegrationTest`, `AppDatabaseMigrationIntegrationTest`. 73 chỗ `edit().clear()`, 82 `Thread.sleep`. 8 file Robolectric (`SettingsAdVipStateTest`, `SettingsDialogCoordinatorTest`, `SettingsIntentHelperTest`, `TestDeviceHelperTest`, `ApertureRevealHelperTest`, `PolaroidExportHelperTest`, `VipPrefsTest`, `VipKeysTest`) không pin `@Config(sdk=[28])` trong khi Robolectric 4.11.1 và `targetSdk 37`. Tiền lệ: `LensManagementActivityWidgetTest` từng làm rò ngôn ngữ thiết bị.

## User story

Là dev, tôi muốn chạy test không làm hỏng máy test và kết quả không phụ thuộc phiên bản SDK cài trên máy.

## Acceptance criteria

- [x] Một `TestRule` dùng chung snapshot/restore `RAppsSingleton`, prefs, `animator_duration_scale`, `font_scale` trong `finally`.
- [x] Test font scale dùng configuration context thay vì ghi cài đặt hệ thống, hoặc restore đảm bảo.
- [x] 8 file Robolectric pin `@Config(sdk=[28])`.
- [x] Chạy full suite hai lần liên tiếp, state thiết bị trước/sau không đổi (diff prefs + settings).

## Kết quả (2026-10-20)

`DeviceStateGuard` áp cho 148/148 lớp androidTest, 8 test Robolectric pin sdk 28 + `RobolectricSdkPinTest`. Pixel 7 Pro, full suite hai lần: 148/148 OK, 0 diff thật ở pass 2. Giới hạn và điểm 9.1/10: `AUDIT_TEST-005_2026-10-20.md`. Lớp test widget/integration: bản thân rule chạy trong mọi test widget/integration; UI không đổi nên không có widget test mới.

## Implementation notes

Chính là gốc flake đã mất 6 vòng ở `AppSearchIntegrationTest` và TEST-004. Làm theo từng lô nhỏ.

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget/UI tests cover visible behavior, accessibility and lifecycle where applicable.
- [x] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [x] Smoke tests pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [x] No new lint/build failures.
- [x] A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story TEST-005 (Test suite rò trạng thái thiết bị và Robolectric không pin SDK) trong doc/task/todo/p3-test-test-005-test-state-leak-and-flaky-guards.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_TEST-005_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
