# FISH-046 — Hover Magnify

| Field | Value |
|---|---|
| Type | exclusive |
| Status | done 2026-11-09, audit 8,8/10 (dưới ngưỡng 9,0, owner đóng với giới hạn) |
| Priority | P3 |
| Epic | Fisheye Smart |
| Estimate | 3 SP |
| Risk | Medium |
| Dependencies | FISH-044 (bộ đo tốc độ ngón) |

Spec: `docs/superpowers/specs/2026-11-09-fish-046-hover-magnify-design.md`. Audit: `doc/task/AUDIT_FISH-046_2026-11-09.md`.

## Acceptance criteria

- [x] Công tắc `focus_magnify` mặc định tắt, trong reset-to-defaults, Settings, 17 locale.
- [x] Icon được chọn phóng 1,2x sau khi ngón yên (< 15 dp/s) trên cùng icon 200 ms, nội suy 150 ms; về 1,0 ngay khi đổi icon hoặc ngón nhanh lên.
- [x] Chỉ phóng hình vẽ; hit-test và chọn icon dùng rect chưa phóng; halo và nhãn tên đi theo icon đã phóng.
- [x] Chỉ khi pan thật và reduced-motion tắt.
- [x] Probe giá trị tác động trước khi code (icon dưới ngón ~58 dp, +20% = +11,6 dp).
- [x] Reachability bằng DOWN/MOVE thật trên Pixel (không hook tốc độ/clock/pan) và test tự vẽ lại không gọi `draw()`.
- [x] Mutation: 17 loại đều bị bắt (năm lần lọt đã được gia cố test).
- [x] Ảnh pixel thật (halo 264 → 310 px) và Perfetto FrameTimeline (không jank; bật 28,5 so với tắt 10,3 fps khi giữ ngón).
- [x] Owner thử tay 2026-11-09 trên Pixel: "cảm giác ổn", không đổi 200 ms / 15 dp/s / 1,2 / 150 ms (nhận xét định tính, không kèm số đo).
- [ ] Giảm số khung vẽ trong giai đoạn chờ 200 ms (chưa làm; chi phí chỉ kéo dài ~350 ms mỗi lần giữ ngón).
