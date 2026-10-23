# DB-007 — Backup v2 không chứa physics từng lens

| Field | Value |
|---|---|
| Type | enhance |
| Status | done 2026-10-23 — audit 9.1/10, `doc/task/AUDIT_DB-007_2026-10-23.md` |
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

- [x] Thêm trường tùy chọn (additive), file v2 cũ vẫn đọc được.
- [x] Export/import khứ hồi giữ đúng 4 giá trị per-lens.
- [x] Import file cũ không xóa giá trị hiện có của máy.
- [x] Test tương thích ngược với fixture v2 hiện tại.

## Evidence recheck (2026-10-23)

DB-003/004 đã done. Đã kiểm lại trên `dev`: `LayoutBackupIo.exportAsync` chỉ mang scope/selection/frozen/hidden (`LensAppSettingsBackup`); `replaceWorkspacePreferences` xóa mọi key `isWorkspacePreferenceKey` rồi ghi lại 4 loại này. Bốn key physics: `distortion_factor_<id>` (Float), `min_icon_size_<id>` (Float), `custom_distortion_factor_<id>` (Float), `smart_focus_bias_<id>` (Boolean). Lens không có override kế thừa key chung không hậu tố, nên backup phải phân biệt "không override" với "override = giá trị".

## Implementation notes

Làm sau DB-003/004 để schema ổn định trước.

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget/UI tests: không đổi UI; `FrmSettingsLayoutBackupWidgetTest` hiện có vẫn xanh trên máy thật (không thêm widget test mới, lý do: chỉ đổi dữ liệu backup).
- [x] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [x] Smoke: Samsung S24 Ultra `R5CX613VZBR`, Android 17, 2026-10-23 16:17 (owner đồng ý đổi từ Pixel vì Pixel mất kết nối adb); ActHome mở, lưới đủ, không quảng cáo; đã gỡ app và khôi phục launcher hệ thống.
- [x] No new lint/build failures.
- [x] A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence and status are updated before moving this file to `done`.

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
