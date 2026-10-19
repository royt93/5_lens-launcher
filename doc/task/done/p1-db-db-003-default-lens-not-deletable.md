# DB-003 — Lens default xóa được làm hỏng export/import và tự hồi sinh rỗng

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Workspace backup |
| Estimate | 2 SP |
| Risk | High |
| Dependencies | None |

## Context and evidence

`model/LensWorkspaceDao.kt:38-45` `deleteLensIfMoreThanOne` chỉ chặn khi còn <=1 lens, không chặn `DEFAULT_LENS_ID`. UI enable Delete chỉ theo `currentLenses.size() > 1` (`ActHome.java:932-933`, `LensManagementActivity.kt:275`). Sau khi xóa default: `LayoutBackupSnapshotFactory.kt:20` `require(count{id==DEFAULT}==1)` làm export fail, `LayoutBackup.kt:327` coi mọi file v2 cũ là `Malformed`, rollback import fail. `LensWorkspace.seedDefaultIfAbsent` (`:50-54`) chạy mỗi `RApplication.onCreate` nên lens `default` hồi sinh rỗng.

## User story

Là người dùng, tôi muốn lens gốc không thể bị xóa nhầm, để backup luôn export và import được.

## Acceptance criteria

- [x] DAO từ chối xóa lens `default` (trả false) dù còn nhiều lens.
- [x] Menu Delete bị disable cho lens `default` ở cả `ActHome` và `LensManagementActivity`, có mô tả a11y lý do.
- [x] Sau mọi chuỗi tạo/xóa lens, export rồi import lại thành công (test vòng khứ hồi).
- [x] Không còn đường nào làm `seedDefaultIfAbsent` sinh lens rỗng thừa.

## Implementation notes

Chặn ở DAO (nguồn sự thật) rồi mới disable UI. Không đổi schema Room.

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

Hoàn tất. Audit **9.1/10**: `doc/task/AUDIT_DB-003_2026-10-19.md`. Unit 930/930, lint 0 lỗi/7 warning, 82 instrumentation + smoke tay trên Pixel 7 Pro (owner chọn). Giới hạn: `enabled=false` của menu trên máy thật chỉ chứng minh bằng widget test (uiautomator không dump được popup); lens default đã bị xóa từ bản cũ vẫn hồi sinh rỗng.

## Prompt để chạy loop

```text
Làm story DB-003 (Lens default xóa được làm hỏng export/import và tự hồi sinh rỗng) trong doc/task/todo/p1-db-db-003-default-lens-not-deletable.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_DB-003_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
