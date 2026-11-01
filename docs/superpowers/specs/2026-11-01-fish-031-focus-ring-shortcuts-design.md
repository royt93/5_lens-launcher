# FISH-031 — Vòng tiêu điểm: giữ icon hiện shortcut ngay trong vòng

Date: 2026-11-01
Status: Owner-approved design

## Context

`LensView` đã có state machine long-press (`mLongPressArmed`, `mLongPressTriggered`, `mHoldIndex`, `mDragStarted`), halo tiêu điểm FISH-024, kéo icon sang lens khác (FISH-038b) và menu app qua `showQuickActionsMenu`. `AppShortcutsProvider` (SEARCH-003) đã đọc shortcut do chính app công bố qua `LauncherApps`, tối đa `MAX_SHORTCUTS = 3`, trả danh sách rỗng khi app không phải launcher mặc định. FISH-031 thêm một lớp thị giác: giữ icon thì lens phóng thêm một bậc và hiện chip shortcut ngay quanh icon, không thay menu app cũ.

Phạm vi loại trừ do owner chốt: Ads/IAP/monetization và mọi việc TalkBack mới (A11Y-002/A11Y-003).

## Hành vi cho người dùng

- Giữ icon hết `ViewConfiguration.getLongPressTimeout()` mà không di chuyển quá touch slop: icon được phóng thêm một hệ số hằng có tên và tối đa 3 chip shortcut hiện cung quanh icon, vẽ trực tiếp trên `LensView`.
- Ngón vẫn đang giữ: trượt vào chip thì chip sáng lên; thả trên chip thì chạy shortcut bằng `AppShortcutsProvider.startShortcut` với `sourceBounds` là rect chip.
- Thả ngoài chip, hoặc app không có shortcut, hoặc dữ liệu chưa nạp kịp: giữ nguyên hành vi hiện có. Một lens: menu app mở ngay lúc hết timeout nếu app không có shortcut (xem mục luồng dữ liệu cho trường hợp có shortcut). Nhiều lens: hold rồi thả không dịch chuyển thì mở menu app khi thả; di chuyển quá slop ngoài chip thì bắt đầu kéo sang lens khác như cũ.
- Reduced-motion (`LensPhysicsPolicy.shouldReduceLensMotion`): bỏ bước phóng thêm, chip hiện ngay không animation. Haptic giữ quy tắc hiện tại (`mGestureReduceMotion != true`).
- Clean Mode bật: không hiện vòng chip (giống cổng của halo FISH-024).
- Đường thay thế cho người không thao tác chạm là menu app cũ. Không thêm hành động TalkBack mới; ghi rõ trong audit là ngoài phạm vi theo quyết định owner.

## Thành phần

- `views/FocusRingLayout.kt` (hàm thuần): từ tâm icon, bán kính icon sau phóng, số chip, kích thước chip và biên view, tính tâm và rect từng chip, đẩy chip vào trong biên khi icon sát mép, và `chipAt(x, y)` trả chỉ số chip hoặc -1. Hằng số (khoảng cách chip, kích thước chip) lấy từ `dimens`, không magic number.
- `views/FocusRingPolicy.kt` (hàm thuần): cổng hiển thị (`enabled`, `cleanMode`, có shortcut), chọn chip theo vị trí ngón, và quyết định khi thả: `RUN_SHORTCUT(index)`, `OPEN_APP_MENU`, `NONE`. Gồm cả quy tắc xung đột kéo: ngón nằm trên chip thì không bắt đầu drag; ra ngoài chip và vượt touch slop thì drag như cũ.
- `views/LensView.kt`: thêm trạng thái vòng vào state machine long-press, nạp shortcut bất đồng bộ khi hết timeout, vẽ chip cùng đường vẽ icon, hit-test bằng cùng rect đã vẽ. Dọn job nạp và trạng thái ở `ACTION_UP`, `ACTION_CANCEL`, khi detach view và khi đổi lens.
- `search/AppShortcutsProvider.kt`: dùng nguyên `shortcutsFor`/`startShortcut`, không đổi API.
- Dimens/hằng: hệ số phóng thêm, bán kính cung, kích thước chip, đặt tên rõ; không thêm key preference và không thêm Room.

