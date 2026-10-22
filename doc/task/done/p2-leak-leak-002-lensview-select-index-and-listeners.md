# LEAK-002 — LensView: IndexOutOfBounds ở launchApp và 3 listener không null hóa khi detach

| Field | Value |
|---|---|
| Type | fix |
| Status | done 2026-10-22 |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Stability |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

`views/LensView.kt:1650-1657` `launchApp()` kiểm `mSelectIndex >= 0` nhưng không chặn biên trên; `list[mSelectIndex]` (cả `drawAppName:1545`). `mSelectIndex` được giữ qua frame khi `mAnimationHiding` (`:1432`) và `launchApp()` chạy từ `onAnimationEnd` (`:1691`); nếu `setApps()` thu nhỏ list giữa chừng (gỡ app) thì crash. `launchAppAtIndex` (`:633`) đã dùng `in list.indices`. Khối detach (`:1759-1761`) null `onHapticPerformed`, `onEdgeSwipeListener`, `vibratorProvider` nhưng bỏ `onCurvatureAdjustedListener`, `onEmptySpaceLongPressListener`, `onSearchSwipeDownListener` (`ActHome.java:717,726,733` gán bằng lambda bắt Activity). Guard `shouldAllowAppLaunch` (`:1237`) luôn true vì `gestureState` vừa bị gán IDLE ở `:1226`.

## User story

Là người dùng, tôi muốn app không crash khi gỡ app đúng lúc đang mở, và không rò Activity.

## Acceptance criteria

- [x] `launchApp` và `drawAppName` dùng `getOrNull`/kiểm biên.
- [x] Test: thu nhỏ danh sách trong lúc animation hide không crash.
- [x] Cả 6 listener được null hóa khi detach (test reflection hoặc kiểm tra field).
- [x] Guard `shouldAllowAppLaunch` kiểm trước khi reset state hoặc bị xóa kèm giải thích.

## Kết quả (2026-10-22)

Bounds check `getOrNull` và cleanup đủ sáu callback đã xong. `shouldAllowAppLaunch` đã xóa vì pinch return trước normal ACTION_UP. Baseline RED/candidate GREEN, 971 unit pass, lint 0 lỗi/7 warning, S24 targeted 42/42 pass, build và smoke pass. Audit/giới hạn: `doc/task/AUDIT_LEAK-002_2026-10-22.md` (9.1/10). Test stale index không điều khiển thành công hide animation thật trên S24; các regression trực tiếp chứng minh crash site, ghi rõ giới hạn trong audit.

## Implementation notes

Gộp F4, F5, F6 của audit lens.

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
Làm story LEAK-002 (LensView: IndexOutOfBounds ở launchApp và 3 listener không null hóa khi detach) trong doc/task/todo/p2-leak-leak-002-lensview-select-index-and-listeners.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_LEAK-002_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
