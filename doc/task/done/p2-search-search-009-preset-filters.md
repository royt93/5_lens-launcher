# SEARCH-009 — Bộ lọc tìm kiếm lưu sẵn (Preset Search Filters)

| Field | Value |
|---|---|
| Status | done |
| Priority | P2 |
| Owner decision | 2026-10-17, implementation plan executed & audited |

## Scope

- Thêm thanh Chip cuộn ngang Material 3 bên dưới ô tìm kiếm: Tất cả (`ALL`), Lens này (`THIS_LENS`), Yêu thích (`FAVORITES`), Đã ẩn (`HIDDEN`).
- Pure model `SearchFilterPreset` và pure engine `SearchFilterEngine`.
- Lưu trữ bộ lọc đã chọn trong `UtilSettings.KEY_SEARCH_FILTER_PRESET`, khôi phục checked state khi mở lại SearchView.
- 17 locale đầy đủ bản dịch cho 4 nhãn bộ lọc.
- 100% offline, 0 network, không thêm thư viện ngoài.

## Verification

- [x] Unit test: `SearchFilterPresetTest` (2 tests pass).
- [x] Unit test: `SearchFilterEngineTest` (6 tests pass).
- [x] Unit test: `UtilSettingsSearchFilterTest` (3 tests pass).
- [x] Unit test: `SearchFilterStringsTest` (1 test pass, đủ 17 locales).
- [x] Full JVM unit regression: 888/888 pass, 0 failures.
- [x] Android Lint: 0 errors, 27 warnings (không phát sinh warning mới).
- [x] Device Widget test: `ActHomeSearchFilterWidgetTest` pass trên TECNO BG6 `118743744X002560`.
- [x] Targeted device regression: 44/44 tests pass trên TECNO BG6.
- [x] On-device smoke test tay: thanh Chip hiển thị chuẩn Material 3, chuyển đổi lọc tức thì, lưu & khôi phục checked state qua đóng/mở search, không có ad che UI.
- [x] Audit tự chấm: **9.70/10** (đạt gate >9.0); xem `doc/task/AUDIT_SEARCH-009_2026-10-17.md`.
- [x] Đã hoàn tất commit `0208728b` và push lên `origin/dev`.
