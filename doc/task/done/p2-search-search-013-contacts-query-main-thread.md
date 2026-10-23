# SEARCH-013 — Query toàn bảng Contacts đồng bộ trên main thread mỗi ký tự gõ

| Field | Value |
|---|---|
| Type | fix |
| Status | done 2026-10-23 |
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

- [x] Không còn query ContentProvider trên main thread trong đường `onTextChanged`.
- [x] Có debounce hợp lý và hủy truy vấn cũ khi gõ tiếp.
- [x] Debounce 150ms + hủy query cũ chuyển sang worker thread; cache danh bạ dài hạn hoãn theo spec để tránh rò rỉ state/Observer.
- [x] Kết quả cuối khớp hành vi cũ (test ranking giữ nguyên).
- [x] Đo trước/sau trên máy thật nhiều danh bạ.

## Evidence recheck (2026-10-23)

Đã kiểm lại trên `dev`: `ActHome.java:1450` `onTextChanged` → `updateSearchResults` (`:1682`) → `updateContactAction` (`:1932`) → `ContactSearchEngine.firstContactForQuery` → `contentResolver.query(Phone.CONTENT_URI, …, null, null, null)` (không selection) rồi rank trong bộ nhớ, tất cả trên main thread. Chỉ chạy khi có tiền tố liên hệ (`contact/call/sms…`) và quyền READ_CONTACTS.

## Kết quả và bằng chứng (2026-10-23)

- `ContactSearchController` điều phối tìm kiếm danh bạ trên `Dispatchers.IO` với debounce 150ms và generation guard.
- Unit tests (`ContactSearchControllerTest`): 8/8 pass (debounce, cancel, generation race, worker thread, error handling, close).
- Widget tests (`AppSearchWidgetTest`): 26/26 pass (thêm test recreate Activity lúc query đang pending) (xác nhận contact row xuất hiện bất đồng bộ sau debounce).
- Benchmark trên Pixel 7 Pro (`ContactSearchLatencyTest` với 1000 contacts giả): 9 keystroke dồn lại chỉ query 1 lần trên worker thread, 0 query trên main thread (so với baseline cũ query 9 lần đồng bộ trên main, chặn main 431 ms; candidate không chặn main, thời gian tường 392 ms gồm gõ và debounce).
- Full verification: 1004 unit tests pass, lint 0 lỗi / 7 warnings.
- Audit: `doc/task/AUDIT_SEARCH-013_2026-10-23.md` (9.2/10).

## Implementation notes

Gộp ENH-1 của audit ui. Tách `HomeSearchController` là việc của ARCH-002, không bắt buộc ở đây.

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget/UI tests cover visible behavior, accessibility and lifecycle where applicable.
- [x] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [x] Smoke tests pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [x] No new lint/build failures.
- [x] A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence and status are updated before moving this file to `done`.

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
