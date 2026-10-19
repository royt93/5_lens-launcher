# DB-007 — Backup v2 không chứa physics từng lens

| Field | Value |
|---|---|
| Type | enhance |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Workspace backup |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | DB-003, DB-004 |

## Context and evidence

`model/LayoutBackup.kt:120-130` `LayoutBackupLens` và `LayoutBackupIo.exportAsync:36-44` chỉ mang scope/selection/frozen/hidden/group. Mất khi khôi phục: `KEY_DISTORTION_FACTOR_<lensId>`, `KEY_ICON_SIZE_<lensId>` (FISH-018), `KEY_SMART_FOCUS_BIAS_<lensId>`, `KEY_CUSTOM_DISTORTION_FACTOR_<lensId>`. `isWorkspacePreferenceKey` (`UtilSettings.kt:570-575`) cũng không gồm các key này nên import để lại giá trị cũ của máy.

## User story

Là người dùng, tôi muốn khôi phục backup ra đúng cảm giác lens như trước.

## Acceptance criteria

- [ ] Thêm trường tùy chọn (additive), file v2 cũ vẫn đọc được.
- [ ] Export/import khứ hồi giữ đúng 4 giá trị per-lens.
- [ ] Import file cũ không xóa giá trị hiện có của máy.
- [ ] Test tương thích ngược với fixture v2 hiện tại.

## Implementation notes

Làm sau DB-003/004 để schema ổn định trước.

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
Làm story DB-007 (Backup v2 không chứa physics từng lens) trong doc/task/todo/p2-db-db-007-backup-v2-include-lens-physics.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_DB-007_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