## Luồng dữ liệu

1. `ACTION_DOWN` trên icon: arm long-press như cũ.
2. Hết timeout, `shouldTriggerLongPress` đúng: ghi `mHoldIndex`, phát haptic, bắt đầu nạp shortcut cho gói của icon (ngoài main thread; lỗi hoặc `SecurityException` cho danh sách rỗng).
3. Nạp xong khi ngón còn giữ và cổng cho phép: bật vòng, tính layout, `invalidate()`.
4. `ACTION_MOVE`: cập nhật chip đang được chọn bằng `chipAt`; `maybeStartDrag` chỉ chạy khi ngón không ở trên chip.
5. `ACTION_UP`: `FocusRingPolicy` quyết định chạy shortcut, mở menu app hoặc không làm gì; hủy job nạp và xóa trạng thái vòng.
6. `ACTION_CANCEL`, detach, đổi lens: hủy job, xóa trạng thái, không chạy gì.

**Trường hợp một lens (đã chốt):** `LensDragPolicy.shouldOpenMenuOnTimeout(false)` hiện mở menu app ngay lúc hết timeout, nên vòng chip không thể cùng tồn tại với menu đó. Quy tắc: khi app có shortcut thì vòng chip hiện thay cho việc mở menu lúc timeout, và menu app mở khi thả ngoài chip (cùng nhánh với trường hợp nhiều lens). Khi app không có shortcut, hoặc dữ liệu chưa nạp xong lúc timeout, menu mở ngay lúc timeout như cũ. Nhờ đó người dùng một lens không đổi hành vi với app không có shortcut và không bao giờ thấy hai lớp chồng nhau.

## Xử lý lỗi và giới hạn

- App không phải launcher mặc định: `shortcutsFor` trả rỗng, không hiện vòng, hành vi giữ nguyên như hiện tại (không báo lỗi).
- `startShortcut` trả false (shortcut không còn hợp lệ): không crash, không đổi state, hiện `Toast` với `R.string.error_app_not_found`, đúng như SEARCH-003 (`SearchResultAdapter`); không thêm chuỗi mới.
- Không đụng pan, pinch, vuốt cạnh, kéo sang lens khác và các test hiện có của chúng.
- Không thêm dependency, không thêm quyền mới.

## Xác minh

- TDD đỏ → xanh. Unit: `FocusRingLayoutTest` (cung, biên, icon sát mép, hit-test trong/ngoài), `FocusRingPolicyTest` (cổng, mọi nhánh thả, xung đột kéo, reduced-motion, Clean Mode, không có shortcut).
- Widget/integration trên `LensView` thật: giữ rồi trượt vào chip rồi thả chạy shortcut (qua listener có thể giả), thả ngoài chip mở menu cũ, hủy giữ không rò job, pan/pinch/kéo sang lens khác không đổi, rect vẽ bằng rect hit-test.
- Đo frame: `dumpsys gfxinfo` (số frame, tỉ lệ jank, p90/p95) trước và sau thay đổi trên cùng thiết bị, cùng kịch bản, ghi số liệu vào audit.
- Gate: `testDevDebugUnitTest lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`, `git diff --check`, `scripts/check-secrets.sh`.
- Smoke trên thiết bị đã khóa theo chính sách hiện hành (TECNO KJ7 `115333744A005844`): backup trước, xác nhận hash APK, dừng nếu quảng cáo hoặc consent che màn hình, khôi phục từng file sau test. Smoke shortcut thật cần app là launcher mặc định; đổi vai trò launcher là thay đổi trạng thái máy, phải hỏi lại owner trước khi làm và trả về đúng như cũ.
- Audit `doc/task/AUDIT_FISH-031_<ngày>.md`, điểm phải trên 9,0 mới đủ ngưỡng push; không push khi chưa có yêu cầu rõ của owner.

## Đánh đổi đã chấp nhận

- Chip chỉ phục vụ thao tác chạm; người không dùng cảm ứng dùng menu app cũ.
- Vòng phụ thuộc việc app là launcher mặc định mới có dữ liệu; nếu không, người dùng thấy hành vi cũ.
- Hệ số phóng thêm và bán kính cung là hằng thiết kế, không cho người dùng chỉnh (YAGNI).
