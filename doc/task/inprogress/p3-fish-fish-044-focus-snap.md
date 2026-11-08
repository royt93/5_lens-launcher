# FISH-044 — Focus snap-to-icon

| Field | Value |
|---|---|
| Type | exclusive |
| Status | implemented 2026-11-08, audit 8,7/10 (dưới ngưỡng 9,0), chờ owner |
| Priority | P3 |
| Epic | Fisheye Smart |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | FISH-041, FISH-042 |

Spec: `docs/superpowers/specs/2026-11-08-fish-044-focus-snap-design.md`. Audit: `doc/task/AUDIT_FISH-044_2026-11-08.md`.

## Acceptance criteria

- [x] Công tắc `focus_snap` mặc định tắt, trong reset-to-defaults, hàng Settings, 17 locale.
- [x] Chỉ kéo tâm quang học (≤ 16 dp, gain tuyến tính về 0 tại 120 dp/s); hit-test, chọn icon, mở app giữ tâm ngón.
- [x] Chỉ khi pan thật và reduced-motion tắt.
- [x] Không cấp phát mỗi frame (static guard); unit 1689/1689, lint 0/15.
- [x] Reachability bằng DOWN/MOVE/UP thật trên KJ7; mutation 27/27 bị bắt; hồi quy 26 lớp OK.
- [x] Test runtime tổ hợp FISH-041 + FISH-044 cùng bật (đã bổ sung, mutation bắt bằng test runtime).
- [x] Đo vi mô CPU drawGrid trên KJ7: +48 µs cho 120 ô (canvas trống, không gồm render).
- [x] Đã đo 10 cặp chuẩn xen kẽ đảo chiều (r21..30, 155.600 frame): median p50 on = off = 6,0 ms (7/10 lần on = 6 ms); delta mean p50 = +0,6 ms; delta mean p90 = +0,5 ms; jank 0,005% cả hai. Đo vi mô CPU: +0,048 ms. Chi phí snap là rất nhỏ (~0,5 ms ở p90), không gây giật.
- [ ] Owner thử tay và tinh chỉnh hằng số.
