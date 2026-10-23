# PERF-007 — Scan PackageManager gây N+1 truy vấn Room và ghi từng app

| Field | Value |
|---|---|
| Type | enhance |
| Status | done 2026-10-23 |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Refresh pipeline |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`util/UtilApp.kt:123` `findAnyByIdentifier(identifier)` mỗi app trong vòng lặp, cộng `setAppPaletteColorAndAwait` (`:128`) ghi từng app. Máy 350 app là 350+ round trip mỗi package event.

## User story

Là người dùng, tôi muốn danh sách app cập nhật nhanh sau khi cài/gỡ.

## Acceptance criteria

- [x] Một truy vấn lấy toàn bộ row, map theo identifier.
- [x] Ghi palette theo lô một lần.
- [x] Test kết quả không đổi; đếm số truy vấn.
- [x] Đo thời gian refresh trước/sau.

## Implementation notes

Giữ generation guard của `replaceSnapshot`.

## Kết quả (2026-10-23)

`getAppShells` đọc `getAllRows()` một lần (row đầu tiên theo identifier thắng) và gom palette mới vào một `@Transaction` `setPaletteColors` cuối scan. Test: Room batch 3/3 (bảo toàn field, lens phụ, rollback), scanner 2/2 (RED 3 SELECT → GREEN 1). Full unit 996, lint 0 lỗi/7 warnings. Pixel 7 Pro: 10 test thiết bị pass; DB fixture 100 app warm 126→12 ms, cold persist 605→149 ms; full refresh 5 lượt 1937→1611 ms trung bình (dải chồng nhau). Số INSERT/UPDATE vẫn tỷ lệ với số app mới. Audit: `doc/task/AUDIT_PERF-007_2026-10-23.md` (9.1/10); giới hạn: không đo cold palette trên DB người dùng, chưa có test cancellation riêng.

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget/UI tests: không thêm UI mới; chạy `LensViewIconReloadWidgetTest` và `ActHomeLensScopeWidgetTest` (không đổi hành vi hiển thị).
- [x] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [x] Smoke tests pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [x] No new lint/build failures.
- [x] A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story PERF-007 (Scan PackageManager gây N+1 truy vấn Room và ghi từng app) trong doc/task/todo/p2-perf-perf-007-scan-n-plus-one-room.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_PERF-007_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
