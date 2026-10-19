# DB-003 — Lens default xóa được làm hỏng export/import và tự hồi sinh rỗng

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
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

- [ ] DAO từ chối xóa lens `default` (trả false) dù còn nhiều lens.
- [ ] Menu Delete bị disable cho lens `default` ở cả `ActHome` và `LensManagementActivity`, có mô tả a11y lý do.
- [ ] Sau mọi chuỗi tạo/xóa lens, export rồi import lại thành công (test vòng khứ hồi).
- [ ] Không còn đường nào làm `seedDefaultIfAbsent` sinh lens rỗng thừa.

## Implementation notes

Chặn ở DAO (nguồn sự thật) rồi mới disable UI. Không đổi schema Room.

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
