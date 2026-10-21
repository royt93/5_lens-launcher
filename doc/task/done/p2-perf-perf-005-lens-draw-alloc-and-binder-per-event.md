# PERF-005 — LensView cấp phát Rect mỗi icon mỗi frame và gọi binder mỗi MotionEvent

| Field | Value |
|---|---|
| Type | fix |
| Status | done 2026-10-21 |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Lens rendering |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

`views/LensView.kt:1472` `Rect(0,0,appIcon.width,appIcon.height)` trong `drawAppIcon` (300 app = 300 Rect/frame); `:1268` `drawWorkspaceBackground` tạo `Rect` mỗi frame cho `mWorkspaceBackgroundDrawable` chưa từng được gán (chỉ khai báo `:482`, null hóa `:1765`). `:1027` `onTouchEvent` gọi `LensPhysicsPolicy.shouldReduceLensMotion(context)` mỗi MotionEvent: `Settings.Global.getFloat` + `isPowerSaveMode` + `currentThermalStatus` (3 IPC), ~100-200 event/s khi kéo. PERF-001 tuyên bố 0 allocation/frame nhưng bỏ sót hai chỗ này.

## User story

Là người dùng máy yếu, tôi muốn kéo lens mượt, không giật do GC hay IPC.

## Acceptance criteria

- [x] Rect nguồn dùng một scratch field tái sử dụng.
- [x] Field/hàm nền workspace chết bị xóa.
- [x] `shouldReduceLensMotion` lấy mẫu một lần ở ACTION_DOWN, giữ tới UP/CANCEL.
- [x] Test hành vi không đổi (geometry, gate reduced-motion).
- [x] Đo `gfxinfo` trước/sau trên thiết bị khóa.

## Kết quả và giới hạn (2026-10-21)

Hoàn tất theo audit `doc/task/AUDIT_PERF-005_2026-10-21.md` (9.1/10). Full unit 972/972, lint 0 lỗi/7 warning, build APK app + androidTest xanh; Pixel 7 Pro `2B051FDH3006MU`: 65 test máy pass. `gfxinfo` cùng harness 300 icon: baseline 464 frame / 2 jank, candidate cuối 465 / 0; percentile chồng lấp, không kết luận giảm jank. Proven: loại Rect per-icon và giảm policy reads theo MOVE. Settings máy khôi phục; 3 key AdMob/AppLovin tự đổi, ghi trong audit.

## Implementation notes

Gộp F1, F2, F3 của audit lens. Không đổi geometry nên `LensGridCache` bất biến.

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
Làm story PERF-005 (LensView cấp phát Rect mỗi icon mỗi frame và gọi binder mỗi MotionEvent) trong doc/task/todo/p2-perf-perf-005-lens-draw-alloc-and-binder-per-event.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_PERF-005_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
