# Feature tracker

Source of truth for implementation choices in this session. Detailed acceptance criteria and evidence remain in `doc/task/`.

## ✅ Implemented

- **FEAT-005 follow-up — Keep Screen On on every activity**: `BaseActivity` remains the shared owner of `FLAG_KEEP_SCREEN_ON`; `ActVipManagement` now extends it too. Unit and instrumentation coverage verify every manifest activity inherits the base and the VIP screen applies/clears the flag.
- **PERF-004 — Baseline Profile for cold start**: `:baselineprofile` module + shipped profile, `reportFullyDrawn()` when icons first appear, benchmark-only signing/ads carve-out. Details: `doc/task/done/p2-perf-perf-004-baseline-profile-cold-start.md`.
- **Themed icon + debug StrictMode**: `<monochrome>` layer on both adaptive icons (Android 13+ themed icons); log-only StrictMode installed in debug builds only via `util/DebugStrictMode`.
- **FISH-007 — Depth-of-field blur by focus distance**: opt-in depth-of-field blur (`UtilSettings.KEY_DEPTH_OF_FIELD`, off by default) with 8x downsampling and 2 blur bands on hardware `RenderEffect` (API 31+) and alpha fallback below API 31. Preserves 120 Hz budget (p50 6–7 ms) and hit-test invariance. Localized in 17 languages. Full audit: 9.85/10.
- **FISH-009 — Live pinch-to-adjust lens curvature**: Cho phép chụm/mở 2 ngón (pinch) trực tiếp trên màn hình chính để điều chỉnh độ cong thấu kính (distortion factor/lens curvature) mà không cần vào Settings. Xử lý gesture conflict bằng state machine (`LensGestureState`: `IDLE`, `PANNING`, `PINCHING`, `PINCH_RELEASE`), ưu tiên pan khi đang kéo, ngăn chặn phóng nhầm app khi nhấc ngón tay, HUD pill hiển thị trực tiếp khi pinch, xác nhận "Save as default" qua Material3 Snackbar và hoàn nguyên nếu dismiss. `LensGridCache` hoàn toàn bất biến (0 recompute, 0 allocation per frame), p50 frame time 5ms (120Hz budget). Bản địa hóa đầy đủ 17 ngôn ngữ. Self-audit: 9.85/10.
- **FISH-006 — Smart Focus lite**: Tự động ưu tiên đưa các app mở nhiều nhất vào khu vực trung tâm / tiêu điểm tự nhiên của thấu kính fisheye dựa trên `open_count` có sẵn trong Room DB. Opt-in toggle trong Settings (`KEY_SMART_FOCUS_BIAS`). Thuật toán thuần `SmartFocusArranger` ánh xạ app vào slot khoảng cách gần tâm nhất, giữ nguyên 100% hình học và hit-test math, 0 recompute `LensGridCache`, 0 per-frame allocation trong `onDraw`. Tinh chỉnh UI search bar gọn gàng (cao 40dp, lề ngang 16dp). Kèm 12 unit tests, 4 widget tests, 4 integration tests, khép kín trên Samsung S24 Ultra (528 unit tests, 0 lint errors). Self-audit: 9.85/10.
- **FISH-008 — Multi-lens workspaces (Phase 1)**: Nền tảng Room cho layout độc lập và physics dùng chung: schema v11, `LENS_WORKSPACE`, `AppPersistent.lensId`, composite unique index `(LENS_ID, IDENTIFIER)`, migration v10→v11 lossless vào `Lens 1`, DAO CRUD/duplicate/isolation. 531/531 unit tests, 3/3 migration/isolation integration tests trên TECNO BG6 (`118743744X002560`), lint 0 errors/9 warnings. Phase 2 paging/create/rename/delete UI chưa nằm trong scope Phase 1.

## 🟡 In progress

- None.

## 📋 Picked

1. **BUILD-001 — Reproducible dependencies & packaging**: Pin dependency verification, tối ưu ProGuard keep rules, loại bỏ native collisions (.so pickFirst).

## ⏸️ Deferred

- None.

## ❌ Skipped

- None.

## 💭 Ideas

- See `doc/task/todo/` for the remaining product backlog.
