# UI-029 — Search bar bị Clean mode đè mà Settings vẫn hiện bật; chevron quick action cứng không RTL

| Field | Value |
|---|---|
| Type | fix |
| Status | done 2026-10-25 — audit 9.2/10, 45/45 device tests on TECNO |
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

- [x] Khi Clean mode bật, `swShowSearchBar` bị vô hiệu hóa kèm hint giải thích (chuỗi đủ locale đã tồn tại và tái sử dụng).
- [x] Đổi Clean mode phát `AppEventManager.appsEdited`; Home cập nhật khi Activity observer đang active/resumed.
- [x] Chevron dùng `ic_arrow_forward_24dp` relative compound drawable với `autoMirrored`.
- [x] Widget test đặt quick-action row sang RTL và xác nhận drawable auto-mirror. Locale `ar` toàn Activity không bị đổi trong test; hướng được xác minh trực tiếp bằng RTL layout direction của widget.

## Implementation notes

Gộp FIX-5 và FIX-6 của audit ui.

## Verification and Definition of Done

- [x] Pure-logic unit test: Not applicable; change composes existing prefs, LiveData event, and view state, with no new pure policy. Full 1,043 unit tests pass.
- [x] Widget/UI behavior: TECNO targeted Settings + Search tests pass; `FrmSettingsCleanLensWidgetTest` 7/7 and `AppSearchWidgetTest` 27/27.
- [x] Integration: `ActHomeCleanLensModeIntegrationTest` 10/10 and live Home resume fixture 1/1 pass on TECNO.
- [x] Smoke tests pass on TECNO BG6 `118743744X002560`; screenshot verified, R4 clean.
- [x] No new lint/build failures: `testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` passed.
- [x] Post-change audit record: `doc/task/AUDIT_UI-029_2026-10-25.md` scores 9.2/10.
- [x] Evidence and status updated.

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
