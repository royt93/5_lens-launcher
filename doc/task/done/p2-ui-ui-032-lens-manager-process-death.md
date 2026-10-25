# UI-032 — LensManagementActivity mất state dialog sau process death và đọc prefs trên main thread

| Field | Value |
|---|---|
| Type | enhance |
| Status | done 2026-10-25 — audit 9.3/10, 35/35 device tests on TECNO |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Lens manager |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`ui/LensManagementActivity.kt:309-313` `onSaveInstanceState` chỉ lưu `STATE_EDITING` + `STATE_SELECTED`, không lưu dialog `lensGroupDialog` (`:43`) nên process death khi nhập tên nhóm thì mất. `settings.getCollapsedLensGroups()` đọc đồng bộ 3 lần trên main thread (`:149,303,334`). Cùng lớp UI-023/FISH-012 và UI-024.

## User story

Là người dùng, tôi muốn không mất chữ đang nhập khi hệ thống thu hồi tiến trình.

## Acceptance criteria

- [x] Lưu/khôi phục text và trạng thái dialog nhóm.
- [x] Đọc collapsed groups một lần, cache theo vòng đời.
- [x] Widget test tái tạo Activity giữa lúc nhập tên (kiểm chứng qua `ActivityScenario.recreate()`).
- [x] (Owner thêm 2026-10-23) Edge-to-edge: toolbar không bị status bar/cutout đè; `rvLensGroups` và thanh `lensGroupActions` không bị navigation/gesture bar che.

## Implementation notes

Theo mẫu UI-023. Bổ sung `LensManagerRoomSnapshot` bảo đảm fixtures không làm mất dữ liệu Room trên thiết bị thật.

## Verification and Definition of Done

- [x] Unit tests: 1,043/1,043 JVM tests pass.
- [x] Widget/UI tests: `LensManagementStateRestorationTest` 3/3, `LensManagementEdgeToEdgeWidgetTest` 4/4, `LensManagementActivityWidgetTest` 28/28 (tổng 35/35 trên TECNO BG6).
- [x] Integration tests: Room snapshot/restore toàn vẹn, SQLite dump diff = 0.
- [x] Smoke tests: TECNO BG6 Android 13 (`118743744X002560`), build SHA `79b2f28a`+, screenshot `/tmp/ui032-manager.png` kiểm chứng insets sạch, không có ad che UI (R4 sạch).
- [x] No new lint/build failures: lint devDebug 0 error.
- [x] Post-change audit record: `doc/task/AUDIT_UI-032_2026-10-25.md` chấm 9.3/10.
- [x] Evidence and status updated.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story UI-032 (LensManagementActivity mất state dialog sau process death và đọc prefs trên main thread) trong doc/task/todo/p2-ui-ui-032-lens-manager-process-death.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_UI-032_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
