# SEARCH-011 — Panel Recent apps hiện app người dùng đã ẩn

| Field | Value |
|---|---|
| Type | fix |
| Status | done |
| Priority | P1 |
| Evidence | confirmed |
| Epic | Privacy |
| Estimate | 1 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`search/RecentAppsPanelResolver.kt:14-17` `resolve()` map key sang snapshot mà không lọc `App::isVisible`, trong khi `AppSearchEngine.kt:33` và `T9SearchEngine.kt:26` đều lọc. App bị ẩn vẫn hiện và mở được từ bottom sheet (`ui/RecentAppsPanelFragment.kt:81`). Đây là lỗi riêng tư, không chỉ giao diện.

## User story

Là người dùng ẩn app riêng tư, tôi muốn app đó không lộ ở bất kỳ danh sách nào.

## Acceptance criteria

- [x] `resolve` loại app `isVisible=false`.
- [x] Test đỏ trong `RecentAppsPanelResolverTest`: key trỏ app ẩn không xuất hiện.
- [x] Ẩn app xong, panel cập nhật đúng khi mở lại.

## Implementation notes

Sửa một dòng `filter`. Giữ hành vi bỏ key mồ côi.

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget/UI tests cover visible behavior, accessibility and lifecycle where applicable.
- [x] Integration tests cover subsystem boundaries and persistence/SDK contracts where applicable.
- [x] Smoke tests pass on the device locked for the session; record model, Android version, build SHA and timestamp; restore device state.
- [x] No new lint/build failures.
- [x] A post-change audit record scores the round `> 9.0/10` before push.
- [x] Evidence and status are updated before moving this file to `done`.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Kết quả (2026-10-20)

Hoàn tất. Audit **9.1/10**: `doc/task/AUDIT_SEARCH-011_2026-10-20.md`. Phạm vi theo owner: chỉ `isVisible=false`; test unit + widget + integration, thêm interruption (recreate, dismiss-reopen), memory leak (có kiểm đột biến) và đo thời gian refresh. Không làm: golden, network (không áp dụng), IAP/ad mediation (không áp dụng). Unit 966/966, lint 0 lỗi/7 warning, 58 instrumentation + smoke trên TECNO KJ7. Phát hiện thêm lỗi thứ hai ngoài story: panel không cập nhật khi app bị ẩn lúc đang mở; đã sửa.

## Prompt để chạy loop

```text
Làm story SEARCH-011 (Panel Recent apps hiện app người dùng đã ẩn) trong doc/task/todo/p1-search-search-011-recent-panel-shows-hidden-apps.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_SEARCH-011_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
