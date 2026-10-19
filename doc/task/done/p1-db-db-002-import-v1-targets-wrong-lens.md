# DB-002 — Import backup v1 ghi nhầm vào lens đang mở

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Workspace backup |
| Estimate | 3 SP |
| Risk | High |
| Dependencies | None |

## Context and evidence

`util/LayoutImportApplier.kt:47` `apply()` gọi `AppPersistent.setAppOrderBatch(orderedApps, orderedApps.first().lensId)`. Danh sách app lấy từ `RAppsSingleton.getApps()` (`ActSettings.java:511`), vốn merge theo `KEY_ACTIVE_LENS_ID` (`TaskUpdateApps.kt:143`). Đứng ở lens Work rồi import v1 thì layout lens Work bị ghi đè, trong khi dialog `layout_import_v1_default_lens_note` hứa chỉ cập nhật lens default. Đường `WorkspaceBackupRoomStore.applyLegacyV1` (`:46`, nguyên tử, lọc app đã cài, giữ `openCount`/`paletteColor`) chỉ được gọi từ `WorkspaceBackupImportCoordinator` và test, không tới được từ UI v1.

## User story

Là người dùng nhiều lens, tôi muốn import backup v1 chỉ ảnh hưởng lens default đúng như dialog nói, để không mất layout lens đang dùng.

## Acceptance criteria

- [x] Import v1 khi đang đứng ở lens không-default không thay đổi bất kỳ row nào của lens đó.
- [x] Layout v1 được áp vào lens `default` và `RAppsSingleton` phản ánh đúng sau import.
- [x] Đường production v1 đi qua `applyLegacyV1` (một đường duy nhất, có rollback), đường `LayoutImportApplier.apply` cũ bị xóa hoặc chỉ còn là phần tính plan.
- [x] Import lỗi giữa chừng không để lại trạng thái nửa vời (có test rollback).

## Implementation notes

Gộp FIX-1 và FIX-2 của audit model: dùng `WorkspaceBackupImportCoordinator` cho cả v1. Test đỏ trước: seed `RAppsSingleton` ở lens Work, import v1, assert row lens Work không đổi.

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

Hoàn tất. Audit **9.2/10**: `doc/task/AUDIT_DB-002_2026-10-19.md`. Unit 929/929, lint 0 lỗi/7 warning, 18 instrumentation pass + smoke trên Pixel 7 Pro (owner chọn). Giới hạn: preview v1 đếm theo lens đang mở; chưa smoke chọn file qua SAF.

## Prompt để chạy loop

```text
Làm story DB-002 (Import backup v1 ghi nhầm vào lens đang mở) trong doc/task/todo/p1-db-db-002-import-v1-targets-wrong-lens.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_DB-002_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
