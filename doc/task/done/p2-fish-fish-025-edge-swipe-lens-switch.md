# FISH-025 — Vuốt cạnh chuyển nhanh Lens (Edge swipe lens switcher)

| Field | Value |
|---|---|
| Status | done |
| Priority | P2 |
| Owner decision | 2026-10-17, implementation plan approved |

## Scope

- Cho phép chuyển đổi tức thì giữa các không gian Lens trên màn hình chính bằng cử chỉ vuốt ngang từ mép màn hình (Edge Swipe).
- Bắt đầu vuốt tại dải hẹp bên trong mép màn hình (mép trái: `0 <= downX <= 32dp`; mép phải: `width - 32dp <= downX <= width`), ngoài vùng search kéo xuống 20% trên cùng của FISH-016.
- Ngưỡng cử chỉ ngang chiếm ưu thế: `|dx| > touchSlop * 2` và `|dx| > |dy| * 1.5f`.
- Vuốt mép phải sang trái chuyển sang Lens kế tiếp (`currentItem + 1`), vuốt mép trái sang phải chuyển về Lens liền trước (`currentItem - 1`), không wrap-around ở biên đầu/cuối.
- Khi chỉ có 1 Lens duy nhất: bỏ qua cử chỉ, không giật màn hình hay cản trở pan fisheye.
- Chế độ cấu hình `EdgeSwipeMode`: `OFF`, `BOTH` (mặc định), `RIGHT_ONLY`, `LEFT_ONLY`. Lưu trong `UtilSettings.KEY_EDGE_SWIPE_LENS_SWITCH` ("edge_swipe_lens_switch").
- UI cài đặt trong `FrmSettings`: Hàng cài đặt "Vuốt cạnh chuyển Lens" với icon `ic_swap_horiz_24dp`, mở single-choice dialog 4 lựa chọn, subtitle cập nhật theo lựa chọn hiện tại.
- Phản hồi xúc giác (Haptic): rung nhẹ khi chuyển lens thành công.
- Bản địa hóa 17 ngôn ngữ đầy đủ.
- Tương thích Clean Mode: cử chỉ vẫn hoạt động bình thường kể cả khi đã ẩn toàn bộ thanh chrome.

## Verification

- [x] Pure gesture policy unit test: `EdgeSwipePolicyTest` (8 tests pass bao phủ: left/right swipe, center swipe, vertical/diagonal drag, threshold, mode filter, top search exclusion, bounds/zero size).
- [x] Settings unit test: `UtilSettingsEdgeSwipeTest` (3 tests pass: default BOTH, save/retrieve all 4 modes, corrupt fallback).
- [x] I18n strings test: `EdgeSwipeStringsTest` (1 test pass: đủ 17 locales cho 5 chuỗi mới).
- [x] Full JVM unit regression: 906/906 tests pass, 0 failures.
- [x] Android Lint: 0 errors, 27 warnings (không phát sinh warning mới).
- [x] Targeted instrumentation tests trên TECNO KJ7 `115333744A005844`:
  - `ActHomeEdgeSwipeWidgetTest`: 1 test pass (vuốt mép phải sang trái sang page 1, vuốt mép trái sang phải về page 0).
  - `FrmSettingsEdgeSwipeWidgetTest`: 1 test pass (subtitle mặc định Both edges, click mở dialog 4 lựa chọn, chọn Right edge only lưu settings và cập nhật subtitle).
  - Regression tests liên quan: `LensViewPagerGestureIntegrationTest` (3/3 pass), `LensViewFocusHaloWidgetTest` (3/3 pass), `FrmSettingsFocusHaloWidgetTest` (4/4 pass), `ActHomeLensManagementWidgetTest#disabledDeleteIconKeepsSameTintAsEnabledMenuIcons` (pass).
  - Tổng targeted tests: **13 tests pass trên KJ7**.
- [x] Smoke test tay & trực quan trên TECNO KJ7:
  - Hàng "Vuốt cạnh chuyển Lens" hiển thị subtitle "Both edges" mặc định.
  - Chạm mở dialog single-choice hiển thị đủ 4 tùy chọn (Off, Both edges, Right edge only, Left edge only) với radio check đúng.
  - Không có quảng cáo che màn hình.
  - Đã vá triệt để hai test widget để tự động sao lưu và khôi phục DB/prefs, đảm bảo DB Room trên KJ7 sau test trở về đúng 1 lens default sạch sẽ.
- [x] Secret scan: `bash scripts/check-secrets.sh` exit 0.
- [x] Diff check: `git diff --check` sạch.
- [x] Audit tự chấm: **9.75/10** (>9.0); xem `doc/task/AUDIT_FISH-025_2026-10-17.md`.
- [x] Đã hoàn tất commit và push theo owner approval.
