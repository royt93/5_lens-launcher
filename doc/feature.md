# Feature tracker

Source of truth for implementation choices in this session. Detailed acceptance criteria and evidence remain in `doc/task/`.

## ✅ Implemented

- **FEAT-005 follow-up — Keep Screen On on every activity**: `BaseActivity` remains the shared owner of `FLAG_KEEP_SCREEN_ON`; `ActVipManagement` now extends it too. Unit and instrumentation coverage verify every manifest activity inherits the base and the VIP screen applies/clears the flag.
- **PERF-004 — Baseline Profile for cold start**: `:baselineprofile` module + shipped profile, `reportFullyDrawn()` when icons first appear, benchmark-only signing/ads carve-out. Details: `doc/task/done/p2-perf-perf-004-baseline-profile-cold-start.md`.
- **Themed icon + debug StrictMode**: `<monochrome>` layer on both adaptive icons (Android 13+ themed icons); log-only StrictMode installed in debug builds only via `util/DebugStrictMode`.
- **FISH-007 — Depth-of-field blur by focus distance**: opt-in depth-of-field blur (`UtilSettings.KEY_DEPTH_OF_FIELD`, off by default) with 8x downsampling and 2 blur bands on hardware `RenderEffect` (API 31+) and alpha fallback below API 31. Preserves 120 Hz budget (p50 6–7 ms) and hit-test invariance. Localized in 17 languages. Full audit: 9.85/10.
- **FISH-009 — Live pinch-to-adjust lens curvature**: Cho phép chụm/mở 2 ngón (pinch) trực tiếp trên màn hình chính để điều chỉnh độ cong thấu kính (distortion factor/lens curvature) mà không cần vào Settings. Xử lý gesture conflict bằng state machine (`LensGestureState`: `IDLE`, `PANNING`, `PINCHING`, `PINCH_RELEASE`), ưu tiên pan khi đang kéo, ngăn chặn phóng nhầm app khi nhấc ngón tay, HUD pill hiển thị trực tiếp khi pinch, xác nhận "Save as default" qua Material3 Snackbar và hoàn nguyên nếu dismiss. `LensGridCache` hoàn toàn bất biến (0 recompute, 0 allocation per frame), p50 frame time 5ms (120Hz budget). Bản địa hóa đầy đủ 17 ngôn ngữ. Self-audit: 9.85/10.
- **FISH-006 — Smart Focus lite**: Tự động ưu tiên đưa các app mở nhiều nhất vào khu vực trung tâm / tiêu điểm tự nhiên của thấu kính fisheye dựa trên `open_count` có sẵn trong Room DB. Opt-in toggle trong Settings (`KEY_SMART_FOCUS_BIAS`). Thuật toán thuần `SmartFocusArranger` ánh xạ app vào slot khoảng cách gần tâm nhất, giữ nguyên 100% hình học và hit-test math, 0 recompute `LensGridCache`, 0 per-frame allocation trong `onDraw`. Tinh chỉnh UI search bar gọn gàng (cao 40dp, lề ngang 16dp). Kèm 12 unit tests, 4 widget tests, 4 integration tests, khép kín trên Samsung S24 Ultra (528 unit tests, 0 lint errors). Self-audit: 9.85/10.
- **FISH-008 — Multi-lens workspaces**: Hỗ trợ nhiều không gian lens độc lập trên màn hình chính ("Lens 1", "Work", "Personal"). Đã bản địa hóa đầy đủ 9 chuỗi quản lý lens và Smart Focus sang toàn bộ 16 locale; unit test khóa độ phủ, placeholder `%1$s`/`%1$d`, và ngăn tái thêm `MissingTranslation`. Smoke menu + add/delete dialog tiếng Việt trên TECNO BG6. Schema v11 Room tách biệt layout app (thứ tự, ẩn/hiện, thư mục, ghim) theo lensId; physics và open_count dùng chung. Vuốt ngang toàn màn hình qua ViewPager2 với indicator chấm tròn tự ẩn khi chỉ có 1 lens. Menu quản lý (Thêm/Đổi tên/Xóa) qua long-press indicator, sao chép layout từ lens đang mở. Giải quyết xung đột gesture giữa LensView (pan/pinch) và ViewPager2 bằng phân định tỷ lệ dịch chuyển ngang 2:1 (`isHorizontalSwipeIntent`). Tự động khôi phục lens đang mở sau khi xoay/recreate màn hình và đồng bộ khi xóa lens active. 540 unit tests, 9 tests instrumentation (widget + gesture) trên TECNO KJ7. Self-audit: 9.4/10.
  - **Phase 3 (27/09/2026)**: mỗi lens có độ cong (`distortion factor`) và Smart Focus riêng, lưu bằng key hậu tố `<key>_<lensId>` trong SharedPreferences, không đổi schema Room. Lens chưa chỉnh thì kế thừa giá trị chung; lens mặc định ánh xạ về đúng key cũ nên cài đặt 1 lens không thay đổi hành vi. Slider độ cong trong Settings và switch Smart Focus ghi vào lens đang active; thêm mục bật/tắt Smart Focus trong menu quản lý lens. Tạo lens mới sao chép setting từ lens nguồn, xóa lens dọn key tương ứng. Smoke trên TECNO KJ7 phát hiện và sửa 3 lỗi thật: menu quản lý lens không có lối vào khi chỉ có 1 lens (thêm long-press vùng trống), popup bị cắt còn 1 dòng do neo vào `LensView` toàn màn hình, và lưới trống sau khi xoay máy. Vòng audit sau đó gỡ 2 lời gọi thừa (`applyAppsToSelectedLensView` — đã kiểm chứng bằng cách gỡ từng nhánh, chỉ nhánh khớp lens active là cần), sửa rò rỉ dialog/popup chưa giải phóng ở `onDestroy`, và bổ sung `FrmLensPerLensWidgetTest`. Mọi fix đều kiểm chứng bằng mutation (gỡ fix → test fail). 559 unit tests, 68 instrumentation tests, lint 0 error / 9 warning.
- **BUILD-001 — Reproducible dependencies & packaging**: Pin dependency verification (`gradle/verification-metadata.xml` sha256), loại bỏ wildcard `pickFirsts += ['**/*.so']`, tối ưu ProGuard keep rules (loại bỏ SugarORM, kotlin/lifecycle keep-all dư thừa, verified R8 minification). 531/531 unit tests, 3/3 device integration tests, Tecno BG6 smoke pass.

## 🟡 In progress

- None.

## 📋 Picked

- None.

## ⏸️ Deferred

- None.

## ❌ Skipped

- None.

## 💭 Ideas

- See `doc/task/todo/` for the remaining product backlog.
