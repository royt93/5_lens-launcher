# FEAT-013 — Quick action 'app vừa cài' dựa trên installDate

| Field | Value |
|---|---|
| Type | idea |
| Status | done |
| Priority | P3 |
| Evidence | idea |
| Epic | Search |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`installDate` đã có trong `model/App` (`util/UtilApp.kt:101`); cần 0 quyền mới.

## User story

Là người dùng, tôi muốn gõ vài chữ để mở nhanh app vừa cài.

## Acceptance criteria

- [x] Quick action liệt kê app cài trong N ngày (hằng số có tên).
- [x] Tôn trọng app ẩn.
- [x] Test thuần.

## Implementation notes

Đã làm trong `AppSearchEngine` (không dùng `QuickActionEngine` vì nó chỉ trả một dòng) và gắn từ khóa `new`/`recent`/`moi cai`/`vua cai`; xem `doc/task/AUDIT_FEAT-013_2026-10-27.md`.

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
Làm story FEAT-013 (Quick action 'app vừa cài' dựa trên installDate) trong doc/task/todo/p3-feat-feat-013-recent-install-quick-action.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_FEAT-013_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
