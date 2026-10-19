# UI-028 — ActHome dùng một field lensDialog cho 5 dialog; vài dialog không dismiss khi destroy

| Field | Value |
|---|---|
| Type | fix |
| Status | todo |
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

- [ ] Mỗi lần mở dialog mới dismiss cái cũ (một hàm dùng chung).
- [ ] `onDestroy`/`onDestroyView` dismiss mọi dialog đang giữ.
- [ ] Widget test: mở dialog, `recreate()`, không còn window leak (kiểm `WindowManager`/log StrictMode).
- [ ] Giữ nguyên bảo toàn state UI-023.

## Implementation notes

Gộp FIX-3 và test-gap #3 của audit ui. Đề xuất helper `showSingleDialog` dùng chung.

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
