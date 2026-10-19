# DB-004 — orderIndex trùng sau khi xóa lens giữa làm backup không import lại được

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Workspace backup |
| Estimate | 2 SP |
| Risk | Medium |
| Dependencies | DB-003 |

## Context and evidence

`model/LensWorkspace.kt:78` `orderIndex = existing.size`. Tạo 3 lens (0,1,2), xóa lens giữa (còn 0,2), tạo mới thì `orderIndex=2` trùng. Export vẫn ghi (`LayoutBackupSnapshotFactory` không require unique) nhưng `LayoutBackup.kt:331` `orderIndices.toSet().size != orderIndices.size` trả `Malformed` khi import.

## User story

Là người dùng, tôi muốn file backup tôi export luôn import lại được.

## Acceptance criteria

- [ ] Lens mới có `orderIndex = max(existing)+1` (hoặc reindex liên tục).
- [ ] Xóa lens giữa rồi tạo mới không sinh trùng `orderIndex`.
- [ ] Export rồi import vòng khứ hồi thành công sau chuỗi tạo/xóa ngẫu nhiên (property-style test).
- [ ] Bản cài cũ đã có dữ liệu trùng được tự sửa khi mở app (migration dữ liệu, không đổi version nếu không cần).

## Implementation notes

Phụ thuộc DB-003 vì cùng vùng DAO/validation. Không bump schema nếu chỉ sửa dữ liệu lúc runtime; nếu bump phải kèm migration và snapshot `app/schemas`.

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
Làm story DB-004 (orderIndex trùng sau khi xóa lens giữa làm backup không import lại được) trong doc/task/todo/p1-db-db-004-lens-order-index-collision.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_DB-004_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
