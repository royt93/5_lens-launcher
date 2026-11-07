# FISH-040 — Sai sắc (chromatic aberration) ở mép lens

| Field | Value |
|---|---|
| Type | exclusive |
| Status | inprogress |
| Priority | P3 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 5 SP |
| Risk | High |
| Dependencies | FISH-034 |

## Context and evidence

Tách ra từ `FISH-034` (owner, 2026-11-06): story đó chỉ làm vignette. Sai sắc nhân 2–3 lần chi phí vẽ icon ở mép; bài học `FISH-007` là layer full-resolution đẩy p50 kéo lens từ 6 ms lên 27 ms trên Pixel 7 Pro. Số đo hiện tại trên TECNO KJ7: p50/p90 6 ms với vignette bật/tắt (xem `doc/task/AUDIT_FISH-034_2026-11-06.md`).

## User story

Là người dùng, tôi muốn mép lens có cảm giác quang học thật hơn ngoài việc tối dần.

## Acceptance criteria

- [ ] Opt-in, mặc định tắt, có trong reset-to-defaults; gate reduced-motion như `FISH-007`/`FISH-034`.
- [ ] Không đổi geometry/hit-test/`LensGridCache`.
- [ ] Đo frame 120 Hz trước/sau; nếu vượt ngân sách thì downsample hoặc giảm số icon áp dụng, không tuyên bố pass khi thiếu số đo hợp lệ.
- [ ] Phương án rõ cho API < 31 (không có `RenderEffect`).

## Implementation notes

Cần chọn cơ chế (vẽ icon nhiều lần lệch pha vs `RenderEffect`/`ColorMatrix`) bằng brainstorming; chưa có thiết kế. Phải tương thích với `LensVignette` (alpha) và DoF.

## Verification and Definition of Done

- [ ] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [ ] Widget/UI tests cover visible behavior and lifecycle where applicable.
- [ ] Integration tests cover subsystem boundaries where applicable.
- [ ] Smoke tests on the locked device; record model, Android version, build SHA and timestamp; restore device state.
- [ ] No new lint/build failures.
- [ ] A post-change audit record scores the round `> 9.0/10` before push.
- [ ] Evidence and status are updated before moving this file to `done`.
