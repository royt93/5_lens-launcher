# UI-027 — Overlay quảng cáo dùng colorWhite cứng, tương phản 1.69:1 ở dark/dynamic color

| Field | Value |
|---|---|
| Type | fix |
| Status | in progress (TDD, 2026-11-14) |
| Priority | P2 |
| Evidence | confirmed |
| Epic | Accessibility |
| Estimate | 1 SP |
| Risk | Low |
| Dependencies | None |

## Context and evidence

`res/layout/act_settings.xml:136` nền `?attr/colorPrimary`; `:160` `indeterminateTint="@color/colorWhite"`, `:167` `textColor="@color/colorWhite"`. `values-night/colors.xml:4` `colorPrimary=#FFB3B8` còn `colorWhite` không có override night. Chữ `ads_action_disclosure` gần như vô hình ở dark mode và với `DynamicColors` (`RApplication.java:139`). Đây là text công bố quảng cáo (a11y + policy). UI-005 đổi nền sang `?attr` nhưng bỏ sót hai thuộc tính foreground.

## User story

Là người dùng dark mode, tôi muốn đọc được dòng cảnh báo có quảng cáo.

## Acceptance criteria

- [ ] Chữ và spinner dùng `?attr/colorOnPrimary`.
- [ ] Test widget đo tương phản >= 4.5:1 ở light, dark, và một bảng dynamic color.
- [ ] Không đổi chuỗi hay hành vi quảng cáo.

## Implementation notes

Hai thuộc tính trong một file layout.

## Kết quả

Xem `doc/task/AUDIT_UI-027_2026-11-14.md`. Hai màu foreground dùng `?attr/colorOnPrimary`, overlay nền/hành vi/chuỗi không đổi. Contrast light 6,64:1, night 7,74:1. Device widget RED 3/3 tại 1,6946:1 trên build cũ; GREEN 3/3 ×10 trên A50s. Audit 9,1/10 sau khi kiểm 16 palette sinh từ Material (dynamic color thật chưa chạy trên máy, A50s là Android 11).
