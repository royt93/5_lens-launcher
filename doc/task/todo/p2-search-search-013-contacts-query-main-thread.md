# SEARCH-013 — Query toàn bảng Contacts đồng bộ trên main thread mỗi ký tự gõ

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Search performance |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

`search/ContactSearchEngine.kt:82-107` `contentResolver.query(Phone.CONTENT_URI, ..., null, null, null)` rồi rank trong bộ nhớ, gọi từ `ActHome.java:1936` `firstContactForQuery` <- `updateContactAction` <- `updateSearchResults` <- `onTextChanged` (`:1451`), không debounce. Mỗi ký tự là một lần đọc cursor đồng bộ; `DebugStrictMode` (`util/DebugStrictMode.kt:31`) sẽ log DiskRead tại đây. Cùng pipeline mỗi keystroke còn chạy tới 9 resolver quick action (`QuickActionEngine.kt`).

## User story

Là người dùng nhiều danh bạ, tôi muốn gõ tìm kiếm mượt, không giật.

## Acceptance criteria

- [ ] Không còn query ContentProvider trên main thread trong đường `onTextChanged`.
- [ ] Có debounce hợp lý và hủy truy vấn cũ khi gõ tiếp.
- [ ] Có `selection`/giới hạn hoặc cache danh bạ có invalidation.
- [ ] Kết quả cuối khớp hành vi cũ (test ranking giữ nguyên).
- [ ] Đo trước/sau trên máy thật nhiều danh bạ.

## Implementation notes

Gộp ENH-1 của audit ui. Tách `HomeSearchController` là việc của ARCH-002, không bắt buộc ở đây.

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
Làm story SEARCH-013 (Query toàn bảng Contacts đồng bộ trên main thread mỗi ký tự gõ) trong doc/task/todo/p2-search-search-013-contacts-query-main-thread.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_SEARCH-013_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
