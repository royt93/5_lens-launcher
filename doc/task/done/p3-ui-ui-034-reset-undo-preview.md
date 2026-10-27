# UI-034 — Xem trước và hoàn tác khi reset về mặc định

| Field | Value |
|---|---|
| Type | new |
| Status | done |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Settings safety |
| Estimate | 3 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`ui/FrmLens.kt:267` `resetToDefault` và `ui/FrmSettings.kt:519` xóa tức thì (thực tế Lens 5 + Settings 26 + Apps 1 khóa, không phải ~22), chỉ có Snackbar sau (`ActSettings.java:358`), không hoàn tác.

## User story

Là người dùng, tôi muốn biết reset sẽ đổi gì và hoàn tác được.

## Acceptance criteria

- [x] Dialog liệt kê các mục sẽ đổi.
- [x] Hoàn tác một lần bằng snapshot prefs trong bộ nhớ (Snackbar action).
- [x] Test: reset rồi undo trả đúng toàn bộ giá trị cũ, kể cả key vắng.

## Implementation notes

Snapshot/restore theo mẫu test isolation (nhớ cả key không tồn tại).

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget/UI tests: `ResetDefaultsWidgetTest` trên KJ7. Accessibility: Not applicable, dùng AlertDialog/Snackbar chuẩn của Material, không có control tuỳ biến (đã review).
- [x] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [x] Smoke tests pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [x] No new lint/build failures.
- [x] A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story UI-034 (Xem trước và hoàn tác khi reset về mặc định) trong doc/task/todo/p3-ui-ui-034-reset-undo-preview.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_UI-034_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
