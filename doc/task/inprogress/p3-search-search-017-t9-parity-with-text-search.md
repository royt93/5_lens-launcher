# SEARCH-017 — T9 đồng bộ hạn mức, lịch sử và alias với tìm kiếm chữ

| Field | Value |
|---|---|
| Type | idea |
| Status | todo |
| Priority | P3 |
| Evidence | confirmed |
| Epic | SEARCH-010 |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | SEARCH-012 |

## Context and evidence

`search/T9SearchEngine.kt:21-46` không có `limit` (đường chữ có `DEFAULT_LIMIT=24`, `AppSearchEngine.kt:9`) và không nhận `recentKeys` (`ActHome.java:1708-1709` chỉ truyền cho đường chữ); chỉ ký hiệu hóa `app.label` (`:28`) trong khi `AppSearchEngine` rank cả `packageAliases` (`:57,65`). Query `2` trên máy 346 app đẩy hàng trăm row vào RecyclerView.

## User story

Là người dùng T9, tôi muốn kết quả có thứ tự và số lượng giống tìm kiếm chữ.

## Acceptance criteria

- [ ] Giới hạn kết quả.
- [ ] Ưu tiên theo lịch sử gần đây.
- [ ] Khớp cả alias.
- [ ] Test thứ tự và giới hạn.

## Implementation notes

Làm sau SEARCH-012.

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
Làm story SEARCH-017 (T9 đồng bộ hạn mức, lịch sử và alias với tìm kiếm chữ) trong doc/task/todo/p3-search-search-017-t9-parity-with-text-search.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_SEARCH-017_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
