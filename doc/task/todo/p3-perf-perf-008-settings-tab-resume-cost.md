# PERF-008 — FrmSettings.assignValues đọc 35 prefs và resolve launcher hệ thống mỗi lần resume

| Field | Value |
|---|---|
| Type | enhance |
| Status | todo |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Settings performance |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`ui/FrmSettings.kt:303-395` gọi từ `onViewCreated:96` và `onResume:101`: ~35 lần đọc prefs + `UtilLauncher.getNameHomeLauncher` (`:319`, `resolveActivity` + `loadLabel` của app khác, đồng bộ trên main thread). Tab luôn RESUMED vì `setOffscreenPageLimit(2)` (`ActSettings.java:188`). Cùng lớp lỗi UI-024.

## User story

Là người dùng, tôi muốn chuyển tab Settings không bị khựng.

## Acceptance criteria

- [ ] Cache tên launcher mặc định, làm mới có điều kiện.
- [ ] Chỉ gán lại giá trị đã đổi.
- [ ] Test số lần `resolveActivity` khi resume nhiều lần.
- [ ] Đo hitch trước/sau.

## Implementation notes

Giữ hành vi hiển thị nguyên vẹn.

## Verification and Definition of Done

- [ ] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [ ] Widget/UI tests cover visible behavior, accessibility and lifecycle where applicable.
- [ ] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [ ] Smoke tests pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [ ] No new lint/build failures.
- [ ] A post-change audit record scores the round `> 9.0/10` before push.
- [ ] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story PERF-008 (FrmSettings.assignValues đọc 35 prefs và resolve launcher hệ thống mỗi lần resume) trong doc/task/todo/p3-perf-perf-008-settings-tab-resume-cost.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_PERF-008_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
