# FISH-024 — Vòng sáng tiêu điểm thấu kính (Focus accent halo / ring)

| Field | Value |
|---|---|
| Status | done |
| Priority | P2 |
| Owner decision | 2026-10-17, implementation plan approved |

## Scope

- Vòng tròn viền mảnh (Accent Ring) bao quanh icon app đang được phóng to tại tiêu điểm thấu kính fisheye (`LensView`), đồng tâm với icon app, bán kính thích ứng theo kích thước phóng to.
- Màu viền lấy từ `UtilSettings.KEY_HIGHLIGHT_COLOR` (đồng bộ màu người dùng chọn hoặc Material You theme).
- Độ trong suốt (alpha) đồng bộ theo animation phóng to/thu nhỏ của thấu kính.
- Cài đặt Bật/Tắt trong `FrmSettings` (`UtilSettings.KEY_SHOW_FOCUS_HALO`), mặc định `true`. Khôi phục về `true` khi reset-to-defaults.
- Clean Mode: tự động ẩn vòng sáng khi `KEY_CLEAN_LENS_MODE` bật (`LensView.shouldDrawFocusHalo(enabled, cleanMode)`).
- 0 allocation trên hot path `onDraw()`.
- Bản địa hóa 17 locales đầy đủ.

## Verification

- [x] Pure policy unit test: `LensViewFocusHaloPolicyTest` (3 tests pass).
- [x] Settings unit test: `UtilSettingsFocusHaloTest` (2 tests pass).
- [x] I18n test: `FocusHaloStringsTest` (1 test pass, đủ 17 locales).
- [x] Full JVM unit regression: 894/894 pass, 0 failures.
- [x] Android Lint: 0 errors, 27 warnings (không phát sinh warning mới).
- [x] Secret scan: `bash scripts/check-secrets.sh` exit 0.
- [x] Diff check: `git diff --check` sạch.
- [x] Instrumentation widget test đã viết: `LensViewFocusHaloWidgetTest` (3 tests: default on, setting off, clean mode suppress) và `FrmSettingsFocusHaloWidgetTest` (4 tests: default on, saved false, toggle persist, reset-to-defaults restore).
- [x] Instrumentation + smoke test tay trên TECNO KJ7 `115333744A005844`: 28 targeted tests pass, Settings toggle và live hover bitmap xác nhận halo, không có quảng cáo che UI.
- [x] Audit tự chấm: **9.75/10** (>9.0); xem `doc/task/AUDIT_FISH-024_2026-10-17.md`.
- [x] Commit và push hoàn tất theo owner approval.
