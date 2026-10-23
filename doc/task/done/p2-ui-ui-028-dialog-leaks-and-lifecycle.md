# UI-028 — ActHome dùng một field lensDialog cho 5 dialog; vài dialog không dismiss khi destroy

| Field | Value |
|---|---|
| Type | fix |
| Status | done 2026-10-23 — audit 9.3/10, `doc/task/AUDIT_UI-028_2026-10-23.md` |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Stability |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | None |

## Context and evidence

`ui/ActHome.java:1099,1272,1308,1313,1346,1374` cùng gán `lensDialog`; chỉ `showHiddenLensAppsDialog:1303` dismiss cái cũ trước khi gán; `onDestroy:2345-2349` chỉ dismiss tham chiếu cuối. `adt/AppAdapter.java:463` `folderDialog`, `ui/FrmSettings.kt:82` `searchHintDialog`, `ui/FrmApps.kt:285` `pendingUninstallDialog` không được dismiss ở `onDestroyView`. Dialog cũ giữ decor view của Activity đã destroy.

## User story

Là người dùng, tôi muốn xoay màn hình hay đóng app không rò cửa sổ dialog.

## Acceptance criteria

- [x] Mỗi lần mở dialog mới dismiss cái cũ: `ActHome.dismissLensDialog()` gọi ở cả 5 vị trí gán `lensDialog`.
- [x] `ActHome.onDestroy`, `FrmSettings.onDestroyView`, `FrmApps.onDestroyView` và `AppAdapter.onViewRecycled` dismiss dialog đang giữ.
- [x] Widget test: recreate fragment/destroy Activity/recycle holder kiểm `isShowing` và field null; chạy RED trước, GREEN sau, có mutation check.
- [x] UI-023 hierarchy-state tests vẫn pass (`AppAdapterFolderDialogWidgetTest` 7/7, `FrmSettingsSearchHintWidgetTest` 1/1).

## Implementation notes

Gộp FIX-3 và test-gap #3 của audit ui. Đề xuất helper `showSingleDialog` dùng chung.

## Verification and Definition of Done

- [x] Unit tests: Not applicable (reviewed) — vòng đời cửa sổ Dialog thuộc framework Android; phủ bằng instrumentation RED/GREEN. Full unit 1027/1027 vẫn xanh.
- [x] 5 widget test mới + 75 regression, 80/80 pass trên Pixel 7 Pro.
- [x] Integration: Not applicable (reviewed) — không đổi persistence/SDK; `FrmAppsSelectionIntegrationTest` 10/10 vẫn pass.
- [x] Smoke Pixel 7 Pro `2B051FDH3006MU`, Android 17, 2026-10-23 18:50; ActHome foreground, lưới đầy đủ, không quảng cáo; APK baseline và Google Play foreground đã khôi phục.
- [x] Full gate xanh: 1027 unit, lint, assemble app + androidTest (chưa so số warning lint với baseline).
- [x] `doc/task/AUDIT_UI-028_2026-10-23.md`, 9.3/10.
- [x] Evidence/status cập nhật.

If a test layer is not applicable, replace its checkbox with a reviewed rationale; do not silently omit it.

## Prompt để chạy loop

```text
Làm story UI-028 (ActHome dùng một field lensDialog cho 5 dialog; vài dialog không dismiss khi destroy) trong doc/task/todo/p2-ui-ui-028-dialog-leaks-and-lifecycle.md.
1. Đọc CLAUDE.md, doc/task/README.md, file story này; kiểm chứng lại evidence trong code trước khi sửa (nếu sai thì ghi vào story và dừng).
2. TDD: viết test đỏ trước (unit, rồi widget nếu có UI, rồi integration nếu chạm nhiều module), xem nó fail đúng lý do, sửa tối thiểu, xem xanh.
3. Chuyển file todo -> inprogress khi bắt đầu, inprogress -> done khi xong. Cập nhật doc/feature.md và doc/task/README.md ngay sau mỗi quyết định.
4. Tuân thủ R3 (hỏi device 1 lần rồi khóa), R4 (gặp quảng cáo che UI thì dừng chờ "done"), R5 (không magic number, tránh late/!, dispose listener/timer).
5. Lặp lại cho tới khi gặp tín hiệu kết thúc bên dưới.
```

### Tín hiệu kết thúc loop

Chỉ dừng loop khi TẤT CẢ đúng:

- Hãy audit lại code changes và chấm điểm trên thang điểm 10 (ghi vào `doc/task/AUDIT_UI-028_<ngày>.md`).
- Bổ sung unit test + widget test + integration test cho mọi case; lớp nào không áp dụng phải ghi lý do `Not applicable` đã review.
- Smoke test lên device đã khóa trong phiên để chứng minh (ghi model, Android, build SHA, thời điểm, ảnh/log); khôi phục đúng state thiết bị.
- `./gradlew testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest` xanh, `git diff --check` sạch, `scripts/check-secrets.sh` sạch.
- Nếu work và điểm > 9/10 thì push code lên `origin/dev` (không commit `.idea`); nếu <= 9 thì sửa tiếp rồi audit lại.
