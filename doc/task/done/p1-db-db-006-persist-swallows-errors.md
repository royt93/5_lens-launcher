# DB-006 — AppPersistent.persist nuốt CancellationException và lỗi DB sau khi đã ghi optimistic

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Persistence |
| Estimate | 2 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

`model/AppPersistent.kt:82-89` `catch (error: Throwable)` chỉ `Logger.e`. (a) Nuốt `CancellationException` phá structured cancellation của `ApplicationScope.scope`. (b) `setAppVisibility/setOpened/setOrganization/setAppOrderBatch` đã cập nhật `RAppsSingleton` trước (`:113, :174, :189, :230`) nên khi commit DB fail, in-memory và Room phân kỳ, không rollback, không báo; mất thiết lập (ẩn/khóa/thư mục) sau restart.

## User story

Là người dùng, tôi muốn thiết lập ẩn/khóa/thư mục không biến mất sau khi khởi động lại.

## Acceptance criteria

- [x] `CancellationException` luôn được ném lại.
- [x] Lỗi DB thật rollback trạng thái in-memory về giá trị trước và phát sự kiện lỗi để UI báo.
- [x] Test: DAO ném lỗi, snapshot in-memory trở về trạng thái cũ.
- [x] Không đổi hành vi đường thành công.

## Implementation notes

Ghi lại giá trị cũ trước khi áp optimistic, khôi phục trong `catch`; rethrow cancellation.

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget/UI tests cover visible behavior, accessibility and lifecycle where applicable.
- [x] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [x] Smoke tests pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [x] No new lint/build failures.
- [x] A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Kết quả (2026-10-20)

Hoàn tất. Audit **9.1/10**: `doc/task/AUDIT_DB-006_2026-10-20.md`. Unit 953/953, lint 0 lỗi/7 warning, 36+ instrumentation + smoke tay trên TECNO KJ7 (máy owner chọn). Giới hạn: đường lỗi chỉ chứng minh bằng test; prefs TECNO còn một số key mồ côi do các test có sẵn (TEST-005), chưa xóa được vì `run-as` không ghi prefs.

## Prompt để chạy loop

```text
Làm story DB-006 (AppPersistent.persist nuốt CancellationException và lỗi DB sau khi đã ghi optimistic) trong doc/task/todo/p1-db-db-006-persist-swallows-errors.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_DB-006_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
