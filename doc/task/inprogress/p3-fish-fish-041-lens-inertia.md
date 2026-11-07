# FISH-041 — Quán tính tâm lens (spring follow)

| Field | Value |
|---|---|
| Type | exclusive |
| Status | inprogress |
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

- [ ] Công tắc riêng `lens_inertia`, mặc định tắt, có trong reset-to-defaults, hàng Settings, 17 locale.
- [ ] Chỉ tâm vẽ trễ (warp, DoF, vignette, peaking, hướng sai sắc); hit-test, `mSelectIndex`, mở app, haptic, ring rotate, pager giữ tâm ngón thật.
- [ ] Chỉ khi pan thật; ghim/tilt/idle/pinch/reduced-motion không có quán tính; nhả tay snap về ngón trước khi ẩn lens và mở app.
- [ ] Không đổi geometry/`LensGridCache`; khi hai tâm trùng thì chi phí bằng hiện tại.
- [ ] Đo frame trên TECNO off/on, 3 lần mỗi trạng thái, hơn 1600 frame; không tuyên bố pass khi thiếu số đo hợp lệ.

## Verification and Definition of Done

- [ ] Unit tests cover pure logic, state transitions and failure branches (test đỏ trước, xanh sau sửa).
- [ ] Widget/UI tests cover visible behavior and lifecycle where applicable.
- [ ] Integration tests cover subsystem boundaries where applicable.
- [ ] Smoke tests on the locked device (TECNO KJ7 only); record model, Android version, build SHA and timestamp; restore device state.
- [ ] No new lint/build failures.
- [ ] A post-change audit record scores the round `> 9.0/10` before push.

## Trạng thái (2026-11-07)

Code, test, mutation và smoke xong; **chưa đóng**: audit 8,7/10 (< 9,0): trên 18 cặp frame budget mean +0,51 ms (tiêu chí spec +0,5 ms), median +0,18 ms, 6/18 lần bật có ngoại lai > 6,2 ms (0/18 lần tắt) chưa giải thích; chẩn đoán cho thấy phương án lùi của spec không nhắm đúng nguồn chi phí nên không áp. Trên A50s (owner đổi khóa máy) 6 cặp on−off ≈ 0, chưa chụp được Perfetto A/B vì KJ7 rớt adb. Chi tiết: `doc/task/AUDIT_FISH-041_2026-11-07.md`.
