# DB-008 — Không có migration dưới v7 và không có fallback, schema 7/8 thiếu snapshot

| Field | Value |
|---|---|
| Type | enhance |
| Status | done 2026-10-26 (audit 9.1/10, `doc/task/AUDIT_DB-008_2026-10-26.md`) |
| Priority | P3 |
| Evidence | confirmed |
| Epic | Persistence |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

`model/AppDatabase.kt:28` chỉ `addMigrations(MIGRATION_7_8 ... 12_13)`, không `fallbackToDestructiveMigration`. DB SugarORM quá cũ (<7) khiến Room ném `IllegalStateException` ngay khi mở, crash loop không tự phục hồi. `app/schemas/` chỉ có 9-13, thiếu 7 và 8 nên không test được hai bước đầu; migration test hiện chỉ phủ 10→11, 11→12, 12→13. `MIGRATION_8_9` có dedup `DELETE ... MAX(ID) GROUP BY IDENTIFIER` (row identifier rỗng gộp thành một, có nguy cơ mất dữ liệu) chưa có test.

## User story

Là người dùng nâng cấp từ bản rất cũ, tôi muốn app mở được thay vì crash.

## Acceptance criteria

- [x] Có đường xử lý DB quá cũ: migration không phá hủy từ legacy (bảng không đọc được được giữ lại ở `APP_PERSISTENT_UNRECOGNISED_BACKUP`).
- [x] Thêm test migration 7→8 và 8→9 (kèm trường hợp identifier rỗng) — `AppDatabaseLegacyMigrationTest`, 11 test.
- [x] Snapshot schema 7/8 không tái dựng được chính xác nên không tạo; giới hạn ghi trong audit.
- [x] Không phá migration hiện có (schema v13 không đổi; test model 118/118 + 18 test thiết bị xanh).

## Implementation notes

Quyết định fallback phá hủy cần owner xác nhận vì mất dữ liệu người dùng. **Đã chọn (owner giao quyền tự quyết, 2026-10-26): không phá hủy** — migrate dữ liệu, bảng lạ đổi tên thành backup.

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget/UI tests: Not applicable — không đổi UI (đã review).
- [x] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [x] Smoke tests pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [x] No new lint/build failures.
- [x] A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story DB-008 (Không có migration dưới v7 và không có fallback, schema 7/8 thiếu snapshot) trong doc/task/todo/p3-db-db-008-legacy-db-migration-fallback.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_DB-008_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
