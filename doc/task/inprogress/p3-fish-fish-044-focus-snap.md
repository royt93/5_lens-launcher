# FISH-044 — Focus snap-to-icon

| Field | Value |
|---|---|
| Type | exclusive |
| Status | implemented 2026-11-08, audit 8,7/10 (dưới ngưỡng 9,0), chờ owner quyết định đóng |
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
- [ ] Test runtime tổ hợp FISH-041 + FISH-044 cùng bật.
- [ ] Owner thử tay và tinh chỉnh hằng số.
