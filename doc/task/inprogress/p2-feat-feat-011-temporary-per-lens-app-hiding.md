# FEAT-011 — Tạm ẩn app riêng theo lens

| Field | Value |
|---|---|
| Status | inprogress |
| Priority | P2 |
| Owner decision | 2026-10-16, implementation plan approved |

## Scope

- Hidden component IDs độc lập theo lens, không đổi global visibility, membership ALL/SELECTED, favorites hoặc layout.
- Chỉ lọc lưới fisheye; search và Apps/list mode giữ nguyên.
- Menu app có “Ẩn khỏi lens này”; menu lens mở danh sách chọn nhiều app để khôi phục. Hủy không lưu draft.
- Cho phép ẩn hết, empty-state CTA luôn cho khôi phục kể cả Clean mode; không che search hoặc lens khác.
- Giữ hidden IDs qua đổi scope, remove/re-add selection, gỡ/cài lại app. Giữ orphan IDs trong backup/import.
- Backup v2 thêm `temporarilyHiddenAppIds` tùy chọn, default empty cho file cũ; strict type/blank/duplicate validation và rollback preferences.
- Duplicate lens copy hidden IDs; delete lens dọn key; không thêm dependency hay Room migration.

## Verification

- [x] Filter RED 2 assertions, GREEN 10 tests.
- [x] Settings isolation/duplicate/delete/workspace rollback RED, GREEN; follow-up default-lens cleanup RED, GREEN.
- [x] JSON hidden IDs round-trip/legacy/adversarial RED, GREEN.
- [x] Export/import JSON, orphan IDs, legacy v2, v1 preservation và preferences rollback integration.
- [x] UI hide/TalkBack, multi-select restore, Cancel, empty recovery, search, Clean mode và recreate: targeted BG6 tests.
- [x] Full JVM unit suite 876/876, lint 0 errors / 27 warnings; 68 chuỗi/17 locale XML hợp lệ, secret scan bằng index tạm và diff check sạch.
- [x] TECNO BG6 `118743744X002560`: ẩn Chrome → lens menu → tick trong dialog có icon/checkbox → Restore → Chrome trở lại grid; không thấy quảng cáo. Không xóa data/đổi default launcher.
- [x] Full instrumentation BG6: OK (613 tests); coverage cuối 67/67 pass, bao gồm 9 FEAT-011 tests. Số không cộng vì test trùng.
- [x] Smoke tay bổ sung BG6: ẩn hết/Clean mode và CTA restore; vuốt lens gốc 54 app không bị ảnh hưởng; restore đúng một app; search tìm app đang hidden; SAF export JSON có 2 hidden IDs → khôi phục app để mutate → SAF import phục hồi hidden IDs đúng. Đã tắt Clean mode, xóa lens smoke tự tạo, giữ file export evidence.
- [x] Review code và fix 4 issue: orphan CTA guard, default-lens cleanup, search transition CTA hide, menu count test alignment.
- [x] Audit tự chấm: **9.60/10** (đạt gate push >9.0); xem `doc/task/AUDIT_FEAT-011_2026-10-17.md`.
- [ ] Chờ chỉ thị owner về commit và push; giữ inprogress.
