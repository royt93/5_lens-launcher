# FISH-030 — Chế độ ống kính đảo (pincushion)

| Field | Value |
|---|---|
| Type | idea |
| Status | done (2026-11-02): audit 9,2/10 (xem AUDIT_FISH-030_2026-10-31.md), smoke UI thật và cặp ảnh Fisheye/Pincushion trên TECNO KJ7, chưa push |
| Priority | P3 |
| Evidence | implementation + tests + TECNO KJ7 UI smoke + paired render; audit 9.2/10, not pushed |
| Epic | Fisheye Smart |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

`UtilSettings.MIN_DISTORTION_FACTOR = 0.5f` (`:102`) chỉ cho phóng ở tâm; toán đã có.

## User story

Là người dùng, tôi muốn chế độ ngắm rìa đẩy icon ra biên.

## Acceptance criteria

- [x] Thay "mở biên dưới" bằng profile PINCUSHION riêng per-lens, độ méo giữ dương (owner chốt 2026-10-31); không preset mới.
- [x] Test hit-test: rect đã vẽ = rect hit-test cho mọi profile (`LensViewProfileIntegrationTest`).
- [x] Độc lập FISH-026/FISH-009 (pinch 12/12, widget 18/18, smart focus 9/9 xanh trên BG6; không đổi đường đàn hồi/pinch).

## Implementation notes

Rủi ro hit-test; viết test thuần trước.

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (`LensProfileTest` 13, `LensProfileSettingsTest`, `LayoutBackupPhysicsTest` 11; 1.418 unit, 0 fail).
- [x] Widget/UI tests cover visible behavior and lifecycle (`LensSettingsActivityWidgetTest` 9). TalkBack/A11Y ngoài phạm vi theo owner; nút profile dùng control Material chuẩn.
- [x] Integration tests cover subsystem boundaries and persistence (`LensViewProfileIntegrationTest` 13, `WorkspaceBackupImportIntegrationTest` 21).
- [x] Smoke tests pass on the device locked for the session (TECNO KJ7, Android 14, APK sha256 `ea709f26...`, 2026-11-02); device restored, 1.192/1.192 file hashes match.
- [x] No new lint/build failures.
- [x] A post-change audit record scores the round `> 9.0/10` before push (9.2/10). Push itself is pending an explicit owner request.
- [x] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story FISH-030 (Chế độ ống kính đảo (pincushion)) trong doc/task/todo/p3-fish-fish-030-negative-distortion-pincushion.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_FISH-030_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
