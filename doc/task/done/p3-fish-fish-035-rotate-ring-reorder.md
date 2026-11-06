# FISH-035 — Vòng lấy nét: xoay một ngón quanh tâm để quay vòng thứ tự app

| Field | Value |
|---|---|
| Type | exclusive |
| Status | done |
| Priority | P3 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 5 SP |
| Risk | High |
| Dependencies | CORE-004, FISH-029 |

## Context and evidence

`AppPersistent.setAppOrderBatch` (generation-guarded) có sẵn; cờ frozen của FISH-021; `LensGestureState` cần tách khỏi pan/pinch.

## User story

Là người dùng một tay, tôi muốn xoay vòng tròn để sắp xếp lại app.

## Acceptance criteria

- [x] Cử chỉ nhận diện rõ, không nhầm pan/pinch. (tracker sweep + bán kính + độ cong; đường thẳng/tiếp tuyến không thành xoay trong mô phỏng và test)
- [x] Chốt có xác nhận bằng `AppPersistent.commitAppOrderBatch` (additive; `setAppOrderBatch` giữ nguyên), frozen chỉ lưu sau khi ghi DB thành công; lens đã frozen vẫn xoay được.
- [x] Test state machine + smoke. (smoke máy thật bằng luồng MotionEvent liên tục qua UiAutomation; adb `input` không tạo được cung)
- [x] Có đường thay thế a11y. (hai action host Rotate forward/back, ngoại lệ TalkBack owner duyệt)

## Implementation notes

Cần chạy sau CORE-004 để tránh sort ghi đè thứ tự vừa chốt.

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget/UI tests cover visible behavior, accessibility and lifecycle where applicable.
- [x] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [x] Smoke tests pass on Pixel 7 Pro `2B051FDH3006MU` locked by the owner for this story; record model, Android version, build SHA and timestamp; restore device state.
- [x] No new lint/build failures.
- [x] A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story FISH-035 (Vòng lấy nét: xoay một ngón quanh tâm để quay vòng thứ tự app) trong doc/task/todo/p3-fish-fish-035-rotate-ring-reorder.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_FISH-035_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.

## Kết quả

Đóng 2026-11-06, audit 9,1/10 (`doc/task/AUDIT_FISH-035_2026-11-06.md`), thiết bị Pixel 7 Pro `2B051FDH3006MU` (owner đổi target từ TECNO). Chấp nhận giới hạn: ngưỡng (30°, 300 ms) chỉ chỉnh bằng sự kiện tổng hợp, owner chưa xoay tay, TalkBack thật chưa chạy, bản dịch AI, pan khi ghim chậm tối đa 300 ms, xoay theo từng nhóm pinned/favorite. Chưa push.
