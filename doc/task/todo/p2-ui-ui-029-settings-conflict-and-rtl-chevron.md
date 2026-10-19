# UI-029 — Search bar bị Clean mode đè mà Settings vẫn hiện bật; chevron quick action cứng không RTL

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Settings consistency |
| Estimate | 2 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`ui/FrmSettings.kt:239-250` `swShowSearchBar` và `swCleanLensMode` độc lập, nhưng `ActHome.updateSearchBarVisibility:2165` tính `showSearchBar = !isCleanMode && KEY_SHOW_SEARCH_BAR`. Toggle vẫn bật dù thanh bị ẩn, không hint; `swCleanLensMode` không phát sự kiện, chỉ áp ở `onResume`. `ui/ActHome.java:1819` `tvQuickActionValue.setText("›")` chữ cứng, không `autoMirrored`/`textDirection`, mũi tên sai hướng ở `ar`.

## User story

Là người dùng, tôi muốn công tắc phản ánh đúng tác dụng và mũi tên đúng chiều ở ngôn ngữ RTL.

## Acceptance criteria

- [ ] Khi Clean mode bật, `swShowSearchBar` bị vô hiệu hóa kèm hint giải thích (chuỗi đủ locale).
- [ ] Đổi Clean mode cập nhật `ActHome` đang mở qua cơ chế sự kiện sẵn có.
- [ ] Chevron dùng drawable `autoMirrored` hoặc ký tự theo `layoutDirection`.
- [ ] Widget test RTL với locale `ar`.

## Implementation notes

Gộp FIX-5 và FIX-6 của audit ui.

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
Làm story UI-029 (Search bar bị Clean mode đè mà Settings vẫn hiện bật; chevron quick action cứng không RTL) trong doc/task/todo/p2-ui-ui-029-settings-conflict-and-rtl-chevron.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_UI-029_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
