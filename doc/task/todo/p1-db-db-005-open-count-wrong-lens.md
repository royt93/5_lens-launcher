# DB-005 — openCount đọc lại từ lens default ghi đè số của lens đang mở

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Multi-lens |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

`model/AppPersistentDao.kt:41` `incrementExisting` không lọc `LENS_ID` (global, đúng ý) nhưng `:44` `getOpenCount` cố định `LENS_ID='default'`. `AppPersistent.incrementAppCount` (`:112-117`) đặt optimistic = count lens hiện tại + 1 rồi ghi đè bằng `committedCount` của lens default. Lens Work có 50, default có 3 thì in-memory nhảy 51 rồi 4. Smart Focus (`SmartFocusArranger`) và sort `OPEN_COUNT_*` ở lens không-default dùng số sai.

## User story

Là người dùng nhiều lens, tôi muốn thứ tự theo số lần mở và Smart Focus phản ánh đúng thói quen thật.

## Acceptance criteria

- [ ] `OPEN_COUNT` tiếp tục global theo thiết kế; mọi nơi đọc dùng cùng một nguồn nhất quán.
- [ ] Không còn nhảy số lùi sau khi mở app ở lens không-default.
- [ ] Sort theo số lần mở và Smart Focus cho kết quả giống nhau giữa các lens cho cùng app.
- [ ] Có test: mở app ở lens Work, count in-memory khớp Room.

## Implementation notes

Chọn một nguồn: `getOpenCount` không lọc lens (MAX hoặc row bất kỳ vì cùng giá trị). Giữ nguyên cam kết 'openCount/palette color global across lenses'.

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
Làm story DB-005 (openCount đọc lại từ lens default ghi đè số của lens đang mở) trong doc/task/todo/p1-db-db-005-open-count-wrong-lens.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_DB-005_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
