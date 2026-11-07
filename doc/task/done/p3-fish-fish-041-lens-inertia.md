# FISH-041 — Quán tính tâm lens (spring follow)

| Field | Value |
|---|---|
| Type | exclusive |
| Status | done 2026-11-07 (audit 8,7/10, owner đóng với giới hạn; TECNO KJ7 + Samsung A50s; chưa push) |
| Priority | P3 |
| Evidence | idea |
| Epic | Fisheye Smart |
| Estimate | 5 SP |
| Risk | High |
| Dependencies | FISH-034, FISH-039, FISH-040 |

## Context and evidence

Owner chọn ý tưởng này sau FISH-040 (2026-11-07), brainstorm một câu hỏi mỗi lần. Spec: `docs/superpowers/specs/2026-11-07-fish-041-lens-inertia-design.md`. Owner chọn thực thi inline, không lập plan riêng.

## User story

Là người dùng, tôi muốn lens có cảm giác có khối lượng: hiệu ứng quang học đuổi theo ngón tay thay vì dính cứng.

## Acceptance criteria

- [x] Công tắc riêng `lens_inertia`, mặc định tắt, có trong reset-to-defaults, hàng Settings, 17 locale.
- [x] Chỉ tâm vẽ trễ (warp, DoF, vignette, peaking, hướng sai sắc); hit-test, `mSelectIndex`, mở app, haptic, ring rotate, pager giữ tâm ngón thật.
- [x] Chỉ khi pan thật; ghim/tilt/idle/pinch/reduced-motion không có quán tính; nhả tay snap về ngón trước khi ẩn lens và mở app.
- [x] Không đổi geometry/`LensGridCache`; khi hai tâm trùng thì chi phí bằng hiện tại.
- [x] Đo frame off/on, hơn 1600 frame mỗi lần, không tuyên bố pass khi thiếu số đo hợp lệ. **Đo hợp lệ nhưng KHÔNG đạt sạch**: KJ7 18 cặp mean +0,51 ms (tiêu chí spec +0,5 ms), median +0,18 ms, 6/18 lần bật có ngoại lai > 6,2 ms (0/18 lần tắt) chưa giải thích; A50s 6 cặp on−off ≈ 0. Owner chấp nhận.

## Verification and Definition of Done

- [x] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [x] Widget/UI tests cover visible behavior and lifecycle where applicable.
- [x] Integration tests cover subsystem boundaries where applicable.
- [x] Smoke tests on the locked device (TECNO KJ7 only); record model, Android version, build SHA and timestamp; restore device state.
- [x] No new lint/build failures.
- [ ] A post-change audit record scores the round `> 9.0/10` before push. **Không đạt: audit 8,7/10. Owner quyết định đóng với giới hạn (2026-11-07).**

## Kết quả

Audit: `doc/task/AUDIT_FISH-041_2026-11-07.md` (8,7/10, dưới ngưỡng 9,0; owner đóng với giới hạn). Raw gfxinfo: `doc/task/evidence/fish-041/`. Giới hạn chính: ngoại lai frame trên KJ7 chưa giải thích và chưa chụp được Perfetto A/B (KJ7 rớt adb); owner chưa thử trực tiếp; smoke tự động trên A50s với animator gốc 0 không kích hoạt pan (`LensAnimation` duration=0), gap 0; tạm bật animator 1.0 thì integration 6/6 và smoke 2/2 xanh, rồi khôi phục 0. Hành vi tay chưa được chứng minh; 100 ms là hằng số đoán; bản dịch AI. Commit: `3cdaf706`, `e165606b`, `9dc3c5ad`, `1fef33aa`, `a488a708`, `5780b76d`, `fa975e2a`.
