# CORE-004 — TaskSortApps và TaskUpdateApps không chung generation guard, dữ liệu cũ ghi đè snapshot mới

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Refresh pipeline |
| Estimate | 3 SP |
| Risk | High |
| Dependencies | None |

## Context and evidence

Hai pipeline giữ `requestedGeneration` riêng (`TaskSortApps.kt:32`, `TaskUpdateApps.kt:59`). `TaskSortApps.loadSortedSnapshot` (`:69`) copy list rồi `commit` (`:77-78`) `instance.apps = ArrayList(snapshot)`. Nếu sort đang chạy mà `TaskUpdateApps.commit` (`:168-170`) vừa commit generation mới (app vừa cài), sort ghi lại list cũ nên app mới mất tới lần refresh sau. Cả hai được `RApplication` bắn từ `appsEdited`/`appsUpdated` (`RApplication.java:287-297`).

## User story

Là người dùng, tôi muốn app vừa cài luôn hiện dù tôi vừa đổi cách sắp xếp.

## Acceptance criteria

- [x] Sort commit dùng generation chung với refresh; commit cũ bị bỏ.
- [x] Test đua: sort bay, update commit trước, kết quả cuối chứa app mới.
- [x] Không tăng thời gian refresh đáng kể (đo bằng test hiện có).
- [x] `RAppsSingleton.replaceSnapshot` vẫn là cổng commit duy nhất.

## Implementation notes

Đưa generation vào `RAppsSingleton` hoặc cho sort chạy trên snapshot đã commit và dùng compare-and-set theo generation.

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget/UI tests cover visible behavior, accessibility and lifecycle where applicable.
- [x] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [x] Smoke tests pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [x] No new lint/build failures.
- [x] A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Kết quả (2026-10-19)

Hoàn tất. Audit **9.1/10**: `doc/task/AUDIT_CORE-004_2026-10-19.md`. Unit 929/929, lint 0 lỗi/7 warning, 64 instrumentation + smoke tay trên Pixel 7 Pro. Giới hạn: race chứng minh bằng unit test; mutator cục bộ không tăng version (ghi ở audit). Device smoke: Pixel (owner chọn), không phải TECNO KJ7.

## Prompt để chạy loop

```text
Làm story CORE-004 (TaskSortApps và TaskUpdateApps không chung generation guard, dữ liệu cũ ghi đè snapshot mới) trong doc/task/todo/p1-core-core-004-sort-update-share-generation-guard.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_CORE-004_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
